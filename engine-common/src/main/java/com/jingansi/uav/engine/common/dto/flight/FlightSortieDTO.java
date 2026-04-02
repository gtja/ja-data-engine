package com.jingansi.uav.engine.common.dto.flight;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单架次飞行明细。
 */
@Data
public class FlightSortieDTO {

    private String productKey;
    private String deviceId;
    private String deviceName;
    private Long sortieNo;
    private BigDecimal flightTimeMinutes;
    private BigDecimal flightDistanceMeters;
    private Long reportTimestampMillis;
    private Integer settlementStatus;
    private String settlementStatusDesc;
}
