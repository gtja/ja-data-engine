package com.jingansi.uav.engine.biz.flight.statistics;

import com.jingansi.uav.engine.common.bo.Result;
import com.jingansi.uav.engine.common.dto.flight.FlightDeviceStatisticsDTO;
import com.jingansi.uav.engine.common.dto.flight.FlightOverallStatisticsDTO;
import com.jingansi.uav.engine.common.dto.flight.FlightSortieDTO;
import com.jingansi.uav.engine.common.model.CommonTopicReceiver;
import com.jingansi.uav.engine.common.model.MqttMessageHeaders;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

public interface FlightStatisticsService {

    /**
     * 汇总所有设备的飞行累计统计。
     */
    Result<FlightOverallStatisticsDTO> totalStatistics();

    /**
     * 按设备和时间范围统计飞行数据。
     */
    Result<List<FlightDeviceStatisticsDTO>> periodStatistics(List<String> deviceIds, String beginTime, String endTime);

    /**
     * 查询架次明细。
     */
    Result<List<FlightSortieDTO>> listSorties(List<String> deviceIds, String beginTime, String endTime);

    /**
     * 导出飞行统计 Excel。
     */
    void exportStatistics(List<String> deviceIds, String beginTime, String endTime, HttpServletResponse response);

    /**
     * 处理 MQTT 属性上报。
     */
    void recordPropertyReport(MqttMessageHeaders headers, CommonTopicReceiver<Map<String, Object>> receiver);
}
