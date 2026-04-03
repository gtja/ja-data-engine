package com.jingansi.uav.engine.biz.flight.statistics.export;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jingansi.uav.engine.biz.infrastructure.export.AsyncExportCommonService;
import com.jingansi.uav.engine.common.bo.Result;
import com.jingansi.uav.engine.common.constant.DataSourceNames;
import com.jingansi.uav.engine.common.dto.doris.DeviceAttrInfoExportTaskDTO;
import com.jingansi.uav.engine.common.enums.AsyncExportTypeEnum;
import com.jingansi.uav.engine.common.vo.flight.FlightStatisticsAsyncExportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 基于 Doris 的飞行统计异步导出服务。
 * 这里只保留“飞行统计导出自己的差异配置”，
 * 通用提交流程统一复用 AsyncExportCommonService。
 */
@Service
@DS(DataSourceNames.MYSQL)
@RequiredArgsConstructor
public class DorisFlightStatisticsExportService {

    private final AsyncExportCommonService asyncExportCommonService;
    private final DorisFlightStatisticsExportFileBuilder dorisFlightStatisticsExportFileBuilder;
    private final DorisFlightStatisticsAsyncExportWorker dorisFlightStatisticsAsyncExportWorker;

    public Result<DeviceAttrInfoExportTaskDTO> submitExportTask(FlightStatisticsAsyncExportRequest request) {
        // 飞行统计导出只需要把自己的配置和处理逻辑传给公共服务即可。
        return asyncExportCommonService.submitExportTask(
                request,
                request == null ? null : request.getDeviceId(),
                AsyncExportTypeEnum.DORIS_FLIGHT_STATISTICS,
                dorisFlightStatisticsExportFileBuilder::validateRequest,
                dorisFlightStatisticsAsyncExportWorker::process);
    }
}
