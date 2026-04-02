package com.jingansi.uav.engine.biz.device.attr.export.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jingansi.uav.engine.biz.device.attr.export.DeviceAttrInfoAsyncExportWorker;
import com.jingansi.uav.engine.biz.device.attr.export.DeviceAttrInfoExportFileBuilder;
import com.jingansi.uav.engine.biz.device.attr.export.DeviceAttrInfoExportService;
import com.jingansi.uav.engine.biz.infrastructure.export.AsyncExportCommonService;
import com.jingansi.uav.engine.common.bo.Result;
import com.jingansi.uav.engine.common.constant.DataSourceNames;
import com.jingansi.uav.engine.common.dto.doris.DeviceAttrInfoExportTaskDTO;
import com.jingansi.uav.engine.common.vo.doris.DeviceAttrInfoExportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 设备属性异步导出服务。
 * 这里只保留“设备属性导出自己的差异配置”，
 * 通用提交流程统一复用 AsyncExportCommonService。
 */
@Service
@DS(DataSourceNames.MYSQL)
@RequiredArgsConstructor
public class DeviceAttrInfoExportServiceImpl implements DeviceAttrInfoExportService {

    private static final String EXPORT_TYPE = "DEVICE_ATTR_INFO";
    private static final int MAX_ACTIVE_TASKS = 2;
    private static final String FILE_NAME_PREFIX = "device_properties_info";
    private static final String EXPORT_BIZ_NAME = "设备属性";

    private final AsyncExportCommonService asyncExportCommonService;
    private final DeviceAttrInfoExportFileBuilder deviceAttrInfoExportFileBuilder;
    private final DeviceAttrInfoAsyncExportWorker deviceAttrInfoAsyncExportWorker;

    @Override
    public Result<DeviceAttrInfoExportTaskDTO> submitExportTask(DeviceAttrInfoExportRequest request) {
        // 设备属性导出只需要把自己的配置和处理逻辑传给公共服务即可。
        return asyncExportCommonService.submitExportTask(
                request,
                EXPORT_TYPE,
                MAX_ACTIVE_TASKS,
                FILE_NAME_PREFIX,
                EXPORT_BIZ_NAME,
                deviceAttrInfoExportFileBuilder::validateRequest,
                deviceAttrInfoAsyncExportWorker::process);
    }

    @Override
    public Result<DeviceAttrInfoExportTaskDTO> getExportTask(String taskNo) {
        return asyncExportCommonService.getExportTask(taskNo);
    }

    @Override
    public Result<String> getDownloadUrl(String taskNo) {
        return asyncExportCommonService.getDownloadUrl(taskNo);
    }
}
