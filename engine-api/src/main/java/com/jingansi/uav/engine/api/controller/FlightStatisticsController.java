package com.jingansi.uav.engine.api.controller;

import com.jingansi.uav.engine.biz.flight.statistics.FlightStatisticsService;
import com.jingansi.uav.engine.common.dto.flight.FlightDeviceStatisticsDTO;
import com.jingansi.uav.engine.common.dto.flight.FlightOverallStatisticsDTO;
import com.jingansi.uav.engine.common.dto.flight.FlightSortieDTO;
import com.jingansi.uav.engine.common.vo.Response;
import com.jingansi.uav.engine.common.vo.flight.FlightStatisticsExportRequest;
import com.jingansi.uav.engine.common.vo.flight.FlightPeriodStatisticsRequest;
import com.jingansi.uav.engine.common.vo.flight.FlightSortiesRequest;
import com.jingansi.uav.engine.common.vo.flight.FlightTotalStatisticsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 飞行统计接口。
 */
@RestController
@RequestMapping("/manage/flight/statistics")
@RequiredArgsConstructor
public class FlightStatisticsController {

    private final FlightStatisticsService flightStatisticsService;

    /**
     * 统计所有飞机的飞行总架次、总时长、总距离。
     */
    @PostMapping("/total")
    public Response<FlightOverallStatisticsDTO> totalStatistics(@RequestBody(required = false) FlightTotalStatisticsRequest request) {
        return Response.returnByCode(flightStatisticsService.totalStatistics());
    }

    /**
     * 根据设备 ID 列表和时间范围统计飞行汇总。
     */
    @PostMapping("/period")
    public Response<List<FlightDeviceStatisticsDTO>> periodStatistics(@RequestBody(required = false) FlightPeriodStatisticsRequest request) {
        List<String> deviceIds = request == null ? null : request.getDeviceIds();
        String beginTime = request == null ? null : request.getBeginTime();
        String endTime = request == null ? null : request.getEndTime();
        return Response.returnByCode(flightStatisticsService.periodStatistics(deviceIds, beginTime, endTime));
    }

    /**
     * 根据设备 ID 列表和时间范围查询架次明细。
     */
    @PostMapping("/sorties")
    public Response<List<FlightSortieDTO>> listSorties(@RequestBody(required = false) FlightSortiesRequest request) {
        List<String> deviceIds = request == null ? null : request.getDeviceIds();
        String beginTime = request == null ? null : request.getBeginTime();
        String endTime = request == null ? null : request.getEndTime();
        return Response.returnByCode(flightStatisticsService.listSorties(deviceIds, beginTime, endTime));
    }

    /**
     * 根据时间范围和飞机列表导出飞行统计 Excel。
     */
    @PostMapping("/export")
    public void export(@RequestBody(required = false) FlightStatisticsExportRequest request,
                       HttpServletResponse response) {
        List<String> deviceIds = request == null ? null : request.getDeviceIds();
        String beginTime = request == null ? null : request.getBeginTime();
        String endTime = request == null ? null : request.getEndTime();
        flightStatisticsService.exportStatistics(deviceIds, beginTime, endTime, response);
    }

}
