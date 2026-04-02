package com.jingansi.uav.engine.biz.integration.mqtt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
@ConfigurationProperties(prefix = "mqtt")
/**
 * 绑定 yaml 顶层 `mqtt` 配置的属性类。
 *
 * <p>这里故意只保留最核心的配置：
 * 连接地址、账号密码、客户端参数、以及订阅 topic 列表。
 * 业务路由不在这里配置，业务路由由各个 handler 自己声明。
 *
 * <p>这里有两个容易混淆的 topic 概念：
 * 1. yaml 里的 `mqtt.topic`：决定客户端到底订阅哪些 topic。
 * 2. 代码里的 `handler.topic()`：决定收到消息后由哪个 handler 处理。
 *
 * <p>分发器会校验：
 * 每个 handler 的 topic 是否至少被一个 `mqtt.topic` 覆盖。
 * 这样可以避免代码里新增了 handler，但 yaml 忘记配，导致 handler 永远不会执行。
 */
public class IotMqttProperties {

    private static final int DEFAULT_WORKER_COUNT = 16;
    private static final int DEFAULT_QUEUE_CAPACITY = 2048;

    /**
     * 一个或多个 broker 地址。
     * 如果有多个地址，可以用逗号分隔。
     * 例如：tcp://127.0.0.1:1883
     */
    private String address;

    private String username;

    private String password;

    /**
     * MQTT 客户端标识。
     * 一般建议保持稳定，便于 broker 识别和断线重连。
     */
    private String clientId = "ja-uav-data-engine";

    /**
     * 订阅 yaml 中 topic 时默认使用的 qos。
     */
    private Integer qos = 1;

    private Integer keepAlive = 60;

    private Integer timeout = 60;

    private Boolean automaticReconnect = true;

    private Boolean cleanSession = false;

    /**
     * 入站订阅的唯一来源。
     * 客户端启动时和断线重连后，都会按这里的 topic 重新订阅。
     */
    private List<String> topic = new ArrayList<>();

    /**
     * MQTT 入站 worker 数量。
     *
     * <p>收到消息后，会按照 deviceId hash 到固定 worker，
     * 保证同一设备消息顺序不乱，不同设备可以并行处理。
     */
    private Integer workerCount = DEFAULT_WORKER_COUNT;

    /**
     * 每个 worker 的有界队列容量。
     *
     * <p>队列满时会阻塞入队，优先保证不丢消息、不断顺序。
     */
    private Integer queueCapacity = DEFAULT_QUEUE_CAPACITY;

    /**
     * 创建 Paho 客户端时，仍然需要一个主地址。
     * 如果配置了多个 broker，这里取第一个作为客户端初始化地址；
     * 完整地址列表仍然会传给连接参数，用于重连和故障切换。
     */
    public String primaryAddress() {
        List<String> addresses = resolveServerUris();
        if (addresses.isEmpty()) {
            throw new IllegalStateException("mqtt.address is required");
        }
        return addresses.get(0);
    }

    /**
     * 把 yaml 中的地址转换成 `MqttConnectOptions#setServerURIs` 需要的格式。
     */
    public String[] serverUriArray() {
        List<String> addresses = resolveServerUris();
        return addresses.toArray(new String[0]);
    }

    /**
     * 统一整理 topic 配置，去掉空值、去重、去掉多余空格，
     * 这样下游代码就不需要重复做这些处理了。
     */
    public List<String> resolveTopics() {
        if (topic == null) {
            return List.of();
        }
        return topic.stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 从一个 yaml 字符串里拆分并整理 broker 地址。
     */
    private List<String> resolveServerUris() {
        if (address == null || address.isBlank()) {
            return List.of();
        }
        return Arrays.stream(address.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * 对 worker 数量做兜底修正，避免非法配置导致线程池不可用。
     */
    public int resolveWorkerCount() {
        if (workerCount == null || workerCount < 1) {
            return DEFAULT_WORKER_COUNT;
        }
        return workerCount;
    }

    /**
     * 对单 worker 队列容量做兜底修正。
     */
    public int resolveQueueCapacity() {
        if (queueCapacity == null || queueCapacity < 1) {
            return DEFAULT_QUEUE_CAPACITY;
        }
        return queueCapacity;
    }
}
