package com.jingansi.uav.engine.biz.device.attr.export.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jingansi.uav.engine.biz.device.attr.export.DeviceAttrInfoAsyncExportWorker;
import com.jingansi.uav.engine.biz.device.attr.export.DeviceAttrInfoExportFileBuilder;
import com.jingansi.uav.engine.biz.device.attr.export.DeviceAttrInfoExportService;
import com.jingansi.uav.engine.biz.infrastructure.export.AsyncExportCommonService;
import com.jingansi.uav.engine.common.bo.Result;
import com.jingansi.uav.engine.common.constant.DataSourceNames;
import com.jingansi.uav.engine.common.dto.PageResultDTO;
import com.jingansi.uav.engine.common.dto.doris.DeviceAttrInfoExportTaskDTO;
import com.jingansi.uav.engine.common.enums.AsyncExportTypeEnum;
import com.jingansi.uav.engine.common.vo.doris.DeviceAttrInfoExportRequest;
import com.jingansi.uav.engine.common.vo.export.AsyncExportTaskPageRequest;
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

    private final AsyncExportCommonService asyncExportCommonService;
    private final DeviceAttrInfoExportFileBuilder deviceAttrInfoExportFileBuilder;
    private final DeviceAttrInfoAsyncExportWorker deviceAttrInfoAsyncExportWorker;

    @Override
    public Result<DeviceAttrInfoExportTaskDTO> submitExportTask(DeviceAttrInfoExportRequest request) {
        // 设备属性导出只需要把自己的配置和处理逻辑传给公共服务即可。
        return asyncExportCommonService.submitExportTask(
                request,
                request == null ? null : request.getDeviceId(),
                AsyncExportTypeEnum.DEVICE_ATTR_INFO,
                deviceAttrInfoExportFileBuilder::validateRequest,
                deviceAttrInfoAsyncExportWorker::process);
    }

    @Override
    public Result<DeviceAttrInfoExportTaskDTO> getExportTask(String taskNo) {
        return asyncExportCommonService.getExportTask(taskNo);
    }

    @Override
    public Result<PageResultDTO<DeviceAttrInfoExportTaskDTO>> pageExportTasks(AsyncExportTaskPageRequest request) {
        return asyncExportCommonService.pageExportTasks(request);
    }

    @Override
    public Result<String> getDownloadUrl(String taskNo) {
        return asyncExportCommonService.getDownloadUrl(taskNo);
    }
}
