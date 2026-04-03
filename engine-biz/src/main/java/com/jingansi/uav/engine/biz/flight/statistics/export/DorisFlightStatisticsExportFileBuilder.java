package com.jingansi.uav.engine.biz.flight.statistics.export;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jingansi.uav.engine.biz.device.IotDeviceService;
import com.jingansi.uav.engine.common.constant.DataSourceNames;
import com.jingansi.uav.engine.common.dto.doris.DorisFlightStatisticsRecordDTO;
import com.jingansi.uav.engine.common.exception.BizException;
import com.jingansi.uav.engine.common.vo.flight.FlightStatisticsAsyncExportRequest;
import com.jingansi.uav.engine.dao.entity.IotDevice;
import com.jingansi.uav.engine.dao.mapper.DeviceAttrInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Doris 飞行统计导出文件构建器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS(DataSourceNames.DORIS)
public class DorisFlightStatisticsExportFileBuilder {

    private static final int WORKBOOK_WINDOW_SIZE = 200;
    private static final int DEFAULT_COLUMN_WIDTH = 20;
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal HOUR_SECONDS = BigDecimal.valueOf(3600);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    private final DeviceAttrInfoMapper deviceAttrInfoMapper;
    private final IotDeviceService iotDeviceService;

    /**
     * 校验飞行统计导出请求。
     */
    public void validateRequest(FlightStatisticsAsyncExportRequest request) {
        if (request == null) {
            throw new BizException("请求参数不能为空");
        }
        if (!StringUtils.hasText(request.getBeginTime())) {
            throw new BizException("beginTime不能为空");
        }
        if (!StringUtils.hasText(request.getEndTime())) {
            throw new BizException("endTime不能为空");
        }
        String deviceId = normalizeDeviceId(request.getDeviceId());
        if (!StringUtils.hasText(deviceId)) {
            throw new BizException("deviceId不能为空");
        }

        LocalDateTime beginDateTime = parseDateTime(request.getBeginTime());
        LocalDateTime endDateTime = parseDateTime(request.getEndTime());
        if (beginDateTime == null || endDateTime == null) {
            throw new BizException("时间格式错误，请使用 yyyy-MM-dd HH:mm:ss");
        }
        if (beginDateTime.isAfter(endDateTime)) {
            throw new BizException("beginTime不能大于endTime");
        }

        long now = System.currentTimeMillis();
        long beginMillis = beginDateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
        long endMillis = endDateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
        if (beginMillis > now || endMillis > now) {
            throw new BizException("查询时间不能晚于当前时间");
        }
    }

