package com.jingansi.uav.engine.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * Doris dwd_device_attr_info 表实体。
 */
@Data
@TableName("dwd_device_attr_info")
public class DeviceAttrInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableField("device_id")
    private String deviceId;

    @TableField("acquire_timestamp_format")
    private String acquireTimestampFormat;

    @TableField("device_type")
    private String deviceType;

    @TableField("parent_id")
    private String parentId;

    @TableField("acquire_timestamp")
    private Long acquireTimestamp;

    @TableField("properties")
    private String properties;

    @TableField("tid")
    private String tid;

    @TableField("bid")
    private String bid;

    @TableField("method")
    private String method;

    @TableField("product_key")
    private String productKey;

    @TableField("version")
    private String version;

    @TableField("type")
    private String type;

    @TableField("update_time")
    private String updateTime;
}
