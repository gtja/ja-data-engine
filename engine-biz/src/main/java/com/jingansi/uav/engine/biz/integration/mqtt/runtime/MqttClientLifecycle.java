package com.jingansi.uav.engine.biz.integration.mqtt.runtime;

import com.jingansi.uav.engine.biz.integration.mqtt.dispatch.MqttMessageDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MQTT 客户端生命周期管理器。
 *
 * <p>目的只有一个：不要让 MQTT 的 connect/subscribe 阻塞 Spring 启动过程。
 * 服务先启动完成，MQTT 再在后台线程里连接和订阅。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttClientLifecycle {

    private static final long RETRY_INTERVAL_MILLIS = 5000L;

    private final MqttClient mqttClient;
    private final MqttConnectOptions mqttConnectOptions;
    private final MqttMessageDispatcher mqttMessageDispatcher;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mqtt-lifecycle-thread");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 应用启动完成后，后台执行 MQTT 初始连接和订阅。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        executorService.execute(this::connectAndSubscribeWithRetry);
    }

    /**
     * 后台重试连接，直到成功或者应用关闭。
     */
    private void connectAndSubscribeWithRetry() {
        while (!stopped.get()) {
            try {
                if (mqttClient.isConnected()) {
                    log.info("MQTT client already connected, skip initial connect");
                    return;
                }
                log.info("MQTT begin connect");
                mqttClient.connect(mqttConnectOptions);
                log.info("MQTT connect finished");

                log.info("MQTT begin subscribe configured topics");
                mqttMessageDispatcher.subscribeConfiguredTopics(mqttClient);
                log.info("MQTT subscribe finished");
                return;
            } catch (Exception ex) {
                log.error("MQTT initial connect or subscribe failed, retry in {} ms", RETRY_INTERVAL_MILLIS, ex);
                sleepBeforeRetry();
            }
        }
    }

    /**
     * 关闭应用时，释放 MQTT 客户端和后台线程资源。
     */
    @PreDestroy
    public void shutdown() {
        stopped.set(true);
        executorService.shutdownNow();
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (Exception ex) {
            log.warn("MQTT disconnect failed", ex);
        }
        try {
            mqttClient.close();
        } catch (Exception ex) {
            log.warn("MQTT close failed", ex);
        }
    }

    /**
     * 两次重试之间等待一小段时间，避免失败时空转打满日志。
     */
    private void sleepBeforeRetry() {
        try {
            TimeUnit.MILLISECONDS.sleep(RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
