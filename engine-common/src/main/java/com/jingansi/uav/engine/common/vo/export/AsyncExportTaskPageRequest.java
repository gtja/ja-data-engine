package com.jingansi.uav.engine.common.vo.export;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * 异步导出任务分页查询请求。
 */
@Data
public class AsyncExportTaskPageRequest {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 设备 ID。
     */
    private String deviceId;

    /**
     * 导出类型。
     */
    private String exportType;

    /**
     * 页码，从 1 开始。
     */
    @JsonAlias({"pageNo", "current"})
    private Integer pageNum = DEFAULT_PAGE_NUM;

    /**
     * 每页条数。
     */
    @JsonAlias({"size", "limit"})
    private Integer pageSize = DEFAULT_PAGE_SIZE;
}
