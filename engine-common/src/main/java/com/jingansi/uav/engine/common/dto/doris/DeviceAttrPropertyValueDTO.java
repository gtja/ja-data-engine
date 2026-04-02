package com.jingansi.uav.engine.common.dto.doris;

import lombok.Builder;
import lombok.Data;

/**
 * 单个属性字段的名称和值。
 */
@Data
@Builder
public class DeviceAttrPropertyValueDTO {

    /**
     * 属性字段 key，例如 homeLatitude。
     */
    private String propertiesType;

    /**
     * 属性字段中文名，例如 返航点纬度。
     */
    private String propertiesTypeName;

    /**
     * 当前记录里的属性值。
     */
    private String value;
}
