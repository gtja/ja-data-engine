package com.jingansi.uav.engine.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jingansi.uav.engine.common.dto.flight.FlightDeviceStatisticsDTO;
import com.jingansi.uav.engine.dao.entity.IotDeviceFlightSortie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IotDeviceFlightSortieMapper extends BaseMapper<IotDeviceFlightSortie> {

    @Select("<script>"
            + "SELECT MAX(product_key) AS productKey, device_id AS deviceId, "
            + "COUNT(1) AS totalFlightSorties, "
            + "COALESCE(SUM(flight_time), 0) AS totalFlightTimeMinutes, "
            + "COALESCE(SUM(flight_distance), 0) AS totalFlightDistanceMeters "
            + "FROM iot_device_flight_sortie "
            + "<where>"
            + "<if test='deviceIds != null and deviceIds.size > 0'>"
            + " AND device_id IN "
            + " <foreach item='item' collection='deviceIds' open='(' separator=',' close=')'>#{item}</foreach>"
            + "</if>"
            + "<if test='startTime != null'> AND report_timestamp <![CDATA[>=]]> #{startTime} </if>"
            + "<if test='endTime != null'> AND report_timestamp <![CDATA[<=]]> #{endTime} </if>"
            + "</where>"
            + "GROUP BY device_id "
            + "ORDER BY device_id ASC"
            + "</script>")
    List<FlightDeviceStatisticsDTO> listDeviceStatistics(@Param("deviceIds") List<String> deviceIds,
                                                         @Param("startTime") Long startTime,
                                                         @Param("endTime") Long endTime);
}