    /**
     * 根据请求构建飞行统计 xlsx 文件。
     */
    public void writeToFile(FlightStatisticsAsyncExportRequest request, File targetFile) {
        validateRequest(request);
        String deviceId = normalizeDeviceId(request.getDeviceId());
        DorisFlightStatisticsRecordDTO record = deviceAttrInfoMapper.selectFlightStatisticsByDeviceIdAndTimeRange(
                deviceId,
                request.getBeginTime().trim(),
                request.getEndTime().trim());
        List<DorisFlightStatisticsRecordDTO> records = record == null ? new ArrayList<>() : Collections.singletonList(record);
        List<DorisFlightStatisticsRecordDTO> exportRows = filterMeaningfulRecords(records);
        if (CollectionUtils.isEmpty(exportRows)) {
            throw new BizException("没有可导出的飞行统计数据");
        }

        Map<String, IotDevice> deviceMap = iotDeviceService.mapByDeviceIds(exportRows.stream()
                .map(DorisFlightStatisticsRecordDTO::getDeviceId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList()));

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(WORKBOOK_WINDOW_SIZE);
             FileOutputStream outputStream = new FileOutputStream(targetFile)) {
            workbook.setCompressTempFiles(true);

            Sheet detailSheet = workbook.createSheet("飞行统计明细");
            detailSheet.setDefaultColumnWidth(DEFAULT_COLUMN_WIDTH);
            writeDetailSheet(detailSheet, exportRows, deviceMap);

            Sheet summarySheet = workbook.createSheet("统计概览");
            summarySheet.setDefaultColumnWidth(DEFAULT_COLUMN_WIDTH);
            writeSummarySheet(summarySheet, request, exportRows);

            workbook.write(outputStream);
            outputStream.flush();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("构建飞行统计导出文件失败, request={}", request, ex);
            throw new BizException("导出飞行统计异常", ex);
        }
    }

    private void writeDetailSheet(Sheet sheet,
                                  List<DorisFlightStatisticsRecordDTO> rows,
                                  Map<String, IotDevice> deviceMap) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("无人机名称");
        headerRow.createCell(1).setCellValue("设备ID");
        headerRow.createCell(2).setCellValue("飞行架次");
        headerRow.createCell(3).setCellValue("飞行里程_km");
        headerRow.createCell(4).setCellValue("飞行时间_小时");

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            DorisFlightStatisticsRecordDTO item = rows.get(rowIndex);
            Row row = sheet.createRow(rowIndex + 1);
            row.createCell(0).setCellValue(resolveDeviceName(item.getDeviceId(), deviceMap));
            row.createCell(1).setCellValue(toStringValue(item.getDeviceId()));
            row.createCell(2).setCellValue(String.valueOf(defaultLong(item.getFlightSortiesInRange())));
            row.createCell(3).setCellValue(decimalToPlainString(metersToKilometers(item.getFlightDistanceMetersInRange())));
            row.createCell(4).setCellValue(decimalToPlainString(secondsToHours(item.getFlightTimeSecondsInRange())));
        }
    }

    private void writeSummarySheet(Sheet sheet,
                                   FlightStatisticsAsyncExportRequest request,
                                   List<DorisFlightStatisticsRecordDTO> rows) {
        long totalSorties = 0L;
        BigDecimal totalDistanceKm = BigDecimal.ZERO;
        BigDecimal totalTimeHours = BigDecimal.ZERO;
        for (DorisFlightStatisticsRecordDTO row : rows) {
            totalSorties += defaultLong(row.getFlightSortiesInRange());
            totalDistanceKm = totalDistanceKm.add(metersToKilometers(row.getFlightDistanceMetersInRange()));
            totalTimeHours = totalTimeHours.add(secondsToHours(row.getFlightTimeSecondsInRange()));
        }

        Row rangeRow = sheet.createRow(0);
        rangeRow.createCell(0).setCellValue("统计区间");
        rangeRow.createCell(1).setCellValue(request.getBeginTime().trim() + " ~ " + request.getEndTime().trim());

        Row coverRow = sheet.createRow(1);
        coverRow.createCell(0).setCellValue("覆盖无人机数量");
        coverRow.createCell(1).setCellValue(String.valueOf(rows.size()));

        Row sortieRow = sheet.createRow(2);
        sortieRow.createCell(0).setCellValue("飞行总架次");
        sortieRow.createCell(1).setCellValue(String.valueOf(totalSorties));

        Row distanceRow = sheet.createRow(3);
        distanceRow.createCell(0).setCellValue("飞行总里程_km");
        distanceRow.createCell(1).setCellValue(decimalToPlainString(scaleDecimal(totalDistanceKm)));

        Row timeRow = sheet.createRow(4);
        timeRow.createCell(0).setCellValue("飞行总时间_小时");
        timeRow.createCell(1).setCellValue(decimalToPlainString(scaleDecimal(totalTimeHours)));
    }

    private String normalizeDeviceId(String deviceId) {
        return StringUtils.hasText(deviceId) ? deviceId.trim() : null;
    }

    private List<DorisFlightStatisticsRecordDTO> filterMeaningfulRecords(List<DorisFlightStatisticsRecordDTO> records) {
        if (CollectionUtils.isEmpty(records)) {
            return new ArrayList<>();
        }
        return records.stream()
                .filter(this::hasMeaningfulData)
                .sorted(Comparator.comparing(DorisFlightStatisticsRecordDTO::getFlightSortiesInRange, Comparator.nullsFirst(Long::compareTo))
                        .reversed()
                        .thenComparing(DorisFlightStatisticsRecordDTO::getDeviceId, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    private boolean hasMeaningfulData(DorisFlightStatisticsRecordDTO record) {
        return defaultLong(record.getFlightSortiesInRange()) > 0
                || defaultDecimal(record.getFlightTimeSecondsInRange()).compareTo(BigDecimal.ZERO) > 0
                || defaultDecimal(record.getFlightDistanceMetersInRange()).compareTo(BigDecimal.ZERO) > 0;
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String resolveDeviceName(String deviceId, Map<String, IotDevice> deviceMap) {
        IotDevice device = deviceMap == null ? null : deviceMap.get(deviceId);
        if (device == null || !StringUtils.hasText(device.getDeviceName())) {
            return toStringValue(deviceId);
        }
        return device.getDeviceName();
    }

    private BigDecimal metersToKilometers(BigDecimal meters) {
        return defaultDecimal(meters).divide(THOUSAND, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal secondsToHours(BigDecimal seconds) {
        return defaultDecimal(seconds).divide(HOUR_SECONDS, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleDecimal(BigDecimal value) {
        return defaultDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private String decimalToPlainString(BigDecimal value) {
        return scaleDecimal(value).toPlainString();
    }

    private String toStringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
