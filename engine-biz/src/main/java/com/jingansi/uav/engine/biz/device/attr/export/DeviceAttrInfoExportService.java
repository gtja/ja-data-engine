package com.jingansi.uav.engine.biz.device.attr.export;

import com.jingansi.uav.engine.common.bo.Result;
import com.jingansi.uav.engine.common.dto.doris.DeviceAttrInfoExportTaskDTO;
import com.jingansi.uav.engine.common.vo.doris.DeviceAttrInfoExportRequest;

public interface DeviceAttrInfoExportService {

    /**
     * 创建异步导出任务。
     */
    Result<DeviceAttrInfoExportTaskDTO> submitExportTask(DeviceAttrInfoExportRequest request);

    /**
     * 查询异步导出任务。
     */
    Result<DeviceAttrInfoExportTaskDTO> getExportTask(String taskNo);

    /**
     * 返回导出文件下载 URL。
     */
    Result<String> getDownloadUrl(String taskNo);
}
