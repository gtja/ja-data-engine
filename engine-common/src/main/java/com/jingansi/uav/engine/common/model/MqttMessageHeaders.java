package com.jingansi.uav.engine.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * MQTT “头信息” 的结构化视图。
 *
 * <p>这些字段不是设备 JSON 消息体本身的一部分，
 * 而是从以下两类信息整理出来的：
 * - MQTT topic
 * - MQTT 投递元数据，例如 qos、retained 等
 *
 * <p>这样做的目的，是把控制中心里“头信息 + 消息体”的思路保留下来，
 * handler 就不需要每次都手动拆 topic，直接拿现成的 productKey、deviceId 等信息即可。
 */
public class MqttMessageHeaders {

    private String topic;
    private List<String> segments;
    private String productKey;
    private String deviceId;
    private Integer qos;
    private Boolean retained;
    private Boolean duplicate;
    private Integer messageId;

    /**
     * 提供一个通用的 header 访问方式，
     * 兼容更偏“headerName -> value” 风格的代码写法。
     */
    public Object getHeader(String name) {
        if (MqttHeadersExtends.TOPIC.equals(name)) {
            return topic;
        }
        if (MqttHeadersExtends.PRODUCT_KEY.equals(name)) {
            return productKey;
        }
        if (MqttHeadersExtends.DEVICE_ID.equals(name)) {
            return deviceId;
        }
        if (MqttHeadersExtends.QOS.equals(name)) {
            return qos;
        }
        if (MqttHeadersExtends.MESSAGE_ID.equals(name)) {
            return messageId;
        }
        if (MqttHeadersExtends.DUPLICATE.equals(name)) {
            return duplicate;
        }
        if (MqttHeadersExtends.RETAINED.equals(name)) {
            return retained;
        }
        return null;
    }

    /**
     * 当下游代码更适合接收一个通用 map，而不是逐个调用 getter 时，可以用这个方法导出。
     */
    public Map<String, Object> asMap() {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put(MqttHeadersExtends.TOPIC, topic);
        headers.put(MqttHeadersExtends.PRODUCT_KEY, productKey);
        headers.put(MqttHeadersExtends.DEVICE_ID, deviceId);
        headers.put(MqttHeadersExtends.QOS, qos);
        headers.put(MqttHeadersExtends.MESSAGE_ID, messageId);
        headers.put(MqttHeadersExtends.DUPLICATE, duplicate);
        headers.put(MqttHeadersExtends.RETAINED, retained);
        return headers;
    }
}
