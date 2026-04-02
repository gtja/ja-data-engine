package com.jingansi.uav.engine.biz.integration.mqtt.dispatch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingansi.uav.engine.biz.integration.mqtt.config.IotMqttProperties;
import com.jingansi.uav.engine.common.model.CommonTopicReceiver;
import com.jingansi.uav.engine.common.model.MqttMessageHeaders;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@Component
/**
 * MQTT 消息分发中心。
 *
 * <p>这里故意把大部分入站处理都收口到一个类里，
 * 这样后面排查 MQTT 问题时，只需要先看这一个地方：
 * - 校验订阅配置
 * - 订阅 yaml 配置的 topic
 * - 把原始 MQTT 数据转换成“头信息 + 消息体”
 * - 找到匹配的 handler
 * - 调用具体业务 handler
 *
 * <p>这里之所以要做缓存，是因为 handler 的 topic 可能带 `+` / `#` 通配符，
 * 不能简单地直接 `map.get(topic)`。
 * 所以当前做法是：
 * 首次遇到一个具体 topic 时解析一次对应 handler，
 * 然后把结果缓存到 `ConcurrentHashMap`，
 * 后续相同 topic 的消息直接走缓存。
 */
public class MqttMessageDispatcher {

    private final List<MqttTopicHandler> handlers;
    private final ObjectMapper objectMapper;
    private final IotMqttProperties properties;

    /**
     * 把运行时的具体 topic 缓存到已经解析好的 handler 列表上。
     *
     * <p>例如：
     * key   = sys/abc/device001/thing/event/property/post
     * value = [ThingPropertyPostHandler]
     *
     * 这样可以避免每条消息都全量扫描所有 handler。
     */
    private final ConcurrentMap<String, List<MqttTopicHandler>> handlerCache = new ConcurrentHashMap<>();

