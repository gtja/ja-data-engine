package com.jingansi.uav.engine.common.vo.flight;

import lombok.Data;

import java.util.List;

/**
 * 飞行统计导出请求。
 */
@Data
public class FlightStatisticsExportRequest {

    /**
     * 起始时间，格式 yyyy-MM-dd HH:mm:ss。
     */
    private String beginTime;

    /**
     * 结束时间，格式 yyyy-MM-dd HH:mm:ss。
     */
    private String endTime;

    /**
     * 需要导出的飞机设备 ID 列表，支持多选。
     */
    private List<String> deviceIds;
}
