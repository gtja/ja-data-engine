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
 * 设备飞行架次明细。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("iot_device_flight_sortie")
public class IotDeviceFlightSortie implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("product_key")
    private String productKey;

    @TableField("device_id")
    private String deviceId;

    @TableField("sortie_no")
    private Long sortieNo;

    @TableField("flight_time")
    private Long flightTime;

    @TableField("flight_distance")
    private BigDecimal flightDistance;

    @TableField("start_total_flight_time")
    private Long startTotalFlightTime;

    @TableField("end_total_flight_time")
    private Long endTotalFlightTime;

    @TableField("start_total_flight_distance")
    private BigDecimal startTotalFlightDistance;

    @TableField("end_total_flight_distance")
    private BigDecimal endTotalFlightDistance;

    @TableField("report_timestamp")
    private Long reportTimestamp;

    /**
     * 结算状态。
     * 0=未结算，1=已结算。
     */
    @TableField("settlement_status")
    private Integer settlementStatus;

    @TableField("gmt_create")
    private Date gmtCreate;

    @TableField("gmt_modified")
    private Date gmtModified;

    @TableField("gmt_create_by")
    private String gmtCreateBy;

    @TableField("gmt_modified_by")
    private String gmtModifiedBy;
}
