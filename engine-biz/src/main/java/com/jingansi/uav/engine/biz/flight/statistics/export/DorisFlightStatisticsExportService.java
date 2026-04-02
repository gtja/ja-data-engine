package com.jingansi.uav.engine.biz.flight.statistics.export;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jingansi.uav.engine.biz.infrastructure.export.AsyncExportCommonService;
import com.jingansi.uav.engine.common.bo.Result;
import com.jingansi.uav.engine.common.constant.DataSourceNames;
import com.jingansi.uav.engine.common.dto.doris.DeviceAttrInfoExportTaskDTO;
import com.jingansi.uav.engine.common.vo.flight.FlightStatisticsExportRequest;
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

    private static final String EXPORT_TYPE = "DORIS_FLIGHT_STATISTICS";
    private static final int MAX_ACTIVE_TASKS = 3;
    private static final String FILE_NAME_PREFIX = "flight_statistics";
    private static final String EXPORT_BIZ_NAME = "飞行统计";

    private final AsyncExportCommonService asyncExportCommonService;
    private final DorisFlightStatisticsExportFileBuilder dorisFlightStatisticsExportFileBuilder;
    private final DorisFlightStatisticsAsyncExportWorker dorisFlightStatisticsAsyncExportWorker;

    public Result<DeviceAttrInfoExportTaskDTO> submitExportTask(FlightStatisticsExportRequest request) {
        // 飞行统计导出只需要把自己的配置和处理逻辑传给公共服务即可。
        return asyncExportCommonService.submitExportTask(
                request,
                EXPORT_TYPE,
                MAX_ACTIVE_TASKS,
                FILE_NAME_PREFIX,
                EXPORT_BIZ_NAME,
                dorisFlightStatisticsExportFileBuilder::validateRequest,
                dorisFlightStatisticsAsyncExportWorker::process);
    }
}
