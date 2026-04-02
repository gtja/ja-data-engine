package com.jingansi.uav.engine.biz.device.attr.export;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingansi.uav.engine.common.constant.DataSourceNames;
import com.jingansi.uav.engine.common.exception.BizException;
import com.jingansi.uav.engine.common.vo.doris.DeviceAttrInfoExportRequest;
import com.jingansi.uav.engine.dao.entity.DeviceAttrInfo;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备属性导出文件构建器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS(DataSourceNames.DORIS)
public class DeviceAttrInfoExportFileBuilder {

    private static final int WORKBOOK_WINDOW_SIZE = 200;
    private static final int DEFAULT_COLUMN_WIDTH = 20;
    private static final DateTimeFormatter SECOND_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MICROSECOND_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    private final DeviceAttrInfoMapper deviceAttrInfoMapper;
    private final ObjectMapper objectMapper;

    public void validateRequest(DeviceAttrInfoExportRequest request) {
        if (request == null) {
            throw new BizException("请求参数不能为空");
        }
        if (!StringUtils.hasText(request.getStartTime())) {
            throw new BizException("开始时间不能为空");
        }
        if (!StringUtils.hasText(request.getEndTime())) {
            throw new BizException("结束时间不能为空");
        }
        if (!StringUtils.hasText(request.getDeviceId())) {
            throw new BizException("deviceId不能为空");
        }
        if (CollectionUtils.isEmpty(resolvePropertiesTypes(request))) {
            throw new BizException("propertiesType不能为空");
        }

        LocalDateTime startDateTime = parseDateTime(request.getStartTime());
        LocalDateTime endDateTime = parseDateTime(request.getEndTime());
        if (startDateTime == null || endDateTime == null) {
            throw new BizException("时间格式错误，请使用 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd HH:mm:ss.SSSSSS");
        }
        if (startDateTime.isAfter(endDateTime)) {
            throw new BizException("开始时间不能大于结束时间");
        }
        long now = System.currentTimeMillis();
        long startMillis = startDateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
        long endMillis = endDateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
        if (startMillis > now || endMillis > now) {
            throw new BizException("查询时间不能晚于当前时间");
        }
    }

    public void writeToFile(DeviceAttrInfoExportRequest request, File targetFile) {
        validateRequest(request);
        List<String> propertiesTypes = resolvePropertiesTypes(request);
        List<DeviceAttrInfo> dataList = deviceAttrInfoMapper.selectByDeviceIdAndTimeRange(
                request.getDeviceId().trim(),
                request.getStartTime().trim(),
                request.getEndTime().trim());
        if (CollectionUtils.isEmpty(dataList)) {
            throw new BizException("没有可导出的数据");
        }
        dataList.sort(Comparator.comparing(DeviceAttrInfo::getAcquireTimestampFormat, Comparator.nullsLast(String::compareTo)));

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(WORKBOOK_WINDOW_SIZE);
             FileOutputStream outputStream = new FileOutputStream(targetFile)) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("设备属性信息");
            sheet.setDefaultColumnWidth(DEFAULT_COLUMN_WIDTH);

            List<String> headers = buildHeaders(propertiesTypes, request.getPropertiesTypeNameMap());
            Row headerRow = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                headerRow.createCell(columnIndex).setCellValue(headers.get(columnIndex));
            }

            for (int rowIndex = 0; rowIndex < dataList.size(); rowIndex++) {
                DeviceAttrInfo item = dataList.get(rowIndex);
                JsonNode propertiesNode = parseProperties(item.getProperties(), propertiesTypes);
                Row row = sheet.createRow(rowIndex + 1);
                int columnIndex = 0;
                row.createCell(columnIndex++).setCellValue(toStringValue(item.getDeviceId()));
                row.createCell(columnIndex++).setCellValue(toStringValue(item.getAcquireTimestampFormat()));
                row.createCell(columnIndex++).setCellValue(toStringValue(item.getDeviceType()));
                row.createCell(columnIndex++).setCellValue(toStringValue(item.getParentId()));
                row.createCell(columnIndex++).setCellValue(toStringValue(item.getMethod()));
                row.createCell(columnIndex++).setCellValue(toStringValue(item.getProductKey()));
                row.createCell(columnIndex++).setCellValue(toStringValue(item.getProperties()));
                for (String key : propertiesTypes) {
                    row.createCell(columnIndex++).setCellValue(extractPropertiesValue(propertiesNode, key));
                }
            }

            workbook.write(outputStream);
            outputStream.flush();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("构建设备属性导出文件失败, request={}", request, ex);
            throw new BizException("导出设备属性信息异常", ex);
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{SECOND_FORMATTER, MICROSECOND_FORMATTER}) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ex) {
                // try next formatter
            }
        }
        return null;
    }

    private List<String> resolvePropertiesTypes(DeviceAttrInfoExportRequest request) {
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

    private List<String> buildHeaders(List<String> propertiesTypes, Map<String, String> propertiesTypeNameMap) {
        List<String> headers = new ArrayList<>();
        headers.add("设备ID");
        headers.add("采集时间");
        headers.add("设备类型");
        headers.add("父设备ID");
        headers.add("方法");
        headers.add("产品编码");
        headers.add("properties原文");
        for (String propertiesType : propertiesTypes) {
            String headerName = propertiesTypeNameMap == null ? null : propertiesTypeNameMap.get(propertiesType);
            headers.add(StringUtils.hasText(headerName) ? headerName : propertiesType);
        }
        return headers;
    }

    private JsonNode parseProperties(String properties, List<String> propertiesTypes) {
        if (!StringUtils.hasText(properties)) {
            return null;
        }
        try {
            return objectMapper.readTree(properties);
        } catch (Exception ex) {
            log.warn("解析properties失败, propertiesTypes={}, properties={}", propertiesTypes, properties);
            return null;
        }
    }

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

    private String toStringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
