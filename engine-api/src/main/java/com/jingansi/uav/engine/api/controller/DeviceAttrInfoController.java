package com.jingansi.uav.engine.api.controller;

import com.jingansi.uav.engine.biz.device.attr.export.DeviceAttrInfoExportService;
import com.jingansi.uav.engine.biz.device.attr.query.DeviceAttrInfoQueryService;
import com.jingansi.uav.engine.biz.flight.statistics.export.DorisFlightStatisticsExportService;
import com.jingansi.uav.engine.common.dto.PageResultDTO;
import com.jingansi.uav.engine.common.dto.doris.DeviceAttrInfoLatestRecordDTO;
import com.jingansi.uav.engine.common.dto.doris.DeviceAttrInfoExportTaskDTO;
import com.jingansi.uav.engine.common.vo.Response;
import com.jingansi.uav.engine.common.vo.doris.DeviceAttrInfoExportRequest;
import com.jingansi.uav.engine.common.vo.doris.DeviceAttrInfoLatestQueryRequest;
import com.jingansi.uav.engine.common.vo.export.AsyncExportTaskPageRequest;
import com.jingansi.uav.engine.common.vo.flight.FlightStatisticsExportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Doris 设备属性导出接口。
 */
@RestController
@RequestMapping("/doris/deviceAttrInfo")
@RequiredArgsConstructor
public class DeviceAttrInfoController {

    private final DeviceAttrInfoExportService deviceAttrInfoExportService;
    private final DeviceAttrInfoQueryService deviceAttrInfoQueryService;
    private final DorisFlightStatisticsExportService dorisFlightStatisticsExportService;

    /**
     * 创建设备属性异步导出任务。
     */
    @PostMapping("/export")
    public Response<DeviceAttrInfoExportTaskDTO> export(@RequestBody(required = false) DeviceAttrInfoExportRequest request) {
        return Response.returnByCode(deviceAttrInfoExportService.submitExportTask(request));
    }
    /**
     * 创建异步导出任务。
     */
    @PostMapping("/flightStatistics/export")
    public Response<DeviceAttrInfoExportTaskDTO> exportFlightStatistics(@RequestBody(required = false) FlightStatisticsExportRequest request) {
        return Response.returnByCode(dorisFlightStatisticsExportService.submitExportTask(request));
    }

    /**
     * 查询导出任务。
     */
    @GetMapping("/export/tasks/{taskNo}")
    public Response<DeviceAttrInfoExportTaskDTO> task(@PathVariable String taskNo) {
        return Response.returnByCode(deviceAttrInfoExportService.getExportTask(taskNo));
    }

    /**
     * 按设备 ID 和导出类型分页查询导出任务列表。
     */
    @PostMapping("/export/tasks/page")
    public Response<PageResultDTO<DeviceAttrInfoExportTaskDTO>> tasks(@RequestBody(required = false) AsyncExportTaskPageRequest request) {
        return Response.returnByCode(deviceAttrInfoExportService.pageExportTasks(request));
    }

    /**
     * 返回导出文件 URL。
     */
    @GetMapping("/export/tasks/{taskNo}/download")
    public Response<String> download(@PathVariable String taskNo) {
        return Response.returnByCode(deviceAttrInfoExportService.getDownloadUrl(taskNo));
    }

    /**
     * 分页查询设备属性数据，并按请求字段提取返回。
     */
    @PostMapping("/page")
    public Response<List<DeviceAttrInfoLatestRecordDTO>> page(@RequestBody(required = false) DeviceAttrInfoLatestQueryRequest request) {
        return Response.returnByCode(deviceAttrInfoQueryService.page(request));
    }

}
