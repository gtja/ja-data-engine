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
     * 按设备列表和时间范围统计飞行架次、时长、里程增量。
     */
    @Select({
            "<script>",
            "SELECT ",
            "device_id AS deviceId, ",
            "MAX(CAST(GET_JSON_STRING(properties, '$.totalFlightSorties') AS BIGINT)) ",
            "- MIN(CAST(GET_JSON_STRING(properties, '$.totalFlightSorties') AS BIGINT)) AS flightSortiesInRange, ",
            "MAX(CAST(GET_JSON_STRING(properties, '$.totalFlightTime') AS DOUBLE)) ",
            "- MIN(CAST(GET_JSON_STRING(properties, '$.totalFlightTime') AS DOUBLE)) AS flightTimeSecondsInRange, ",
            "MAX(CAST(GET_JSON_STRING(properties, '$.totalFlightDistance') AS DOUBLE)) ",
            "- MIN(CAST(GET_JSON_STRING(properties, '$.totalFlightDistance') AS DOUBLE)) AS flightDistanceMetersInRange ",
            "FROM dwd_device_attr_info ",
            "WHERE device_type = 'UAV' ",
            "AND acquire_timestamp_format &gt;= #{beginTime} ",
            "AND acquire_timestamp_format &lt;= #{endTime} ",
            "<if test='deviceIds != null and deviceIds.size() > 0'>",
            "AND device_id IN ",
            "<foreach collection='deviceIds' item='deviceId' open='(' separator=',' close=')'>",
            "#{deviceId}",
            "</foreach>",
            "</if>",
            "GROUP BY device_id ",
            "ORDER BY flightSortiesInRange DESC, device_id ASC ",
            "</script>"
    })
    List<DorisFlightStatisticsRecordDTO> selectFlightStatisticsByDeviceIdsAndTimeRange(@Param("deviceIds") List<String> deviceIds,
                                                                                        @Param("beginTime") String beginTime,
                                                                                        @Param("endTime") String endTime);

}
