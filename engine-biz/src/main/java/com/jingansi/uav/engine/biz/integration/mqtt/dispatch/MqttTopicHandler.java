package com.jingansi.uav.engine.biz.integration.mqtt.dispatch;

import com.jingansi.uav.engine.common.model.CommonTopicReceiver;
import com.jingansi.uav.engine.common.model.MqttMessageHeaders;

import java.util.Map;

/**
 * MQTT 业务处理器的最小契约。
 *
 * <p>每个业务 topic 理论上只需要做两件事：
 * 1. 声明自己关心的 topic 模式
 * 2. 实现这个 topic 的业务处理逻辑
 *
 * <p>这里支持 MQTT 风格的通配符，例如 `+` 和 `#`。
 * 分发器会同时用这个接口做两件事：
 * - 启动时校验 yaml 配置和 handler 是否匹配
 * - 运行时把消息路由到正确的 handler
 */
public interface MqttTopicHandler {

    /**
     * 声明当前 handler 负责处理的 topic 模式。
     */
    String topic();

    /**
     * 分发器完成以下动作后，最终进入业务处理入口：
     * - 匹配 topic
     * - 解析头信息
     * - 反序列化并整理消息体
     */
    void handle(MqttMessageHeaders headers, CommonTopicReceiver<Map<String, Object>> receiver);

    /**
     * 把通配匹配逻辑放在接口里，
     * 这样 handler 自己声明的 topic，同样也定义了自己的匹配规则，
     * 代码更集中，不容易跑散。
     */
    default boolean supports(String incomingTopic) {
        String[] patternSegments = topic().split("/");
        String[] topicSegments = incomingTopic.split("/");
        int index = 0;
        for (; index < patternSegments.length; index++) {
            String patternSegment = patternSegments[index];
            if ("#".equals(patternSegment)) {
                // 多级通配符：一旦命中，后面的层级全部视为匹配。
                return true;
            }
            if (index >= topicSegments.length) {
                return false;
            }
            if (!"+".equals(patternSegment) && !patternSegment.equals(topicSegments[index])) {
                return false;
            }
        }
        return index == topicSegments.length;
    }
}
