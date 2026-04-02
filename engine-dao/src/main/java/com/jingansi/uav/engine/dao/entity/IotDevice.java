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
 * 设备信息表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("iot_device")
public class IotDevice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("product_id")
    private Long productId;

    @TableField("product_key")
    private String productKey;

    @TableField("product_version")
    private String productVersion;

    @TableField("parent_id")
    private String parentId;

    @TableField("device_id")
    private String deviceId;

    @TableField("device_name")
    private String deviceName;

    @TableField("device_secret")
    private String deviceSecret;

    @TableField("device_status")
    private String deviceStatus;

    @TableField("gmt_last_online")
    private Date gmtLastOnline;

    @TableField("register_time")
    private Date registerTime;

    @TableField("device_extend")
    private String deviceExtend;

    @TableField("gmt_create")
    private Date gmtCreate;

    @TableField("gmt_modified")
    private Date gmtModified;

    @TableField("gmt_create_by")
    private String gmtCreateBy;

    @TableField("gmt_modified_by")
    private String gmtModifiedBy;

    @TableField("parent_product_key")
    private String parentProductKey;

    @TableField("org_code")
    private String orgCode;
}
