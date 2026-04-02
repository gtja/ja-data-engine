package com.jingansi.uav.engine.biz.device.attr.export;

import com.jingansi.uav.engine.biz.infrastructure.export.AsyncExportTaskService;
import com.jingansi.uav.engine.biz.infrastructure.storage.s3.S3ObjectStorageService;
import com.jingansi.uav.engine.biz.infrastructure.storage.s3.S3Properties;
import com.jingansi.uav.engine.common.exception.BizException;
import com.jingansi.uav.engine.common.vo.doris.DeviceAttrInfoExportRequest;
import com.jingansi.uav.engine.dao.entity.AsyncExportTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 设备属性异步导出执行器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAttrInfoAsyncExportWorker {

    private static final String EXPORT_OBJECT_PREFIX = "exports/device_attr_info";

    private final AsyncExportTaskService asyncExportTaskService;
    private final DeviceAttrInfoExportFileBuilder deviceAttrInfoExportFileBuilder;
    private final S3ObjectStorageService s3ObjectStorageService;
    private final S3Properties s3Properties;

    @Async("exportTaskExecutor")
    public void process(AsyncExportTask task, DeviceAttrInfoExportRequest request) {
        File tempFile = null;
        try {
            asyncExportTaskService.markRunning(task.getId());
            tempFile = File.createTempFile("device-attr-export-" + task.getTaskNo(), ".xlsx");
            deviceAttrInfoExportFileBuilder.writeToFile(request, tempFile);

            String bucketName = s3Properties.getBucketName();
            String objectKey = buildObjectKey(task.getTaskNo(), task.getFileName());
            s3ObjectStorageService.upload(bucketName, objectKey, tempFile);
            asyncExportTaskService.markSuccess(task.getId(), bucketName, objectKey);
        } catch (Exception ex) {
            log.error("设备属性异步导出失败, taskNo={}", task.getTaskNo(), ex);
            asyncExportTaskService.markFailed(task.getId(), resolveErrorMessage(ex));
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("删除临时导出文件失败, path={}", tempFile.getAbsolutePath());
            }
        }
    }



    private String buildObjectKey(String taskNo, String fileName) {
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        return EXPORT_OBJECT_PREFIX + "/" + day + "/" + taskNo + "/" + fileName;
    }

    private String resolveErrorMessage(Exception ex) {
        if (ex instanceof BizException && StringUtils.hasText(ex.getMessage())) {
            return ex.getMessage();
        }
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return "异步导出失败";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
