package com.jingansi.uav.engine.biz.flight.statistics.handler;

import com.jingansi.uav.engine.biz.flight.statistics.FlightStatisticsService;
import com.jingansi.uav.engine.biz.integration.mqtt.dispatch.MqttTopicHandler;
import com.jingansi.uav.engine.common.constant.MqttTopicConstants;
import com.jingansi.uav.engine.common.constant.ProductKeyConstants;
import com.jingansi.uav.engine.common.model.CommonTopicReceiver;
import com.jingansi.uav.engine.common.model.MqttMessageHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * 飞行统计属性上报处理器。
 *
 * <p>这个 handler 只负责把“设备属性上报”路由到飞行统计服务，
 * 真正的统计和入库逻辑全部收口在 service 里。
 */
@Component
@RequiredArgsConstructor
public class FlightStatisticsPropertyPostHandler implements MqttTopicHandler {

    private final FlightStatisticsService flightStatisticsService;

    @Override
    public String topic() {
        return MqttTopicConstants.THING_EVENT_PROPERTY_POST;
    }

    @Override
    public void handle(MqttMessageHeaders headers, CommonTopicReceiver<Map<String, Object>> receiver) {
        if (!Objects.equals(ProductKeyConstants.FLIGHT_STATISTICS_PRODUCT_KEY, headers.getProductKey())) {
            return;
        }
        flightStatisticsService.recordPropertyReport(headers, receiver);
    }
}
