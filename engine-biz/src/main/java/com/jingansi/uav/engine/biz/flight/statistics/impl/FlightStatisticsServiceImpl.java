package com.jingansi.uav.engine.biz.flight.statistics.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jingansi.uav.engine.biz.device.IotDeviceService;
import com.jingansi.uav.engine.biz.flight.statistics.FlightStatisticsService;
import com.jingansi.uav.engine.common.bo.Result;
import com.jingansi.uav.engine.common.constant.DataSourceNames;
import com.jingansi.uav.engine.common.dto.flight.FlightDeviceStatisticsDTO;
import com.jingansi.uav.engine.common.dto.flight.FlightOverallStatisticsDTO;
import com.jingansi.uav.engine.common.dto.flight.FlightSortieDTO;
import com.jingansi.uav.engine.common.enums.FlightSortieSettlementStatusEnum;
import com.jingansi.uav.engine.common.exception.BizException;
import com.jingansi.uav.engine.common.model.CommonTopicReceiver;
import com.jingansi.uav.engine.common.model.MqttMessageHeaders;
import com.jingansi.uav.engine.dao.entity.IotDevice;
import com.jingansi.uav.engine.dao.entity.IotDeviceFlightSortie;
import com.jingansi.uav.engine.dao.entity.IotDeviceFlightState;
import com.jingansi.uav.engine.dao.mapper.IotDeviceFlightSortieMapper;
import com.jingansi.uav.engine.dao.mapper.IotDeviceFlightStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 飞行统计服务。
 *
 * <p>这块逻辑直接迁自控制中心飞行统计能力，但入站改成了当前项目的 MQTT handler 架构。
 * 处理思路保持一致：
 * - 属性上报里拿累计架次、累计时长、累计距离
 * - Redis 缓存当前设备已处理到的累计架次
 * - 当累计架次加 1 时，结算上一架次，再为当前架次创建占位记录
 * - 查询接口从累计状态表和架次明细表里统计结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS(DataSourceNames.MYSQL)
public class FlightStatisticsServiceImpl implements FlightStatisticsService {

    private static final String FLIGHT_SORTIES_CACHE_KEY = "IOT_DEVICE_FLIGHT_SORTIES_%s";
    private static final String UPDATE_BY = "mqtt-flight-statistics";
    private static final BigDecimal MINUTE_SECONDS = BigDecimal.valueOf(60);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    private static final String FIELD_TOTAL_FLIGHT_TIME = "totalFlightTime";
    private static final String FIELD_TOTAL_FLIGHT_SORTIES = "totalFlightSorties";
    private static final String FIELD_TOTAL_FLIGHT_DISTANCE = "totalFlightDistance";

    private final StringRedisTemplate stringRedisTemplate;
    private final IotDeviceFlightStateMapper flightStateMapper;
    private final IotDeviceFlightSortieMapper flightSortieMapper;
    private final IotDeviceService iotDeviceService;

