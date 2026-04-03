package com.jingansi.uav.engine.common.enums;

import lombok.Getter;

/**
 * 异步导出任务类型枚举。
 */
@Getter
public enum AsyncExportTypeEnum {

    DEVICE_ATTR_INFO("DEVICE_ATTR_INFO", "设备属性", "device_properties_info", 2),
    DORIS_FLIGHT_STATISTICS("DORIS_FLIGHT_STATISTICS", "飞行统计", "flight_statistics", 3);

    private final String code;

    private final String bizName;

    private final String fileNamePrefix;

    private final int maxActiveTasks;

    AsyncExportTypeEnum(String code, String bizName, String fileNamePrefix, int maxActiveTasks) {
        this.code = code;
        this.bizName = bizName;
        this.fileNamePrefix = fileNamePrefix;
        this.maxActiveTasks = maxActiveTasks;
    }

    public static AsyncExportTypeEnum fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String normalizedCode = code.trim();
        for (AsyncExportTypeEnum value : values()) {
            if (value.code.equalsIgnoreCase(normalizedCode)) {
                return value;
            }
        }
        return null;
    }
}
