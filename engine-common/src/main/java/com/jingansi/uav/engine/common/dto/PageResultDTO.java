package com.jingansi.uav.engine.common.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 通用分页返回。
 */
@Data
@Builder
public class PageResultDTO<T> {

    /**
     * 当前页码，从 1 开始。
     */
    private Integer pageNum;

    /**
     * 当前页大小。
     */
    private Integer pageSize;

    /**
     * 总记录数。
     */
    private Long total;

    /**
     * 总页数。
     */
    private Integer totalPages;

    /**
     * 是否还有下一页。
     */
    private Boolean hasMore;

    /**
     * 当前页数据。
     */
    private List<T> records;
}