    /**
     * 处理设备属性上报里的累计飞行数据，并把它转换成可查询的飞行状态和架次记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPropertyReport(MqttMessageHeaders headers, CommonTopicReceiver<Map<String, Object>> receiver) {
        Map<String, Object> data = receiver == null ? null : receiver.getData();
        if (data == null || data.isEmpty()) {
            return;
        }
        String productKey = firstNonBlank(receiver.getProductKey(), headers == null ? null : headers.getProductKey());
        String deviceId = firstNonBlank(receiver.getDeviceId(), headers == null ? null : headers.getDeviceId());
        if (!StringUtils.hasText(deviceId)|| !StringUtils.hasText(productKey)) {
            log.warn("飞行统计忽略消息topic={}", headers == null ? null : headers.getTopic());
            return;
        }

        Long totalFlightSorties = parseLong(data.get(FIELD_TOTAL_FLIGHT_SORTIES));
        Long totalFlightTime = parseLong(data.get(FIELD_TOTAL_FLIGHT_TIME));
        BigDecimal totalFlightDistance = parseDecimal(data.get(FIELD_TOTAL_FLIGHT_DISTANCE));
        if (totalFlightSorties == null || totalFlightTime == null || totalFlightDistance == null) {
            return;
        }
        if (totalFlightSorties < 0 || totalFlightTime < 0 || totalFlightDistance.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("设备飞行累计值非法, productKey={}, deviceId={}, sorties={}, time={}, distance={}",
                    productKey, deviceId, totalFlightSorties, totalFlightTime, totalFlightDistance);
            return;
        }

        long reportTimestamp = receiver.getTimestamp() == null ? System.currentTimeMillis() : receiver.getTimestamp();
        Long redisSorties = getCachedSorties(deviceId);

        /**
         * Redis 是主路径。
         *
         * <p>只有 Redis 没有当前设备累计架次时，才回源数据库状态表恢复缓存。
         * 正常 Redis 命中的情况下，不再每条消息都先查数据库。
         */
        if (redisSorties == null) {
            IotDeviceFlightState state = findState(deviceId);
            if (state == null) {
                handleFirstAccess(productKey, deviceId, totalFlightSorties, totalFlightTime, totalFlightDistance, reportTimestamp);
                return;
            }
            normalizeState(state);
            redisSorties = state.getTotalFlightSorties();
            cacheSorties(deviceId, redisSorties);
        }

        /**
         * 累计架次没变化，说明设备仍处于当前架次。
         *不做架次结算。
         */
        if (Objects.equals(totalFlightSorties, redisSorties)) {
            return;
        }

        long sortieDelta = totalFlightSorties - redisSorties;
        if (sortieDelta == 1L) {
            settlePreviousSortie(productKey, deviceId, redisSorties, totalFlightTime, totalFlightDistance, reportTimestamp);
            ensureCurrentSortiePlaceholder(productKey, deviceId, totalFlightSorties, totalFlightTime, totalFlightDistance, reportTimestamp);
            updateStateTotals(productKey, deviceId, totalFlightSorties, totalFlightTime, totalFlightDistance, reportTimestamp);
            cacheSorties(deviceId, totalFlightSorties);
            return;
        }

        if (sortieDelta > 1L) {
            log.warn("设备架次跨越变更(>1)，按一次架次结算后同步。productKey={}, deviceId={}, redisSorties={}, reportSorties={}",
                    productKey, deviceId, redisSorties, totalFlightSorties);
            settlePreviousSortie(productKey, deviceId, redisSorties, totalFlightTime, totalFlightDistance, reportTimestamp);
            ensureCurrentSortiePlaceholder(productKey, deviceId, totalFlightSorties, totalFlightTime, totalFlightDistance, reportTimestamp);
            updateStateTotals(productKey, deviceId, totalFlightSorties, totalFlightTime, totalFlightDistance, reportTimestamp);
            cacheSorties(deviceId, totalFlightSorties);
            return;
        }

