package com.jingansi.uav.engine.common.enums;

import lombok.Getter;

/**
 * 异步导出任务状态枚举。
 *
 * <p>对外接口只返回数字状态码，避免前端直接依赖英文枚举值。
 */
@Getter
public enum AsyncExportTaskStatusEnum {

    PENDING(0, "待处理"),
    RUNNING(1, "执行中"),
    SUCCESS(2, "成功"),
    FAILED(3, "失败");

    private final int code;

    private final String desc;

    AsyncExportTaskStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
