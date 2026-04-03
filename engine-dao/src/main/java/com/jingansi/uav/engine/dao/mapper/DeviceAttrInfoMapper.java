package com.jingansi.uav.engine.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jingansi.uav.engine.common.dto.doris.DorisFlightStatisticsRecordDTO;
import com.jingansi.uav.engine.dao.entity.DeviceAttrInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Doris dwd_device_attr_info 查询。
 */
@Mapper
public interface DeviceAttrInfoMapper extends BaseMapper<DeviceAttrInfo> {

    /**
     * 手写分页查询设备属性记录。
     */
    @Select({
            "<script>",
            "SELECT ",
            "device_id AS deviceId, ",
            "acquire_timestamp_format AS acquireTimestampFormat, ",
            "device_type AS deviceType, ",
            "parent_id AS parentId, ",
            "acquire_timestamp AS acquireTimestamp, ",
            "properties, ",
            "tid, ",
            "bid, ",
            "method, ",
            "product_key AS productKey, ",
            "version, ",
            "type, ",
            "update_time AS updateTime ",
            "FROM dwd_device_attr_info ",
            "WHERE device_id = #{deviceId} ",
            "<if test='startTime != null and startTime != \"\"'>",
            "AND acquire_timestamp_format &gt;= #{startTime} ",
            "</if>",
            "ORDER BY acquire_timestamp_format ASC",
            "LIMIT #{offset}, #{limit} ",
            "</script>"
    })
    List<DeviceAttrInfo> selectPageRecords(@Param("deviceId") String deviceId,
                                           @Param("startTime") String startTime,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);

    /**
     * 按设备 ID 和采集时间范围查询设备属性记录。
     */
    @Select("SELECT "
            + "device_id AS deviceId, "
            + "acquire_timestamp_format AS acquireTimestampFormat, "
            + "device_type AS deviceType, "
            + "parent_id AS parentId, "
            + "properties, "
            + "method, "
            + "product_key AS productKey "
            + "FROM dwd_device_attr_info "
            + "WHERE device_id = #{deviceId} "
            + "AND acquire_timestamp_format >= #{startTime} "
            + "AND acquire_timestamp_format <= #{endTime} ")
    List<DeviceAttrInfo> selectByDeviceIdAndTimeRange(@Param("deviceId") String deviceId,
                                                      @Param("startTime") String startTime,
                                                      @Param("endTime") String endTime);

    /**
     * 按单设备和时间范围统计飞行架次、时长、里程增量。
     */
    @Select({
            "<script>",
            "SELECT ",
            "#{deviceId} AS deviceId, ",
            "MAX(totalFlightSorties) - MIN(totalFlightSorties) AS flightSortiesInRange, ",
            "MAX(totalFlightTime) - MIN(totalFlightTime) AS flightTimeSecondsInRange, ",
            "MAX(totalFlightDistance) - MIN(totalFlightDistance) AS flightDistanceMetersInRange ",
            "FROM (",
            "SELECT ",
            "CAST(GET_JSON_STRING(properties, '$.totalFlightSorties') AS BIGINT) AS totalFlightSorties, ",
            "CAST(GET_JSON_STRING(properties, '$.totalFlightTime') AS DOUBLE) AS totalFlightTime, ",
            "CAST(GET_JSON_STRING(properties, '$.totalFlightDistance') AS DOUBLE) AS totalFlightDistance ",
            "FROM dwd_device_attr_info ",
            "WHERE device_type = 'UAV' ",
            "AND device_id = #{deviceId} ",
            "AND acquire_timestamp_format &gt;= #{beginTime} ",
            "AND acquire_timestamp_format &lt;= #{endTime} ",
            ") t",
            "</script>"
    })
    DorisFlightStatisticsRecordDTO selectFlightStatisticsByDeviceIdAndTimeRange(@Param("deviceId") String deviceId,
                                                                                @Param("beginTime") String beginTime,
                                                                                @Param("endTime") String endTime);

}
