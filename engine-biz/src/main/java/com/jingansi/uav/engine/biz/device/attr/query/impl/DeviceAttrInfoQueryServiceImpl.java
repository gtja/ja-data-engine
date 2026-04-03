package com.jingansi.uav.engine.biz.device.attr.query.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingansi.uav.engine.biz.device.attr.query.DeviceAttrInfoQueryService;
import com.jingansi.uav.engine.common.bo.Result;
import com.jingansi.uav.engine.common.constant.DataSourceNames;
import com.jingansi.uav.engine.common.dto.doris.DeviceAttrInfoLatestRecordDTO;
import com.jingansi.uav.engine.common.vo.doris.DeviceAttrInfoLatestQueryRequest;
import com.jingansi.uav.engine.dao.entity.DeviceAttrInfo;
import com.jingansi.uav.engine.dao.mapper.DeviceAttrInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设备属性查询服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS(DataSourceNames.DORIS)
public class DeviceAttrInfoQueryServiceImpl implements DeviceAttrInfoQueryService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_QUERY_COUNT = 1000;
    private static final DateTimeFormatter SECOND_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MICROSECOND_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private final DeviceAttrInfoMapper deviceAttrInfoMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Result<List<DeviceAttrInfoLatestRecordDTO>> page(DeviceAttrInfoLatestQueryRequest request) {
        Result<Void> validationResult = validatePageRequest(request);
        if (!validationResult.isSuccess()) {
            return Result.error(validationResult.getMsg());
        }
        String deviceId = normalizeText(request.getDeviceId());
        List<String> propertiesTypes = resolvePropertiesTypes(request);
        int pageNum = resolvePageNum(request);
        int pageSize = resolvePageSize(request);
        String startTime = normalizeText(request.getStartTime());
        long offset = (long) (pageNum - 1) * pageSize;
        List<DeviceAttrInfo> records = deviceAttrInfoMapper.selectPageRecords(deviceId, startTime, offset, pageSize);
        List<DeviceAttrInfoLatestRecordDTO> result = records.stream()
                .map(item -> toLatestRecord(item, propertiesTypes))
                .collect(Collectors.toList());
        return Result.ok(result);
    }

    /**
     * 去重并整理请求里的属性字段列表。
     */
    private List<String> resolvePropertiesTypes(DeviceAttrInfoLatestQueryRequest request) {
        Set<String> keys = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(request.getPropertiesTypes())) {
            request.getPropertiesTypes().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(keys::add);
        }
        if (StringUtils.hasText(request.getPropertiesType())) {
            for (String item : request.getPropertiesType().split(",")) {
                if (StringUtils.hasText(item)) {
                    keys.add(item.trim());
                }
            }
        }
        return new ArrayList<>(keys);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Result<Void> validatePageRequest(DeviceAttrInfoLatestQueryRequest request) {
        if (request == null) {
            return Result.error("请求参数不能为空");
        }
        String deviceId = normalizeText(request.getDeviceId());
        if (!StringUtils.hasText(deviceId)) {
            return Result.error("deviceId不能为空");
        }
        if (CollectionUtils.isEmpty(resolvePropertiesTypes(request))) {
            return Result.error("propertiesType不能为空");
        }
        int pageNum = resolvePageNum(request);
        int pageSize = resolvePageSize(request);
        if ((long) pageNum * pageSize > MAX_QUERY_COUNT) {
            return Result.error("分页数据最多只能查询1000条");
        }
        if (StringUtils.hasText(request.getStartTime()) && !isValidDateTime(request.getStartTime())) {
            return Result.error("startTime格式错误，要求yyyy-MM-dd HH:mm:ss或yyyy-MM-dd HH:mm:ss.SSSSSS");
        }
        return Result.ok();
    }

    /**
     * 规范页码，非法值统一按第一页处理。
     */
    private int resolvePageNum(DeviceAttrInfoLatestQueryRequest request) {
        Integer pageNum = request.getPageNum();
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 规范分页大小，非法值统一按默认值处理。
     */
    private int resolvePageSize(DeviceAttrInfoLatestQueryRequest request) {
        Integer pageSize = request.getPageSize();
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return pageSize;
    }

    private boolean isValidDateTime(String value) {
        String trimmed = value == null ? null : value.trim();
        if (!StringUtils.hasText(trimmed)) {
            return false;
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{SECOND_FORMATTER, MICROSECOND_FORMATTER}) {
            try {
                LocalDateTime.parse(trimmed, formatter);
                return true;
            } catch (DateTimeParseException ex) {
                // try next formatter
            }
        }
        return false;
    }

    /**
     * 把单条属性记录转换成接口返回对象。
     */
    private DeviceAttrInfoLatestRecordDTO toLatestRecord(DeviceAttrInfo item,
                                                         List<String> propertiesTypes) {
        JsonNode propertiesNode = parseProperties(item.getProperties());
        Map<String, String> properties = new LinkedHashMap<>();
        for (String propertiesType : propertiesTypes) {
            properties.put(propertiesType, extractPropertiesValue(propertiesNode, propertiesType));
        }
        return DeviceAttrInfoLatestRecordDTO.builder()
                .deviceId(item.getDeviceId())
                .acquireTimestampFormat(item.getAcquireTimestampFormat())
                .properties(properties)
                .build();
    }

    /**
     * 解析原始 properties JSON。
     */
    private JsonNode parseProperties(String properties) {
        if (!StringUtils.hasText(properties)) {
            return null;
        }
        try {
            return objectMapper.readTree(properties);
        } catch (Exception ex) {
            log.warn("解析设备属性 properties 失败, properties={}", properties);
            return null;
        }
    }

    /**
     * 提取指定属性字段的值。
     */
    private String extractPropertiesValue(JsonNode propertiesNode, String propertiesType) {
        if (propertiesNode == null || !StringUtils.hasText(propertiesType)) {
            return "";
        }
        JsonNode valueNode = propertiesNode.get(propertiesType);
        if (valueNode == null || valueNode.isNull()) {
            return "";
        }
        return valueNode.isValueNode() ? valueNode.asText() : valueNode.toString();
    }

}
