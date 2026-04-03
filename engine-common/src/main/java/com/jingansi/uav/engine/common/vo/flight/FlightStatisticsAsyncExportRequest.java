package com.jingansi.uav.engine.common.vo.flight;

import lombok.Data;

/**
 * 飞行统计异步导出请求。
 */
@Data
public class FlightStatisticsAsyncExportRequest {

    /**
     * 起始时间，格式 yyyy-MM-dd HH:mm:ss。
     */
    private String beginTime;

    /**
     * 结束时间，格式 yyyy-MM-dd HH:mm:ss。
     */
    private String endTime;

    /**
     * 需要导出的飞机设备 ID。
     */
    private String deviceId;
}
