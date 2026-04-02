package com.jingansi.uav.engine.biz.infrastructure.export.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jingansi.uav.engine.biz.infrastructure.export.AsyncExportTaskService;
import com.jingansi.uav.engine.common.constant.DataSourceNames;
import com.jingansi.uav.engine.common.enums.AsyncExportTaskStatusEnum;
import com.jingansi.uav.engine.dao.entity.AsyncExportTask;
import com.jingansi.uav.engine.dao.mapper.AsyncExportTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Date;
import java.util.UUID;

/**
 * 异步导出任务服务。
 */
@Service
@RequiredArgsConstructor
@DS(DataSourceNames.MYSQL)
public class AsyncExportTaskServiceImpl implements AsyncExportTaskService {

    private static final String UPDATE_BY = "async-export";

    private final AsyncExportTaskMapper asyncExportTaskMapper;

    @Override
    public AsyncExportTask createPendingTask(String exportType, String requestPayload, String fileName) {
        Date now = new Date();
        AsyncExportTask task = AsyncExportTask.builder()
                .taskNo(UUID.randomUUID().toString().replace("-", ""))
                .exportType(exportType)
                .taskStatus(AsyncExportTaskStatusEnum.PENDING.getCode())
                .fileName(fileName)
                .requestPayload(requestPayload)
                .gmtCreate(now)
                .gmtModified(now)
                .gmtCreateBy(UPDATE_BY)
                .gmtModifiedBy(UPDATE_BY)
                .build();
        asyncExportTaskMapper.insert(task);
        return task;
    }

    @Override
    public AsyncExportTask getByTaskNo(String taskNo) {
        if (!StringUtils.hasText(taskNo)) {
            return null;
        }
        return asyncExportTaskMapper.selectOne(new LambdaQueryWrapper<AsyncExportTask>()
                .eq(AsyncExportTask::getTaskNo, taskNo)
                .last("limit 1"));
    }

    @Override
    public long countByExportTypeAndStatuses(String exportType, Collection<AsyncExportTaskStatusEnum> statuses) {
        if (!StringUtils.hasText(exportType) || statuses == null || statuses.isEmpty()) {
            return 0L;
        }
        return asyncExportTaskMapper.selectCount(new LambdaQueryWrapper<AsyncExportTask>()
                .eq(AsyncExportTask::getExportType, exportType)
                .in(AsyncExportTask::getTaskStatus, statuses.stream().map(AsyncExportTaskStatusEnum::getCode).toArray()));
    }

    @Override
    public void markRunning(Long id) {
        updateStatus(id, AsyncExportTaskStatusEnum.RUNNING, null, null, null, new Date(), null);
    }

    @Override
    public void markSuccess(Long id, String bucketName, String objectKey) {
        updateStatus(id, AsyncExportTaskStatusEnum.SUCCESS, bucketName, objectKey, null, null, new Date());
    }

    @Override
    public void markFailed(Long id, String errorMessage) {
        updateStatus(id, AsyncExportTaskStatusEnum.FAILED, null, null, errorMessage, null, new Date());
    }

    private void updateStatus(Long id,
                              AsyncExportTaskStatusEnum status,
                              String bucketName,
                              String objectKey,
                              String errorMessage,
                              Date startTime,
                              Date finishTime) {
        AsyncExportTask update = new AsyncExportTask();
        update.setId(id);
        update.setTaskStatus(status.getCode());
        update.setBucketName(bucketName);
        update.setObjectKey(objectKey);
        update.setErrorMessage(errorMessage);
        update.setGmtModified(new Date());
        update.setGmtModifiedBy(UPDATE_BY);
        if (startTime != null) {
            update.setGmtStart(startTime);
        }
        if (finishTime != null) {
            update.setGmtFinish(finishTime);
        }
        asyncExportTaskMapper.updateById(update);
    }
}
