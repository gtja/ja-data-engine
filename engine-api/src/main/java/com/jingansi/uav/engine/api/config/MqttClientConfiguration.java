package com.jingansi.uav.engine.api.config;

import com.jingansi.uav.engine.biz.integration.mqtt.config.IotMqttProperties;
import com.jingansi.uav.engine.biz.integration.mqtt.dispatch.MqttMessageDispatcher;
import com.jingansi.uav.engine.biz.integration.mqtt.runtime.MqttClientCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * 初始化原生 Paho MQTT 客户端。
 *
 * <p>这里把启动路径尽量压到最简单：
 * 1. 从 yaml 构造连接参数
 * 2. 校验 handler 的 topic 是否被 yaml 里的订阅配置覆盖
 * 3. 创建 MQTT 客户端
 * 4. 真正的连接和订阅交给启动后的生命周期组件异步执行
 *
 * <p>这里没有再额外套更多抽象层，
 * 因为当前目标就是：
 * “yaml 配好，写一个 handler 类，就能直接用”。
 */
public class MqttClientConfiguration {

    @Bean
    public MqttConnectOptions mqttConnectOptions(IotMqttProperties properties) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(properties.serverUriArray());
        options.setAutomaticReconnect(Boolean.TRUE.equals(properties.getAutomaticReconnect()));
        options.setKeepAliveInterval(properties.getKeepAlive());
        options.setConnectionTimeout(properties.getTimeout());
        options.setCleanSession(Boolean.TRUE.equals(properties.getCleanSession()));
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            options.setUserName(properties.getUsername());
        }
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            options.setPassword(properties.getPassword().toCharArray());
        }
        return options;
    }

    @Bean
    public MqttClient mqttClient(IotMqttProperties properties,
                                 MqttClientCallback mqttClientCallback,
                                 MqttMessageDispatcher mqttMessageDispatcher) throws Exception {
        // 启动时先做校验，避免服务已经跑起来了，结果某些消息因为漏配 topic 被静默丢掉。
        mqttMessageDispatcher.validateConfiguredTopics();
        MqttClient client = new MqttClient(
                properties.primaryAddress(),
                properties.getClientId(),
                new MemoryPersistence()
        );
        client.setCallback(mqttClientCallback);
        return client;
    }
}
