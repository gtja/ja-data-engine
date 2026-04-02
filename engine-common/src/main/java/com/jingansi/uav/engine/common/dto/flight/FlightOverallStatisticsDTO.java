package com.jingansi.uav.engine.common.dto.flight;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 全量飞行汇总统计。
 */
@Data
public class FlightOverallStatisticsDTO {

    /**
     * 飞行总架次。
     */
    private Long totalFlightSorties;

    /**
     * 飞行总时长，单位分钟。
     */
    private BigDecimal totalFlightTimeMinutes;

    /**
     * 飞行总里程，单位米。
     */
    private BigDecimal totalFlightDistanceMeters;
}
