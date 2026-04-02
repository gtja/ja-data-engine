package com.jingansi.uav.engine.biz.device.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jingansi.uav.engine.biz.device.IotDeviceService;
import com.jingansi.uav.engine.common.constant.DataSourceNames;
import com.jingansi.uav.engine.dao.entity.IotDevice;
import com.jingansi.uav.engine.dao.mapper.IotDeviceMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备基础服务实现。
 */
@Service
@DS(DataSourceNames.MYSQL)
public class IotDeviceServiceImpl extends ServiceImpl<IotDeviceMapper, IotDevice> implements IotDeviceService {

    /**
     * 按设备 ID 批量查询设备。
     */
    @Override
    public List<IotDevice> listByDeviceIds(List<String> deviceIds) {
        if (CollectionUtils.isEmpty(deviceIds)) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<IotDevice>()
                .in(IotDevice::getDeviceId, deviceIds)
                .orderByAsc(IotDevice::getDeviceId));
    }

    /**
     * 把设备列表转换成以 deviceId 为 key 的映射。
     */
    @Override
    public Map<String, IotDevice> mapByDeviceIds(List<String> deviceIds) {
        return listByDeviceIds(deviceIds).stream()
                .collect(Collectors.toMap(IotDevice::getDeviceId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }
}
