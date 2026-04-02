package com.jingansi.uav.engine.api;

import com.jingansi.uav.engine.biz.integration.mqtt.config.IotMqttProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.jingansi.uav.engine")
@EnableConfigurationProperties(IotMqttProperties.class)
public class JaUavDataEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(JaUavDataEngineApplication.class, args);
    }
}
