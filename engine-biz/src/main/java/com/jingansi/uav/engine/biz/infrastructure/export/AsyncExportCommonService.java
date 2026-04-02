package com.jingansi.uav.engine.biz.infrastructure.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingansi.uav.engine.biz.infrastructure.storage.s3.S3Properties;
import com.jingansi.uav.engine.common.bo.Result;
import com.jingansi.uav.engine.common.dto.doris.DeviceAttrInfoExportTaskDTO;
import com.jingansi.uav.engine.common.enums.AsyncExportTaskStatusEnum;
import com.jingansi.uav.engine.common.exception.BizException;
import com.jingansi.uav.engine.dao.entity.AsyncExportTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Objects;

/**
 * 异步导出公共服务。
 * <p>
 * 这个类只做“固定不变”的那部分事情：
 * 1. 校验请求
 * 2. 检查同类型任务是否超过并发上限
 * 3. 序列化请求并创建导出任务记录
 * 4. 调用具体业务自己的异步 worker 去真正处理导出
 * <p>
 * 各业务之间真正不同的部分，例如：
 * 1. 导出类型
 * 2. 最大并发数
 * 3. 导出文件名前缀
 * 4. 请求校验逻辑
 * 5. 异步处理逻辑
 * 都通过方法参数传入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncExportCommonService {

    private static final String DEFAULT_BUSY_MESSAGE = "有任务正在查询,请您稍后再试";
    private static final EnumSet<AsyncExportTaskStatusEnum> ACTIVE_STATUSES =
            EnumSet.of(AsyncExportTaskStatusEnum.PENDING, AsyncExportTaskStatusEnum.RUNNING);
    private static final DateTimeFormatter FILE_NAME_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AsyncExportTaskService asyncExportTaskService;
    private final S3Properties s3Properties;
    private final ObjectMapper objectMapper;

    /**
     * 提交异步导出任务。
     *
     * @param request 导出请求
     * @param exportType 导出类型，用于区分不同业务任务
     * @param maxActiveTasks 同一导出类型允许同时运行的任务数上限
     * @param fileNamePrefix 导出文件名前缀，不同业务可传不同值
     * @param exportBizName 导出业务名称，仅用于日志和错误提示
     * @param validator 业务自己的请求校验器
     * @param processor 业务自己的异步处理器，通常直接传 worker::process
     */
    public <T> Result<DeviceAttrInfoExportTaskDTO> submitExportTask(T request,
                                                                    String exportType,
                                                                    int maxActiveTasks,
                                                                    String fileNamePrefix,
                                                                    String exportBizName,
                                                                    AsyncExportRequestValidator<T> validator,
                                                                    AsyncExportTaskProcessor<T> processor) {
        return submitExportTask(
                request,
                exportType,
                maxActiveTasks,
                fileNamePrefix,
                exportBizName,
                DEFAULT_BUSY_MESSAGE,
                validator,
                processor);
    }

    /**
     * 提交异步导出任务。
     * 和上面的重载相比，这里允许业务方自定义“系统繁忙”提示语。
     */
    public <T> Result<DeviceAttrInfoExportTaskDTO> submitExportTask(T request,
                                                                    String exportType,
                                                                    int maxActiveTasks,
                                                                    String fileNamePrefix,
                                                                    String exportBizName,
                                                                    String busyMessage,
                                                                    AsyncExportRequestValidator<T> validator,
                                                                    AsyncExportTaskProcessor<T> processor) {
        String normalizedExportType = requireText(exportType, "exportType不能为空");
        String normalizedFileNamePrefix = requireText(fileNamePrefix, "fileNamePrefix不能为空");
        String normalizedExportBizName = requireText(exportBizName, "exportBizName不能为空");
        String normalizedBusyMessage = StringUtils.hasText(busyMessage) ? busyMessage.trim() : DEFAULT_BUSY_MESSAGE;
        Objects.requireNonNull(validator, "validator不能为空");
        Objects.requireNonNull(processor, "processor不能为空");

        // 第一步，先走业务自己的参数校验。
        validator.validate(request);
        // 第二步，限制同一导出类型的并发任务数量，避免导出过多拖垮系统。
        if (asyncExportTaskService.countByExportTypeAndStatuses(normalizedExportType, ACTIVE_STATUSES) >= maxActiveTasks) {
            return Result.error(normalizedBusyMessage);
        }
        try {
            // 第三步，把原始请求落库，方便后续排查和任务追踪。
            String requestPayload = objectMapper.writeValueAsString(request);
            AsyncExportTask task = asyncExportTaskService.createPendingTask(
                    normalizedExportType, requestPayload, buildFileName(normalizedFileNamePrefix));
            // 第四步，交给具体业务自己的 worker 去异步生成文件并上传。
            processor.process(task, request);
            return Result.ok(toTaskDTO(task));
        } catch (TaskRejectedException ex) {
            log.warn("{}异步导出任务被拒绝", normalizedExportBizName);
            return Result.error(normalizedBusyMessage);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("创建{}异步导出任务失败, exportType={}", normalizedExportBizName, normalizedExportType, ex);
            return Result.error("创建" + normalizedExportBizName + "异步导出任务失败");
        }
    }

    /**
     * 按任务编号查询导出任务状态。
     */
    public Result<DeviceAttrInfoExportTaskDTO> getExportTask(String taskNo) {
        AsyncExportTask task = asyncExportTaskService.getByTaskNo(taskNo);
        if (task == null) {
            return Result.error("导出任务不存在");
        }
        return Result.ok(toTaskDTO(task));
    }

    /**
     * 获取导出文件下载地址。
     * 只有任务成功并且对象存储文件信息存在时才会返回 URL。
     */
    public Result<String> getDownloadUrl(String taskNo) {
        AsyncExportTask task = asyncExportTaskService.getByTaskNo(taskNo);
        if (task == null) {
            return Result.error("导出任务不存在");
        }
        if (!Objects.equals(AsyncExportTaskStatusEnum.SUCCESS.getCode(), task.getTaskStatus())) {
            return Result.error("导出任务尚未完成");
        }
        if (!StringUtils.hasText(task.getBucketName()) || !StringUtils.hasText(task.getObjectKey())) {
            return Result.error("导出文件不存在");
        }
        return Result.ok(buildObjectUrl(task.getBucketName(), task.getObjectKey()));
    }

    /**
     * 统一生成导出文件名。
     * 例如：flight_statistics_20260402103059.xlsx
     */
    private String buildFileName(String fileNamePrefix) {
        return fileNamePrefix + "_" + LocalDateTime.now().format(FILE_NAME_TIME_FORMATTER) + ".xlsx";
    }

    /**
     * 要求字符串参数不能为空且不能只包含空格。
     */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    /**
     * 把数据库任务对象转成接口返回 DTO。
     */
    private DeviceAttrInfoExportTaskDTO toTaskDTO(AsyncExportTask task) {
        String downloadUrl = Objects.equals(AsyncExportTaskStatusEnum.SUCCESS.getCode(), task.getTaskStatus())
                ? buildObjectUrl(task.getBucketName(), task.getObjectKey())
                : null;
        return DeviceAttrInfoExportTaskDTO.builder()
                .taskNo(task.getTaskNo())
                .taskStatus(task.getTaskStatus())
                .fileName(task.getFileName())
                .downloadUrl(downloadUrl)
                .errorMessage(task.getErrorMessage())
                .gmtCreate(task.getGmtCreate())
                .gmtStart(task.getGmtStart())
                .gmtFinish(task.getGmtFinish())
                .build();
    }

    /**
     * 拼接对象存储访问地址。
     */
    private String buildObjectUrl(String bucketName, String objectKey) {
        String base = firstNonBlank(s3Properties.getExternalPrefix(), s3Properties.getPrefix(), s3Properties.getEndpoint());
        if (!StringUtils.hasText(base) || !StringUtils.hasText(bucketName) || !StringUtils.hasText(objectKey)) {
            return null;
        }
        return trimTrailingSlash(base) + "/" + bucketName.trim() + "/" + trimLeadingSlash(objectKey);
    }

    /**
     * 从多个地址配置中取第一个非空值。
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 去掉结尾多余的 /，避免 URL 出现双斜杠。
     */
    private String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 去掉开头多余的 /，避免 URL 出现双斜杠。
     */
    private String trimLeadingSlash(String value) {
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }
}