    public MqttMessageDispatcher(List<MqttTopicHandler> handlers,
                                 ObjectMapper objectMapper,
                                 IotMqttProperties properties) {
        this.handlers = handlers;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void subscribeConfiguredTopics(MqttClient mqttClient) {
        Set<String> topics = new LinkedHashSet<>(properties.resolveTopics());
        if (topics.isEmpty()) {
            throw new IllegalStateException("mqtt.topic must configure at least one subscription topic");
        }
        // 订阅前先校验 yaml 和代码是否一致，避免服务看起来正常，
        // 实际上有些 handler 因为漏配 topic 根本收不到消息。
        validateHandlerCoverage(topics);
        for (String topic : topics) {
            try {
                mqttClient.subscribe(topic, properties.getQos());
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to subscribe topic=" + topic, ex);
            }
        }
        log.info("MQTT subscribed configured topics={}, count={}", topics, topics.size());
    }

    public void dispatch(String topic, MqttMessage mqttMessage) {
        dispatch(topic,
                mqttMessage == null ? null : mqttMessage.getPayload(),
                mqttMessage == null ? null : mqttMessage.getQos(),
                mqttMessage != null && mqttMessage.isRetained(),
                mqttMessage != null && mqttMessage.isDuplicate(),
                mqttMessage == null ? null : mqttMessage.getId());
    }

    /**
     * worker 线程使用的消息分发入口。
     *
     * <p>这里接收的是已经从 callback 线程复制出来的最小消息快照，
     * 避免跨线程直接持有 Paho 原始消息对象。
     */
    public void dispatch(String topic,
                         byte[] payload,
                         Integer qos,
                         Boolean retained,
                         Boolean duplicate,
                         Integer messageId) {
        MqttMessageHeaders headers = buildHeaders(topic, qos, retained, duplicate, messageId);
        CommonTopicReceiver<Map<String, Object>> receiver = normalize(headers, parsePayload(topic, payload));

        // 路由结果按“具体 topic”做缓存。
        // 第一次命中某个 topic 时会做一次匹配，后续相同 topic 直接命中缓存。
        List<MqttTopicHandler> matchedHandlers = handlerCache.computeIfAbsent(topic, this::resolveHandlers);
        if (matchedHandlers.isEmpty()) {
            log.info("No mqtt handler matched topic={}", topic);
            return;
        }
        for (MqttTopicHandler handler : matchedHandlers) {
            handler.handle(headers, receiver);
        }
    }

    public void validateConfiguredTopics() {
        Set<String> topics = new LinkedHashSet<>(properties.resolveTopics());
        if (topics.isEmpty()) {
            throw new IllegalStateException("mqtt.topic must configure at least one subscription topic");
        }
        validateHandlerCoverage(topics);
    }

    /**
     * 把原始 MQTT 消息体反序列化成和控制中心类似的统一接收模型。
     *
     * <p>如果反序列化失败，也不会直接丢消息，
     * 而是把原始文本塞进 `data.rawPayload`，
     * 这样至少还能继续往后走，避免因为 DTO 一时不匹配直接吞掉消息。
     */
    private CommonTopicReceiver<Map<String, Object>> parsePayload(String topic, byte[] payload) {
        if (payload == null) {
            payload = new byte[0];
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<CommonTopicReceiver<Map<String, Object>>>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to deserialize mqtt payload, topic={}", topic, ex);
            String rawPayload = new String(payload, StandardCharsets.UTF_8);
            Map<String, Object> data = new HashMap<>();
            data.put("rawPayload", rawPayload);
            return CommonTopicReceiver.<Map<String, Object>>builder()
                    .message(rawPayload)
                    .timestamp(System.currentTimeMillis())
                    .data(data)
                    .build();
        }
    }

    /**
     * 对消息体做统一整理，保证 handler 看到的是稳定结构：
     * - `data` 为空时回退到 `params`
     * - 负载为空时补成空 map，而不是 null
     * - timestamp/version 缺失时自动补齐
     * - productKey/deviceId 优先用消息体，没有时回退到 topic 解析结果
     */
    private CommonTopicReceiver<Map<String, Object>> normalize(MqttMessageHeaders headers,
                                                               CommonTopicReceiver<Map<String, Object>> receiver) {
        if (receiver == null) {
            receiver = new CommonTopicReceiver<>();
        }
        if (receiver.getData() == null && receiver.getParams() != null) {
            receiver.setData(receiver.getParams());
        }
        if (receiver.getData() == null) {
            receiver.setData(new HashMap<>());
        }
        if (receiver.getTimestamp() == null) {
            receiver.setTimestamp(System.currentTimeMillis());
        }
        if (receiver.getVersion() == null || receiver.getVersion().isBlank()) {
            receiver.setVersion("1.0");
        }
        if (receiver.getProductKey() == null || receiver.getProductKey().isBlank()) {
            receiver.setProductKey(headers.getProductKey());
        }
        if (receiver.getDeviceId() == null || receiver.getDeviceId().isBlank()) {
            receiver.setDeviceId(headers.getDeviceId());
        }
        return receiver;
    }

    /**
     * 从原始 topic 和 MQTT 投递属性里提取业务层经常会用到的最小头信息。
     */
    private MqttMessageHeaders buildHeaders(String topic,
                                           Integer qos,
                                           Boolean retained,
                                           Boolean duplicate,
                                           Integer messageId) {
        List<String> segments = Arrays.asList(topic.split("/"));
        String productKey = segments.size() > 1 && "sys".equals(segments.get(0)) ? segments.get(1) : null;
        String deviceId = segments.size() > 2 && "sys".equals(segments.get(0)) ? segments.get(2) : null;
        return MqttMessageHeaders.builder()
                .topic(topic)
                .segments(segments)
                .productKey(productKey)
                .deviceId(deviceId)
                .qos(qos)
                .retained(retained)
                .duplicate(duplicate)
                .messageId(messageId)
                .build();
    }

    /**
     * 校验每个业务 handler 至少能被一个已配置的订阅 topic 覆盖。
     *
     * <p>没有这个校验时，很容易出现：
     * 代码里新写了 handler，
     * 但是 yaml 忘了加订阅 topic，
     * 最后这个 handler 一直不执行，还很难第一时间看出来。
     */
    private void validateHandlerCoverage(Set<String> configuredTopics) {
        Set<String> uncovered = new LinkedHashSet<>();
        for (MqttTopicHandler handler : handlers) {
            boolean covered = configuredTopics.stream().anyMatch(handler::supports);
            if (!covered) {
                uncovered.add(handler.topic());
            }
        }
        if (!uncovered.isEmpty()) {
            throw new IllegalStateException("Handler topic not covered by mqtt.topic: " + uncovered);
        }
    }

    /**
     * 为一个具体 topic 找到对应的 handler，并把结果缓存起来。
     *
     * <p>这是在两个极端之间的折中：
     * - 每条消息都全量扫描所有 handler，太浪费
     * - 只做最简单的 map 直取，又无法处理带通配符的 handler topic
     */
    private List<MqttTopicHandler> resolveHandlers(String topic) {
        List<MqttTopicHandler> matchedHandlers = handlers.stream()
                .filter(handler -> handler.supports(topic))
                .collect(Collectors.toList());
        return matchedHandlers.isEmpty() ? Collections.emptyList() : matchedHandlers;
    }
}
