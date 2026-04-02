package com.jingansi.uav.engine.biz.integration.mqtt.runtime;

import com.jingansi.uav.engine.biz.integration.mqtt.dispatch.MqttMessageDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * 原生 MQTT 回调桥接类。
 *
 * <p>这个类不写业务逻辑，
 * 只负责响应 MQTT 的底层事件，然后把工作转交给分发器：
 * - 重连后：重新订阅 yaml 中配置的 topic
 * - 收到消息时：把原始 topic 和消息转交给分发器
 */
public class MqttClientCallback implements MqttCallbackExtended {

    private final MqttMessageDispatcher mqttMessageDispatcher;
    private final MqttShardedMessageExecutor mqttShardedMessageExecutor;
    private final ObjectProvider<MqttClient> mqttClientProvider;

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT connected, reconnect={}, serverURI={}", reconnect, serverURI);
        if (!reconnect) {
            // reconnect=false 表示首次连接成功，这是正常情况。
            // 首次连接后的订阅由生命周期组件在后台线程里执行，这里只处理重连场景。
            return;
        }
        MqttClient mqttClient = mqttClientProvider.getIfAvailable();
        if (mqttClient != null) {
            // 重连后 broker 不一定还保留之前的订阅，所以这里统一重新订阅一遍配置里的 topic。
            mqttMessageDispatcher.subscribeConfiguredTopics(mqttClient);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost", cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        // 这是 MQTT 消息进入应用层的唯一入口。
        // callback 线程不再直接跑业务，而是先把消息按 deviceId 分片入队。
        mqttShardedMessageExecutor.submit(topic, message);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("MQTT delivery complete, messageId={}", token.getMessageId());
    }
}
