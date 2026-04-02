package com.jingansi.uav.engine.common.vo.flight;

import lombok.Data;

import java.util.List;

/**
 * 飞行架次明细查询请求。
 */
@Data
public class FlightSortiesRequest {

    private String beginTime;
    private String endTime;
    private List<String> deviceIds;
}
