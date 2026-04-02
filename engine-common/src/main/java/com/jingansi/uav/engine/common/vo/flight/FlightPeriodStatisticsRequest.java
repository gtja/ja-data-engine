package com.jingansi.uav.engine.common.vo.flight;

import lombok.Data;

import java.util.List;

/**
 * 飞行时间段统计请求。
 */
@Data
public class FlightPeriodStatisticsRequest {

    /**
     * 起始时间，格式 yyyy-MM-dd HH:mm:ss。
     */
    private String beginTime;

    /**
     * 结束时间，格式 yyyy-MM-dd HH:mm:ss。
     */
    private String endTime;

    /**
     * 设备 ID 列表，空则统计全部设备。
     */
    private List<String> deviceIds;
}
