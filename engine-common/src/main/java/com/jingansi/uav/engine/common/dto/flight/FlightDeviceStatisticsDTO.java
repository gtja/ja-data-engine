package com.jingansi.uav.engine.common.dto.flight;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 设备维度飞行聚合统计。
 */
@Data
public class FlightDeviceStatisticsDTO {

    private String productKey;
    private String deviceId;
    private Long totalFlightSorties;
    private BigDecimal totalFlightTimeMinutes;
    private BigDecimal totalFlightDistanceMeters;
}
