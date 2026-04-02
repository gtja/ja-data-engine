package com.jingansi.uav.engine.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * 统一的 MQTT 入站消息体模型，风格上对齐控制中心。
 *
 * <p>核心思想是：
 * 业务 handler 不直接去解析原始 JSON 字符串，
 * 而是统一拿到一个结构稳定的 receiver 对象。
 *
 * <p>这个对象里大致包含：
 * - 事务元数据：tid、bid、timestamp、version
 * - 业务路由信息：method、productKey、deviceId
 * - 业务负载：data / params
 * - 可选状态字段：code、message
 */
public class CommonTopicReceiver<T> {

    /** 当前请求或事务的唯一标识。 */
    private String tid;
    /** 整条业务链路的标识，常用于串联多次消息交互。 */
    private String bid;

    @Builder.Default
    private String version = "1.0";

    private Long timestamp;
    /** 业务数据通常优先放在这个字段里。 */
    private T data;
    /** 有些设备会把业务数据放在 params，分发器会自动整理到 data。 */
    private T params;
    private String method;
    private String productKey;
    private String deviceId;
    private String code;
    private String message;
}