        log.warn("设备累计架次回退，按最新累计值重新同步。productKey={}, deviceId={}, redisSorties={}, reportSorties={}",
                productKey, deviceId, redisSorties, totalFlightSorties);
        ensureCurrentSortiePlaceholder(productKey, deviceId, totalFlightSorties, totalFlightTime, totalFlightDistance, reportTimestamp);
        updateStateTotals(productKey, deviceId, totalFlightSorties, totalFlightTime, totalFlightDistance, reportTimestamp);
        cacheSorties(deviceId, totalFlightSorties);
    }

    /**
     * 汇总所有设备的累计飞行架次、飞行时长和飞行距离。
     */
    @Override
    public Result<FlightOverallStatisticsDTO> totalStatistics() {
        List<IotDeviceFlightState> states = flightStateMapper.selectList(new LambdaQueryWrapper<>());
        FlightOverallStatisticsDTO overall = new FlightOverallStatisticsDTO();
        long totalSorties = 0L;
        BigDecimal totalTimeSeconds = BigDecimal.ZERO;
        BigDecimal totalDistance = BigDecimal.ZERO;
        for (IotDeviceFlightState state : states) {
            if (state.getTotalFlightSorties() != null) {
                totalSorties += state.getTotalFlightSorties();
            }
            if (state.getTotalFlightTime() != null) {
                totalTimeSeconds = totalTimeSeconds.add(BigDecimal.valueOf(state.getTotalFlightTime()));
            }
            if (state.getTotalFlightDistance() != null) {
                totalDistance = totalDistance.add(state.getTotalFlightDistance());
            }
        }
        overall.setTotalFlightSorties(totalSorties);
        overall.setTotalFlightTimeMinutes(secondsToMinutes(totalTimeSeconds));
        overall.setTotalFlightDistanceMeters(totalDistance);
        return Result.ok(overall);
    }

    /**
     * 按设备和时间范围统计飞行汇总结果。
     */
    @Override
    public Result<List<FlightDeviceStatisticsDTO>> periodStatistics(List<String> deviceIds, String beginTime, String endTime) {
        if (!StringUtils.hasText(beginTime) && !StringUtils.hasText(endTime)) {
            return Result.ok(convertStatisticsTimeToMinutes(mergeStatisticsWithState(deviceIds, null, null)));
        }
        LocalDateTime beginDateTime = parseDateTime(beginTime);
        LocalDateTime endDateTime = parseDateTime(endTime);
        if (StringUtils.hasText(beginTime) && beginDateTime == null) {
            return Result.error("beginTime格式错误，要求yyyy-MM-dd HH:mm:ss");
        }
        if (StringUtils.hasText(endTime) && endDateTime == null) {
            return Result.error("endTime格式错误，要求yyyy-MM-dd HH:mm:ss");
        }
        if (beginDateTime == null && endDateTime == null) {
            return Result.error("beginTime/endTime格式错误，要求yyyy-MM-dd HH:mm:ss");
        }
        if (beginDateTime == null) {
            beginDateTime = endDateTime;
        }
        if (endDateTime == null) {
            endDateTime = beginDateTime;
        }
        if (beginDateTime.isAfter(endDateTime)) {
            return Result.error("beginTime不能大于endTime");
        }
        Long startMillis = dateTimeStartMillis(beginDateTime);
        Long endMillis = dateTimeEndMillis(endDateTime);
        return Result.ok(convertStatisticsTimeToMinutes(mergeStatisticsWithState(deviceIds, startMillis, endMillis)));
    }

    /**
     * 查询架次明细列表，并补上飞机名称和结算状态描述。
     */
    @Override
    public Result<List<FlightSortieDTO>> listSorties(List<String> deviceIds, String beginTime, String endTime) {
        LocalDateTime beginDateTime = parseDateTime(beginTime);
        LocalDateTime endDateTime = parseDateTime(endTime);
        if (StringUtils.hasText(beginTime) && beginDateTime == null) {
            return Result.error("beginTime格式错误，要求yyyy-MM-dd HH:mm:ss");
        }
        if (StringUtils.hasText(endTime) && endDateTime == null) {
            return Result.error("endTime格式错误，要求yyyy-MM-dd HH:mm:ss");
        }
        if (beginDateTime == null && endDateTime == null && (StringUtils.hasText(beginTime) || StringUtils.hasText(endTime))) {
            return Result.error("beginTime/endTime格式错误，要求yyyy-MM-dd HH:mm:ss");
        }
        if (beginDateTime == null) {
            beginDateTime = endDateTime;
        }
        if (endDateTime == null) {
            endDateTime = beginDateTime;
        }
        if (beginDateTime != null && endDateTime != null && beginDateTime.isAfter(endDateTime)) {
            return Result.error("beginTime不能大于endTime");
        }

        Long startMillis = beginDateTime == null ? null : dateTimeStartMillis(beginDateTime);
        Long endMillis = endDateTime == null ? null : dateTimeEndMillis(endDateTime);
        List<IotDeviceFlightSortie> sorties = flightSortieMapper.selectList(new LambdaQueryWrapper<IotDeviceFlightSortie>()
                .in(!CollectionUtils.isEmpty(deviceIds), IotDeviceFlightSortie::getDeviceId, deviceIds)
                .ge(startMillis != null, IotDeviceFlightSortie::getReportTimestamp, startMillis)
                .le(endMillis != null, IotDeviceFlightSortie::getReportTimestamp, endMillis)
                .orderByAsc(IotDeviceFlightSortie::getDeviceId)
                .orderByAsc(IotDeviceFlightSortie::getSortieNo));
        Map<String, IotDevice> deviceMap = iotDeviceService.mapByDeviceIds(sorties.stream()
                .map(IotDeviceFlightSortie::getDeviceId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList()));
        List<FlightSortieDTO> result = sorties.stream()
                .map(item -> toSortieDTO(item, deviceMap))
                .collect(Collectors.toList());
        return Result.ok(result);
    }

    /**
     * 导出飞行统计 Excel。
     *
     * <p>导出结果按“架次明细”输出，而不是按设备聚合。
     * 每一行就是一条架次记录，包含：
     * 飞机名称、飞行距离、飞行时间、总架次号、结算状态。
     */
    @Override
    public void exportStatistics(List<String> deviceIds, String beginTime, String endTime, HttpServletResponse response) {
        Result<List<FlightSortieDTO>> sortiesResult = listSorties(deviceIds, beginTime, endTime);
        if (!sortiesResult.isSuccess()) {
            throw new BizException(sortiesResult.getMsg());
        }
        try {
            byte[] workbook = buildExportWorkbook(sortiesResult.getData());
            writeExportResponse(workbook, response, "飞行架次_");
        } catch (Exception ex) {
            log.error("导出飞行统计 Excel 失败", ex);
            throw new BizException("导出飞行统计 Excel 失败", ex);
        }
    }

    /**
     * 处理设备首次接入飞行统计时的初始化逻辑。
     *
     * <p>首次接入时会先落状态表，再创建当前架次的未结算占位记录。
     */
    private void handleFirstAccess(String productKey,
                                   String deviceId,
                                   Long totalFlightSorties,
                                   Long totalFlightTime,
                                   BigDecimal totalFlightDistance,
                                   Long reportTimestamp) {
        Date now = new Date();
        boolean inserted = false;
        IotDeviceFlightState state = IotDeviceFlightState.builder()
                .productKey(productKey)
                .deviceId(deviceId)
                .totalFlightSorties(totalFlightSorties)
                .totalFlightTime(totalFlightTime)
                .totalFlightDistance(totalFlightDistance)
                .lastReportTimestamp(reportTimestamp)
                .gmtCreate(now)
                .gmtModified(now)
                .gmtCreateBy(UPDATE_BY)
                .gmtModifiedBy(UPDATE_BY)
                .build();
        try {
            flightStateMapper.insert(state);
            inserted = true;
        } catch (DuplicateKeyException ex) {
            state = findState(deviceId);
            if (state == null) {
                return;
            }
        }
        ensureCurrentSortiePlaceholder(productKey, deviceId, totalFlightSorties, totalFlightTime, totalFlightDistance, reportTimestamp);
        if (!inserted) {
            updateStateTotals(productKey, deviceId, totalFlightSorties, totalFlightTime, totalFlightDistance, reportTimestamp);
        }
        cacheSorties(deviceId, totalFlightSorties);
    }

    /**
     * 结算上一架次。
     *
     * <p>结算时会根据“当前累计值 - 上一架次开始时累计值”算出本架次的时长和距离，
     * 并把该架次标记成已结算。
     */
    private void settlePreviousSortie(String productKey,
                                      String deviceId,
                                      Long previousSortieNo,
                                      Long totalFlightTime,
                                      BigDecimal totalFlightDistance,
                                      Long reportTimestamp) {
        IotDeviceFlightSortie previous = flightSortieMapper.selectOne(new LambdaQueryWrapper<IotDeviceFlightSortie>()
                .eq(IotDeviceFlightSortie::getDeviceId, deviceId)
                .eq(IotDeviceFlightSortie::getSortieNo, previousSortieNo)
                .last("limit 1"));

        Long baselineTime = null;
        BigDecimal baselineDistance = null;
        if (previous != null) {
            if (previous.getStartTotalFlightTime() != null) {
                baselineTime = previous.getStartTotalFlightTime();
            }
            if (previous.getStartTotalFlightDistance() != null) {
                baselineDistance = previous.getStartTotalFlightDistance();
            }
        }

        /**
         * 正常情况下上一架次的占位记录一定存在，直接用它的起始累计量做差分。
         *
         * <p>只有占位记录不存在时，才回数据库状态表兜底一次，
         * 这样既保证正确性，也不会在 Redis 正常命中时每条消息都查库。
         */
        if (baselineTime == null || baselineDistance == null) {
            IotDeviceFlightState state = findState(deviceId);
            if (state != null) {
                normalizeState(state);
                if (baselineTime == null) {
                    baselineTime = state.getTotalFlightTime();
                }
                if (baselineDistance == null) {
                    baselineDistance = state.getTotalFlightDistance();
                }
            }
        }

        if (baselineTime == null) {
            baselineTime = 0L;
        }
        if (baselineDistance == null) {
            baselineDistance = BigDecimal.ZERO;
        }

        long flightTime = Math.max(0L, totalFlightTime - baselineTime);
        BigDecimal flightDistance = totalFlightDistance.subtract(baselineDistance);
        if (flightDistance.compareTo(BigDecimal.ZERO) < 0) {
            flightDistance = BigDecimal.ZERO;
        }

        Date now = new Date();
        if (previous == null) {
            IotDeviceFlightSortie settled = IotDeviceFlightSortie.builder()
                    .productKey(productKey)
                    .deviceId(deviceId)
                    .sortieNo(previousSortieNo)
                    .flightTime(flightTime)
                    .flightDistance(flightDistance)
                    .startTotalFlightTime(baselineTime)
                    .endTotalFlightTime(totalFlightTime)
                    .startTotalFlightDistance(baselineDistance)
                    .endTotalFlightDistance(totalFlightDistance)
                    .reportTimestamp(reportTimestamp)
                    .settlementStatus(FlightSortieSettlementStatusEnum.SETTLED.getCode())
                    .gmtCreate(now)
                    .gmtModified(now)
                    .gmtCreateBy(UPDATE_BY)
                    .gmtModifiedBy(UPDATE_BY)
                    .build();
            try {
                flightSortieMapper.insert(settled);
            } catch (DuplicateKeyException ex) {
                log.info("补偿结算架次已存在，忽略。deviceId={}, sortieNo={}", deviceId, previousSortieNo);
            }
            return;
        }

        previous.setProductKey(productKey);
        previous.setFlightTime(flightTime);
        previous.setFlightDistance(flightDistance);
        previous.setEndTotalFlightTime(totalFlightTime);
        previous.setEndTotalFlightDistance(totalFlightDistance);
        previous.setReportTimestamp(reportTimestamp);
        previous.setSettlementStatus(FlightSortieSettlementStatusEnum.SETTLED.getCode());
        previous.setGmtModified(now);
        previous.setGmtModifiedBy(UPDATE_BY);
        flightSortieMapper.updateById(previous);
    }

    /**
     * 当前架次固定存一条占位记录。
     *
     * <p>这样下次累计架次增长时，就能直接拿这条记录的起始累计量去结算上一架次。
     * 当前占位记录始终标记为未结算。
     */
    private void ensureCurrentSortiePlaceholder(String productKey,
                                                String deviceId,
                                                Long sortieNo,
                                                Long totalFlightTime,
                                                BigDecimal totalFlightDistance,
                                                Long reportTimestamp) {
        Date now = new Date();
        IotDeviceFlightSortie exists = flightSortieMapper.selectOne(new LambdaQueryWrapper<IotDeviceFlightSortie>()
                .eq(IotDeviceFlightSortie::getDeviceId, deviceId)
                .eq(IotDeviceFlightSortie::getSortieNo, sortieNo)
                .last("limit 1"));
        if (exists != null) {
            exists.setProductKey(productKey);
            exists.setFlightTime(0L);
            exists.setFlightDistance(BigDecimal.ZERO);
            exists.setStartTotalFlightTime(totalFlightTime);
            exists.setEndTotalFlightTime(totalFlightTime);
            exists.setStartTotalFlightDistance(totalFlightDistance);
            exists.setEndTotalFlightDistance(totalFlightDistance);
            exists.setReportTimestamp(reportTimestamp);
            exists.setSettlementStatus(FlightSortieSettlementStatusEnum.UNSETTLED.getCode());
            exists.setGmtModified(now);
            exists.setGmtModifiedBy(UPDATE_BY);
            flightSortieMapper.updateById(exists);
            return;
        }

        IotDeviceFlightSortie placeholder = IotDeviceFlightSortie.builder()
                .productKey(productKey)
                .deviceId(deviceId)
                .sortieNo(sortieNo)
                .flightTime(0L)
                .flightDistance(BigDecimal.ZERO)
                .startTotalFlightTime(totalFlightTime)
                .endTotalFlightTime(totalFlightTime)
                .startTotalFlightDistance(totalFlightDistance)
                .endTotalFlightDistance(totalFlightDistance)
                .reportTimestamp(reportTimestamp)
                .settlementStatus(FlightSortieSettlementStatusEnum.UNSETTLED.getCode())
                .gmtCreate(now)
                .gmtModified(now)
                .gmtCreateBy(UPDATE_BY)
                .gmtModifiedBy(UPDATE_BY)
                .build();
        try {
            flightSortieMapper.insert(placeholder);
        } catch (DuplicateKeyException ex) {
            log.info("当前架次占位记录已存在，忽略。deviceId={}, sortieNo={}", deviceId, sortieNo);
        }
    }

    /**
     * 同步更新设备当前累计飞行状态。
     *
     * <p>这里直接按 deviceId 做 update-or-insert，
     * 不再为了更新状态先额外查一次数据库。
     */
    private void updateStateTotals(String productKey,
                                   String deviceId,
                                   Long totalFlightSorties,
                                   Long totalFlightTime,
                                   BigDecimal totalFlightDistance,
                                   Long reportTimestamp) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        Date now = new Date();
        IotDeviceFlightState updateState = new IotDeviceFlightState();
        updateState.setProductKey(productKey);
        updateState.setTotalFlightSorties(totalFlightSorties);
        updateState.setTotalFlightTime(totalFlightTime);
        updateState.setTotalFlightDistance(totalFlightDistance);
        updateState.setLastReportTimestamp(reportTimestamp);
        updateState.setGmtModified(now);
        updateState.setGmtModifiedBy(UPDATE_BY);

        int updated = flightStateMapper.update(updateState, new LambdaQueryWrapper<IotDeviceFlightState>()
                .eq(IotDeviceFlightState::getDeviceId, deviceId));
        if (updated > 0) {
            return;
        }

        IotDeviceFlightState insertState = IotDeviceFlightState.builder()
                .productKey(productKey)
                .deviceId(deviceId)
                .totalFlightSorties(totalFlightSorties)
                .totalFlightTime(totalFlightTime)
                .totalFlightDistance(totalFlightDistance)
                .lastReportTimestamp(reportTimestamp)
                .gmtCreate(now)
                .gmtModified(now)
                .gmtCreateBy(UPDATE_BY)
                .gmtModifiedBy(UPDATE_BY)
                .build();
        try {
            flightStateMapper.insert(insertState);
        } catch (DuplicateKeyException ex) {
            flightStateMapper.update(updateState, new LambdaQueryWrapper<IotDeviceFlightState>()
                    .eq(IotDeviceFlightState::getDeviceId, deviceId));
        }
    }

    /**
     * 从 Redis 读取设备最近一次处理到的累计架次。
     */
    private Long getCachedSorties(String deviceId) {
        String value = stringRedisTemplate.opsForValue().get(cacheKey(deviceId));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ex) {
            log.warn("飞行架次缓存值非法，deviceId={}, value={}", deviceId, value);
            return null;
        }
    }

    /**
     * 把设备当前累计架次写回 Redis，减少每次都查数据库。
     */
    private void cacheSorties(String deviceId, Long sorties) {
        if (!StringUtils.hasText(deviceId) || sorties == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(cacheKey(deviceId), String.valueOf(sorties));
    }

    /**
     * 根据设备 ID 查询飞行累计状态。
     */
    private IotDeviceFlightState findState(String deviceId) {
        return flightStateMapper.selectOne(new LambdaQueryWrapper<IotDeviceFlightState>()
                .eq(IotDeviceFlightState::getDeviceId, deviceId)
                .last("limit 1"));
    }

    /**
     * 生成 Redis 缓存 key。
     */
    private String cacheKey(String deviceId) {
        return String.format(FLIGHT_SORTIES_CACHE_KEY, deviceId);
    }

    /**
     * 兜底修正状态表空值，避免差分计算时出现空指针。
     */
    private void normalizeState(IotDeviceFlightState state) {
        if (state.getTotalFlightSorties() == null) {
            state.setTotalFlightSorties(0L);
        }
        if (state.getTotalFlightTime() == null) {
            state.setTotalFlightTime(0L);
        }
        if (state.getTotalFlightDistance() == null) {
            state.setTotalFlightDistance(BigDecimal.ZERO);
        }
    }

    /**
     * 合并架次表统计结果和状态表设备列表，保证没有架次记录的设备也能返回零值结果。
     */
    private List<FlightDeviceStatisticsDTO> mergeStatisticsWithState(List<String> deviceIds, Long startTime, Long endTime) {
        List<FlightDeviceStatisticsDTO> statistics = flightSortieMapper.listDeviceStatistics(deviceIds, startTime, endTime);
        Map<String, FlightDeviceStatisticsDTO> map = new HashMap<>();
        if (!CollectionUtils.isEmpty(statistics)) {
            statistics.forEach(item -> {
                fillDefault(item);
                map.put(item.getDeviceId(), item);
            });
        }

        List<IotDeviceFlightState> states = flightStateMapper.selectList(new LambdaQueryWrapper<IotDeviceFlightState>()
                .in(!CollectionUtils.isEmpty(deviceIds), IotDeviceFlightState::getDeviceId, deviceIds));
        if (!CollectionUtils.isEmpty(states)) {
            states.forEach(state -> map.computeIfAbsent(state.getDeviceId(), key -> {
                FlightDeviceStatisticsDTO dto = new FlightDeviceStatisticsDTO();
                dto.setProductKey(state.getProductKey());
                dto.setDeviceId(state.getDeviceId());
                dto.setTotalFlightSorties(0L);
                dto.setTotalFlightTimeMinutes(BigDecimal.ZERO);
                dto.setTotalFlightDistanceMeters(BigDecimal.ZERO);
                return dto;
            }));
        }
        return map.values().stream()
                .sorted(Comparator.comparing(FlightDeviceStatisticsDTO::getDeviceId, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    /**
     * 给聚合统计对象补齐默认值，避免前端收到 null。
     */
    private void fillDefault(FlightDeviceStatisticsDTO dto) {
        if (dto.getTotalFlightSorties() == null) {
            dto.setTotalFlightSorties(0L);
        }
        if (dto.getTotalFlightTimeMinutes() == null) {
            dto.setTotalFlightTimeMinutes(BigDecimal.ZERO);
        }
        if (dto.getTotalFlightDistanceMeters() == null) {
            dto.setTotalFlightDistanceMeters(BigDecimal.ZERO);
        }
    }

    /**
     * 把数据库架次实体转换成接口返回对象，并补上结算状态描述。
     */
    private FlightSortieDTO toSortieDTO(IotDeviceFlightSortie sortie, Map<String, IotDevice> deviceMap) {
        IotDevice device = deviceMap == null ? null : deviceMap.get(sortie.getDeviceId());
        FlightSortieDTO dto = new FlightSortieDTO();
        dto.setProductKey(sortie.getProductKey());
        dto.setDeviceId(sortie.getDeviceId());
        dto.setDeviceName(device != null && StringUtils.hasText(device.getDeviceName()) ? device.getDeviceName() : sortie.getDeviceId());
        dto.setSortieNo(sortie.getSortieNo());
        dto.setFlightTimeMinutes(secondsToMinutes(sortie.getFlightTime()));
        dto.setFlightDistanceMeters(sortie.getFlightDistance());
        dto.setReportTimestampMillis(sortie.getReportTimestamp());
        dto.setSettlementStatus(sortie.getSettlementStatus());
        return dto;
    }

    /**
     * 生成 Excel 二进制内容。
     */
    private byte[] buildExportWorkbook(List<FlightSortieDTO> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("飞行记录");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("飞机名称");
            headerRow.createCell(1).setCellValue("设备ID");
            headerRow.createCell(2).setCellValue("飞行距离（米）");
            headerRow.createCell(3).setCellValue("飞行时间（分钟）");
            headerRow.createCell(4).setCellValue("目前飞行总架次");
            headerRow.createCell(5).setCellValue("架次结算状态");

            for (int index = 0; index < rows.size(); index++) {
                FlightSortieDTO item = rows.get(index);
                Row dataRow = sheet.createRow(index + 1);
                dataRow.createCell(0).setCellValue(item.getDeviceName());
                dataRow.createCell(1).setCellValue(item.getDeviceId());
                dataRow.createCell(2).setCellValue(decimalToPlainString(item.getFlightDistanceMeters()));
                dataRow.createCell(3).setCellValue(decimalToPlainString(item.getFlightTimeMinutes()));
                dataRow.createCell(4).setCellValue(String.valueOf(defaultLong(item.getSortieNo())));
                dataRow.createCell(5).setCellValue(item.getSettlementStatusDesc());
            }

            for (int columnIndex = 0; columnIndex < 6; columnIndex++) {
                sheet.autoSizeColumn(columnIndex);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void writeExportResponse(byte[] workbook, HttpServletResponse response, String filePrefix) throws IOException {
        String fileName = filePrefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
        response.setContentLength(workbook.length);
        response.getOutputStream().write(workbook);
        response.getOutputStream().flush();
    }

    /**
     * 把聚合统计里的秒数统一转换成分钟。
     */
    private List<FlightDeviceStatisticsDTO> convertStatisticsTimeToMinutes(List<FlightDeviceStatisticsDTO> statistics) {
        if (statistics == null) {
            return new ArrayList<>();
        }
        statistics.forEach(item -> item.setTotalFlightTimeMinutes(secondsToMinutes(item.getTotalFlightTimeMinutes())));
        return statistics;
    }

    /**
     * 把秒换算成分钟，保留两位小数。
     */
    private BigDecimal secondsToMinutes(Number seconds) {
        if (seconds == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = new BigDecimal(String.valueOf(seconds));
        return value.divide(MINUTE_SECONDS, 2, RoundingMode.HALF_UP);
    }

    /**
     * 解析请求里的日期时间字符串。
     */
    private LocalDateTime parseDateTime(String dateTime) {
        if (!StringUtils.hasText(dateTime)) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTime.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * 把时间转换成毫秒时间戳，表示该秒的开始。
     */
    private Long dateTimeStartMillis(LocalDateTime dateTime) {
        return dateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
    }

    /**
     * 把时间转换成毫秒时间戳，表示该秒的结束。
     */
    private Long dateTimeEndMillis(LocalDateTime dateTime) {
        return dateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli() + 999;
    }

    /**
     * 把消息体里的累计数值安全转换成 Long。
     */
    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String str = String.valueOf(value).trim();
        if (!StringUtils.hasText(str)) {
            return null;
        }
        try {
            return new BigDecimal(str).longValue();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 把消息体里的累计距离安全转换成 BigDecimal。
     */
    private BigDecimal parseDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        String str = String.valueOf(value).trim();
        if (!StringUtils.hasText(str)) {
            return null;
        }
        try {
            return new BigDecimal(str);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 返回两个字符串里第一个非空白值。
     */
    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second;
    }

    /**
     * BigDecimal 空值兜底。
     */
    private BigDecimal defaultBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Long 空值兜底。
     */
    private Long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 导出时统一转成普通字符串，避免 Excel 里出现科学计数法。
     */
    private String decimalToPlainString(BigDecimal value) {
        return defaultBigDecimal(value).stripTrailingZeros().toPlainString();
    }

}
