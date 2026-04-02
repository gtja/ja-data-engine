package com.jingansi.uav.engine.biz.infrastructure.export;

import com.jingansi.uav.engine.common.enums.AsyncExportTaskStatusEnum;
import com.jingansi.uav.engine.dao.entity.AsyncExportTask;

import java.util.Collection;

public interface AsyncExportTaskService {

    AsyncExportTask createPendingTask(String exportType, String requestPayload, String fileName);

    AsyncExportTask getByTaskNo(String taskNo);

    long countByExportTypeAndStatuses(String exportType, Collection<AsyncExportTaskStatusEnum> statuses);

    void markRunning(Long id);

    void markSuccess(Long id, String bucketName, String objectKey);

    void markFailed(Long id, String errorMessage);
}
