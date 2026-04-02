package com.jingansi.uav.engine.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 异步导出任务。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("async_export_task")
public class AsyncExportTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_no")
    private String taskNo;

    @TableField("export_type")
    private String exportType;

    @TableField("task_status")
    private Integer taskStatus;

    @TableField("file_name")
    private String fileName;

    @TableField("bucket_name")
    private String bucketName;

    @TableField("object_key")
    private String objectKey;

    @TableField("request_payload")
    private String requestPayload;

    @TableField("error_message")
    private String errorMessage;

    @TableField("gmt_start")
    private Date gmtStart;

    @TableField("gmt_finish")
    private Date gmtFinish;

    @TableField("gmt_create")
    private Date gmtCreate;

    @TableField("gmt_modified")
    private Date gmtModified;

    @TableField("gmt_create_by")
    private String gmtCreateBy;

    @TableField("gmt_modified_by")
    private String gmtModifiedBy;
}
