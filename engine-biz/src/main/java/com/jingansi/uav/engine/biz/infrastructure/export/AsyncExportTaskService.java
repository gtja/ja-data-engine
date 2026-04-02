package com.jingansi.uav.engine.biz.infrastructure.export;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jingansi.uav.engine.common.enums.AsyncExportTypeEnum;
import com.jingansi.uav.engine.common.enums.AsyncExportTaskStatusEnum;
import com.jingansi.uav.engine.dao.entity.AsyncExportTask;

import java.util.Collection;

public interface AsyncExportTaskService {

    AsyncExportTask createPendingTask(AsyncExportTypeEnum exportType, String deviceId, String requestPayload, String fileName);

    AsyncExportTask getByTaskNo(String taskNo);

    Page<AsyncExportTask> pageByExportTypeAndDeviceId(AsyncExportTypeEnum exportType, String deviceId, int pageNum, int pageSize);

    long countByExportTypeAndStatuses(AsyncExportTypeEnum exportType, Collection<AsyncExportTaskStatusEnum> statuses);

    void markRunning(Long id);

    void markSuccess(Long id, String bucketName, String objectKey);

    void markFailed(Long id, String errorMessage);
}
