package com.jingansi.uav.engine.common.dto.doris;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * 设备属性异步导出任务返回。
 */
@Data
@Builder
public class DeviceAttrInfoExportTaskDTO {

    private String taskNo;

    /**
     * 导出任务类型。
     */
    private String exportType;

    /**
     * 任务状态：0待处理，1执行中，2成功，3失败。
     */
    private Integer taskStatus;

    private String fileName;

    private String downloadUrl;

    private String errorMessage;

    private Date gmtCreate;

    private Date gmtStart;

    private Date gmtFinish;
}
