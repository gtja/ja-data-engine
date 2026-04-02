package com.jingansi.uav.engine.biz.integration.mqtt.runtime;

import com.jingansi.uav.engine.biz.integration.mqtt.config.IotMqttProperties;
import com.jingansi.uav.engine.biz.integration.mqtt.dispatch.MqttMessageDispatcher;
import com.jingansi.uav.engine.common.constant.ProductKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MQTT 入站分片执行器。
 *
 * <p>核心目标：
 * - 同一 deviceId 的消息固定落到同一个 worker，保证顺序
 * - 不同设备可以并行处理，避免 callback 线程直接被业务阻塞
 * - 使用有界队列限制内存占用
 *
 * <p>入站链路变成：
 * messageArrived -> 按 deviceId hash 入队 -> 分片 worker -> dispatcher -> handler
 */
@Slf4j
@Component
public class MqttShardedMessageExecutor {

    private final MqttMessageDispatcher mqttMessageDispatcher;
    private final IotMqttProperties properties;

    private final List<BlockingQueue<InboundTask>> shardQueues = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService workerExecutor;

    public MqttShardedMessageExecutor(MqttMessageDispatcher mqttMessageDispatcher,
                                      IotMqttProperties properties) {
        this.mqttMessageDispatcher = mqttMessageDispatcher;
        this.properties = properties;
    }

    /**
     * 应用启动时预创建所有分片 worker。
     */
    @PostConstruct
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        int workerCount = properties.resolveWorkerCount();
        int queueCapacity = properties.resolveQueueCapacity();
        workerExecutor = Executors.newFixedThreadPool(workerCount, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        for (int index = 0; index < workerCount; index++) {
            final int shardIndex = index;
            BlockingQueue<InboundTask> queue = new ArrayBlockingQueue<>(queueCapacity);
            shardQueues.add(queue);
            workerExecutor.submit(() -> runWorker(shardIndex, queue));
        }
        log.info("MQTT sharded executor started, workerCount={}, queueCapacity={}", workerCount, queueCapacity);
    }

    /**
     * 收到原始 MQTT 消息后，先复制必要字段，再按 deviceId 分片入队。
     *
     * <p>这里不直接跑业务逻辑，
     * callback 线程只负责快速入队，真正的 dispatch 在 worker 线程中执行。
     */
    public void submit(String topic, MqttMessage message) {
        if (!running.get()) {
            log.warn("MQTT sharded executor is not running, skip topic={}", topic);
            return;
        }
        if (message == null) {
            return;
        }
        if (!ProductKeyConstants.FLIGHT_STATISTICS_PRODUCT_KEY.equals(topic.split("/")[1])) {
            return;
        }
        byte[] payload = message.getPayload();
        byte[] payloadCopy = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
        InboundTask task = new InboundTask(
                topic,
                payloadCopy,
                message.getQos(),
                message.isRetained(),
                message.isDuplicate(),
                message.getId()
        );
        int shardIndex = shardIndex(resolveShardKey(topic));
        BlockingQueue<InboundTask> queue = shardQueues.get(shardIndex);
        try {
            // 队列满时阻塞等待，优先保证消息不丢和设备内顺序不乱。
            queue.put(task);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("MQTT message enqueue interrupted, topic={}, shardIndex={}", topic, shardIndex);
        }
    }

    /**
     * 应用关闭时停止 worker。
     */
    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (workerExecutor != null) {
            workerExecutor.shutdownNow();
            try {
                workerExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("MQTT sharded executor stopped");
    }

    /**
     * 单个分片 worker 的消费循环。
     */
    private void runWorker(int shardIndex, BlockingQueue<InboundTask> queue) {
        Thread.currentThread().setName("mqtt-dispatch-worker-" + shardIndex);
        while (running.get()) {
            try {
                InboundTask task = queue.take();
                mqttMessageDispatcher.dispatch(task.topic, task.payload, task.qos, task.retained, task.duplicate, task.messageId);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                if (!running.get()) {
                    return;
                }
            } catch (Exception ex) {
                log.error("MQTT shard worker dispatch failed, shardIndex={}", shardIndex, ex);
            }
        }
    }



    /**
     * 优先使用 topic 中的 deviceId 作为分片键。
     *
     * <p>如果当前 topic 不是 sys/{productKey}/{deviceId}/... 结构，
     * 就退化为按完整 topic 分片。
     */
    private String resolveShardKey(String topic) {
        if (!StringUtils.hasText(topic)) {
            return "";
        }
        String[] segments = topic.split("/");
        if (segments.length > 2 && "sys".equals(segments[0]) && StringUtils.hasText(segments[2])) {
            return segments[2];
        }
        return topic;
    }

    /**
     * 根据分片键计算固定 worker 下标。
     */
    private int shardIndex(String shardKey) {
        int shardCount = shardQueues.size();
        if (shardCount == 0) {
            return 0;
        }
        return spreadHash(shardKey) & (shardCount - 1);
    }


    /**
     * 使用和 HashMap 类似的高低位扰动方式，降低普通 hashCode 低位分布不均带来的碰撞风险。
     *
     * <p>HashMap 的核心思路：
     * h ^ (h >>> 16)
     *
     * <p>由于 shardCount 保证为 2 的幂，因此最终索引可使用
     * hash & (shardCount - 1)
     * 代替 hash % shardCount。
     */
    private int spreadHash(String shardKey) {
        int h;
        return shardKey == null ? 0 : (h = shardKey.hashCode()) ^ (h >>> 16);
    }

    /**
     * 入队前复制出来的最小消息快照。
     *
     * <p>只保留业务分发需要的字段，避免跨线程直接持有 Paho 原始对象。
     */
    private static final class InboundTask {

        private final String topic;
        private final byte[] payload;
        private final Integer qos;
        private final Boolean retained;
        private final Boolean duplicate;
        private final Integer messageId;

        private InboundTask(String topic,
                            byte[] payload,
                            Integer qos,
                            Boolean retained,
                            Boolean duplicate,
                            Integer messageId) {
            this.topic = topic;
            this.payload = payload;
            this.qos = qos;
            this.retained = retained;
            this.duplicate = duplicate;
            this.messageId = messageId;
        }
    }
}
