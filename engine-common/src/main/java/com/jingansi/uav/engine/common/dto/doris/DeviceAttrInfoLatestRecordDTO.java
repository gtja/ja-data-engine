package com.jingansi.uav.engine.common.dto.doris;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

/**
 * 设备属性最近记录查询结果。
 *
 * <p>固定字段直接输出，动态属性字段通过 {@link JsonAnyGetter}
 * 展开到顶层 JSON。
 */
@Data
@Builder
public class DeviceAttrInfoLatestRecordDTO {

    private String deviceId;
    private String acquireTimestampFormat;

    /**
     * 动态属性容器，仅用于序列化时展开成顶层字段。
     */
    @JsonIgnore
    private Map<String, String> properties;

    @JsonAnyGetter
    public Map<String, String> getProperties() {
        return properties == null ? Collections.emptyMap() : properties;
    }
}
