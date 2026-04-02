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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 设备飞行累计状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("iot_device_flight_state")
public class IotDeviceFlightState implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("product_key")
    private String productKey;

    @TableField("device_id")
    private String deviceId;

    @TableField("total_flight_sorties")
    private Long totalFlightSorties;

    @TableField("total_flight_time")
    private Long totalFlightTime;

    @TableField("total_flight_distance")
    private BigDecimal totalFlightDistance;

    @TableField("last_report_timestamp")
    private Long lastReportTimestamp;

    @TableField("gmt_create")
    private Date gmtCreate;

    @TableField("gmt_modified")
    private Date gmtModified;

    @TableField("gmt_create_by")
    private String gmtCreateBy;

    @TableField("gmt_modified_by")
    private String gmtModifiedBy;
}
