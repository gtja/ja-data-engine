package com.jingansi.uav.engine.common.dto.doris;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Doris 飞行统计聚合记录。
 */
@Data
public class DorisFlightStatisticsRecordDTO {

    /**
     * 设备 ID。
     */
    private String deviceId;

    /**
     * 查询区间内飞行架次增量。
     */
    private Long flightSortiesInRange;

    /**
     * 查询区间内飞行时长增量，单位秒。
     */
    private BigDecimal flightTimeSecondsInRange;

    /**
     * 查询区间内飞行里程增量，单位米。
     */
    private BigDecimal flightDistanceMetersInRange;
}
