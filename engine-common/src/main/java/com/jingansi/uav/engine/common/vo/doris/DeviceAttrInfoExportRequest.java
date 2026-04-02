package com.jingansi.uav.engine.common.vo.doris;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 设备属性信息导出请求。
 */
@Data
public class DeviceAttrInfoExportRequest {

    /**
     * 开始时间，格式 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd HH:mm:ss.SSSSSS。
     */
    private String startTime;

    /**
     * 结束时间，格式 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd HH:mm:ss.SSSSSS。
     */
    private String endTime;

    /**
     * 设备 ID。
     */
    private String deviceId;

    /**
     * properties 中需要提取的 key，逗号分隔。
     */
    private String propertiesType;

    /**
     * properties 中需要提取的 key 列表。
     */
    private List<String> propertiesTypes;

    /**
     * properties key 对应的导出表头名称。
     */
    private Map<String, String> propertiesTypeNameMap;
}
