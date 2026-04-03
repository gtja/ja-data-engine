package com.jingansi.uav.engine.common.vo.doris;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 设备属性分页查询请求。
 */
@Data
public class DeviceAttrInfoLatestQueryRequest {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 设备 ID。
     */
    private String deviceId;

    /**
     * 开始时间，格式 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd HH:mm:ss.SSSSSS。
     */

    private String startTime;

    /**
     * 需要从 properties 中提取的字段列表。
     */
    private List<String> propertiesTypes;

    /**
     * 需要从 properties 中提取的字段，逗号分隔。
     */
    private String propertiesType;

    /**
     * 字段中文名称映射。
     */
    private Map<String, String> propertiesTypeNameMap;

    /**
     * 页码，从 1 开始。
     */
    @JsonAlias({"pageNo", "current", "page"})
    private Integer pageNum = DEFAULT_PAGE_NUM;

    /**
     * 每页条数。
     */
    @JsonAlias({"size", "limit", "pagesize", "page_size"})
    private Integer pageSize = DEFAULT_PAGE_SIZE;
}
