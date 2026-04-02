package com.jingansi.uav.engine.biz.device.attr.query;

import com.jingansi.uav.engine.common.bo.Result;
import com.jingansi.uav.engine.common.dto.doris.DeviceAttrInfoLatestRecordDTO;
import com.jingansi.uav.engine.common.vo.doris.DeviceAttrInfoLatestQueryRequest;

import java.util.List;

/**
 * 设备属性查询服务。
 */
public interface DeviceAttrInfoQueryService {

    /**
     * 分页查询设备属性数据。
     */
    Result<List<DeviceAttrInfoLatestRecordDTO>> page(DeviceAttrInfoLatestQueryRequest request);
}
