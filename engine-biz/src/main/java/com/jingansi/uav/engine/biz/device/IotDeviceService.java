package com.jingansi.uav.engine.biz.device;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jingansi.uav.engine.dao.entity.IotDevice;

import java.util.List;
import java.util.Map;

/**
 * 设备基础服务。
 */
public interface IotDeviceService extends IService<IotDevice> {

    /**
     * 按设备 ID 列表查询设备。
     */
    List<IotDevice> listByDeviceIds(List<String> deviceIds);

    /**
     * 按设备 ID 列表组装设备映射，便于业务侧按 deviceId 快速取设备名称等信息。
     */
    Map<String, IotDevice> mapByDeviceIds(List<String> deviceIds);
}
