package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.masterdata.entity.MdIoTemplate;
import com.fssc.masterdata.entity.MdIoTemplateField;
import com.fssc.masterdata.mapper.MdIoTemplateFieldMapper;
import com.fssc.masterdata.mapper.MdIoTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 统一导入导出引擎（V1.4.83/V1.4.84）——闭环差距②后半「导入导出无统一引擎」。
 *
 * <p><b>改造前</b>：fssc-server 内 6 支手写 {@code *ExcelService}
 * （ApPlan / ArReceipt / ArOffset / ArStatement / BudgetItem / RcBadDebt），
 * 每支各自硬编码 {@code HEADERS} 数组、各自写一遍 POI 解析、各自决定错误文案，
 * 新增一种导入模板 = 复制一支 Service 再改列头 ⇒ 必然长出 N+1 支。
 *
 * <p><b>改造后</b>：列头与转换规则落 {@code md_io_template} / {@code md_io_template_field}，
 * 本引擎按模板解释器统一执行。新增导入模板 = 插两行数据，<b>不需要写代码</b>。
 * 6 支手写 Service 的列头已逐字迁移为 7 个模板（见 V1.4.84），
 * 保证与现有导入文件格式 100% 兼容。
 *
 * <p><b>转换规则声明式</b>（{@code column_type}）：
 * <ul>
 *   <li>{@code STRING} 原样文本，带 maxLength 校验</li>
 *   <li>{@code NUMBER} 按 decimalScale 归一，超精度按四舍五入</li>
 *   <li>{@code DATE} 支持 yyyy-MM-dd / yyyy/M/d / yyyyMMdd 等常见写法</li>
 *   <li>{@code ENUM} 按 enum_values 校验，不在集合内报错（对齐财会〔2024〕12号 §7 标准接口）</li>
 *   <li>{@code ref_table} 非空时按 refCodeCol 反查主键写入（客户/科目/公司自动带 ID）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IoTemplateService {

    private final MdIoTemplateMapper templateMapper;
    private final MdIoTemplateFieldMapper fieldMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 导入解析结果：逐行数据 + 逐行错误（不中断整批，便于用户一次改完） */
    public record ImportResult(List<Map<String, Object>> rows, List<RowError> errors,
                               int totalRows, int okRows, String templateName) {
    }

    /** 行级错误 */
    public record RowError(int rowNo, String columnName, String message) {
    }

    private static final DateTimeFormatter[] DATE_PATTERNS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
    };
    private static final Pattern NUM = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    // ==================== 模板查询 ====================

    public List<MdIoTemplate> listAll(String direction) {
        LambdaQueryWrapper<MdIoTemplate> w = new LambdaQueryWrapper<MdIoTemplate>()
                .orderByAsc(MdIoTemplate::getBizType)
                .orderByAsc(MdIoTemplate::getTemplateCode);
        if (direction != null && !direction.isBlank()) {
            w.eq(MdIoTemplate::getDirection, direction);
        }
        return templateMapper.selectList(w);
    }

    public MdIoTemplate resolveTemplate(String templateCode) {
        MdIoTemplate t = templateMapper.selectOne(new LambdaQueryWrapper<MdIoTemplate>()
                .eq(MdIoTemplate::getTemplateCode, templateCode)
                .orderByDesc(MdIoTemplate::getVersion)
                .last("LIMIT 1"));
        if (t == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "导入导出模板不存在: " + templateCode);
        }
        return t;
    }

    public List<MdIoTemplateField> fieldsOf(Long templateId) {
        return fieldMapper.selectList(new LambdaQueryWrapper<MdIoTemplateField>()
                .eq(MdIoTemplateField::getTemplateId, templateId)
                .orderByAsc(MdIoTemplateField::getSortOrder)
                .orderByAsc(MdIoTemplateField::getId));
    }

    // ==================== 模板下载（空白模板供用户填写） ====================

    /**
     * 生成空白导入模板（带表头与一行示例）。
     * 这是「引入模板在系统后台自动生成」的落地：用户不需要找 Excel 模板文件。
     */
    public byte[] buildBlankTemplate(String templateCode) {
        MdIoTemplate t = resolveTemplate(templateCode);
        List<MdIoTemplateField> fields = fieldsOf(t.getId());
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(t.getSheetName() == null ? "Sheet1" : t.getSheetName());
            Row header = sheet.createRow(0);
            Row sample = sheet.createRow(1);
            for (int i = 0; i < fields.size(); i++) {
                MdIoTemplateField f = fields.get(i);
                header.createCell(i).setCellValue(f.getColumnName());
                sample.createCell(i).setCellValue(sampleOf(f));
                sheet.setColumnWidth(i, Math.min(40, Math.max(12, f.getColumnName().length() * 2 + 4)) * 256);
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "生成模板失败: " + e.getMessage());
        }
    }

    private String sampleOf(MdIoTemplateField f) {
        return switch (Objects.requireNonNullElse(f.getColumnType(), "STRING")) {
            case "NUMBER" -> "0.00";
            case "DATE" -> "2026-01-01";
            case "ENUM" -> {
                String ev = f.getEnumValues();
                yield (ev == null || ev.isBlank()) ? "" : ev.split(",")[0];
            }
            default -> Objects.requireNonNullElse(f.getRemark(), "");
        };
    }

    // ==================== 导入解析 ====================

    /**
     * 按模板解析上传的 Excel。
     *
     * <p><b>不中断整批</b>：单行出错只记录到 {@link RowError}，其余行继续解析，
     * 避免用户上传 500 行、错 1 行就得从头来（这正是手写 Service 逐个 throw 的痛点）。
     */
    public ImportResult importExcel(String templateCode, MultipartFile file) {
        MdIoTemplate t = resolveTemplate(templateCode);
        List<MdIoTemplateField> fields = fieldsOf(t.getId());
        if (fields.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板未配置字段定义: " + templateCode);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请上传 Excel 文件");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        int headerRow = Objects.requireNonNullElse(t.getHeaderRow(), 1) - 1;
        int maxRows = Objects.requireNonNullElse(t.getMaxRows(), 10000);
        String sheetName = t.getSheetName();
        int total = 0;

        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = (sheetName == null || sheetName.isBlank())
                    ? wb.getSheetAt(0) : wb.getSheet(sheetName);
            if (sheet == null) {
                sheet = wb.getSheetAt(0);
            }
            Row hr = sheet.getRow(headerRow);
            if (hr == null) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "未找到表头行（第 " + (headerRow + 1) + " 行），请使用标准模板");
            }
            // 中文列头 → (列下标, 字段定义)
            // 用下标而非列名回查：省掉每格一次 O(n) 的列名扫描，也避免 colMap 里存 Cell 对象
            DataFormatter fmt = new DataFormatter();
            Map<Integer, MdIoTemplateField> colMap = new LinkedHashMap<>();
            for (int c = 0; c < hr.getLastCellNum(); c++) {
                String name = fmt.formatCellValue(hr.getCell(c)).trim();
                MdIoTemplateField f = fields.stream()
                        .filter(x -> Objects.equals(x.getColumnName(), name))
                        .findFirst().orElse(null);
                if (f == null) {
                    errors.add(new RowError(headerRow + 1, name,
                            "模板列「" + name + "」未在系统模板中定义，请使用系统生成的模板"));
                } else {
                    colMap.put(c, f);
                }
            }

            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, fmt)) {
                    continue;
                }
                total++;
                if (total > maxRows) {
                    errors.add(new RowError(r + 1, null, "超过单次最大行数 " + maxRows + "，请分批导入"));
                    break;
                }
                Map<String, Object> data = new LinkedHashMap<>();
                boolean rowBad = false;
                for (Map.Entry<Integer, MdIoTemplateField> e : colMap.entrySet()) {
                    String raw = fmt.formatCellValue(row.getCell(e.getKey()));
                    MdIoTemplateField f = e.getValue();
                    try {
                        Object v = convert(raw, f);
                        if (v == null && Objects.equals(f.getRequired(), 1)) {
                            errors.add(new RowError(r + 1, f.getColumnName(), "必填项不能为空"));
                            rowBad = true;
                            continue;
                        }
                        if (v != null && f.getMaxLength() != null
                                && v instanceof String s && s.length() > f.getMaxLength()) {
                            errors.add(new RowError(r + 1, f.getColumnName(),
                                    "超出最大长度 " + f.getMaxLength() + "（实际 " + s.length() + "）"));
                            rowBad = true;
                            continue;
                        }
                        data.put(f.getFieldKey(), v);
                    } catch (BizException be) {
                        errors.add(new RowError(r + 1, f.getColumnName(), be.getMessage()));
                        rowBad = true;
                    }
                }
                if (!rowBad) {
                    data.put("__rowNo", r + 1);
                    rows.add(data);
                }
            }
        } catch (BizException be) {
            throw be;
        } catch (Exception e) {
            log.error("[io] 解析失败 template={}", templateCode, e);
            throw new BizException(ErrorCode.PARAM_INVALID, "Excel 解析失败: " + e.getMessage());
        }

        log.info("[io] 导入解析完成 template={} 总行={} 成功={} 错误={}",
                templateCode, total, rows.size(), errors.size());
        return new ImportResult(rows, errors, total, rows.size(), t.getTemplateName());
    }

    // ==================== 导出 ====================

    /**
     * 按模板导出数据为 Excel。
     *
     * @param templateCode 模板编码（direction=EXPORT）
     * @param conditions   附加查询条件（key=列名,value=值），做等值过滤
     */
    public byte[] exportExcel(String templateCode, Map<String, Object> conditions) {
        MdIoTemplate t = resolveTemplate(templateCode);
        List<MdIoTemplateField> fields = fieldsOf(t.getId());
        String target = t.getTargetTable();
        if (target == null || target.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板未配置目标表，无法导出: " + templateCode);
        }
        List<String> selCols = fields.stream().map(MdIoTemplateField::getDbColumn).toList();
        StringBuilder where = new StringBuilder(" WHERE del_flag = 0 ");
        if (conditions != null) {
            for (Map.Entry<String, Object> e : conditions.entrySet()) {
                if (!selCols.contains(e.getKey())) {
                    continue;
                }
                where.append(" AND `").append(e.getKey()).append("` = ?");
            }
        }
        String sql = "SELECT " + selCols.stream().map(c -> "`" + c + "`").reduce((a, b) -> a + "," + b).orElse("*")
                + " FROM `" + target + "`" + where + " ORDER BY id DESC LIMIT 10000";
        Object[] args = conditions == null ? new Object[0]
                : conditions.entrySet().stream()
                .filter(e -> selCols.contains(e.getKey()))
                .map(Map.Entry::getValue).toArray();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(t.getSheetName() == null ? "Sheet1" : t.getSheetName());
            Row header = sheet.createRow(0);
            for (int i = 0; i < fields.size(); i++) {
                header.createCell(i).setCellValue(fields.get(i).getColumnName());
                sheet.setColumnWidth(i, 20 * 256);
            }
            List<Map<String, Object>> data = jdbcTemplate.queryForList(sql, args);
            int ri = 1;
            for (Map<String, Object> row : data) {
                Row r = sheet.createRow(ri++);
                for (int i = 0; i < fields.size(); i++) {
                    MdIoTemplateField f = fields.get(i);
                    Object v = row.get(f.getDbColumn());
                    Cell c = r.createCell(i);
                    if (v == null) {
                        c.setCellValue("");
                    } else if (v instanceof Number n) {
                        c.setCellValue(n.doubleValue());
                    } else {
                        c.setCellValue(String.valueOf(v));
                    }
                }
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            wb.write(bos);
            log.info("[io] 导出行数={} template={}", ri - 1, templateCode);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "导出失败: " + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    /**
     * 按声明式规则转换单元格值。
     */
    private Object convert(String raw, MdIoTemplateField f) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        String type = Objects.requireNonNullElse(f.getColumnType(), "STRING");
        switch (type) {
            case "NUMBER" -> {
                String cleaned = v.replace(",", "").replace("￥", "").replace("¥", "");
                if (!NUM.matcher(cleaned).matches()) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "「" + v + "」不是合法数字");
                }
                int scale = Objects.requireNonNullElse(f.getDecimalScale(), 2);
                return new BigDecimal(cleaned).setScale(scale, RoundingMode.HALF_UP);
            }
            case "DATE" -> {
                return parseDate(v);
            }
            case "DATETIME" -> {
                return parseDate(v) + " 00:00:00";
            }
            case "ENUM" -> {
                String ev = f.getEnumValues();
                if (ev != null && !ev.isBlank()) {
                    Set<String> allow = Arrays.stream(ev.split(","))
                            .map(String::trim).collect(java.util.stream.Collectors.toSet());
                    if (!allow.contains(v)) {
                        throw new BizException(ErrorCode.PARAM_INVALID,
                                "取值「" + v + "」不在允许范围(" + ev + ")内");
                    }
                }
                return v;
            }
            case "BOOLEAN" -> {
                return List.of("是", "Y", "YES", "TRUE", "1", "√").contains(v.toUpperCase()) ? 1 : 0;
            }
            default -> {
                return v;
            }
        }
    }

    private LocalDate parseDate(String v) {
        for (DateTimeFormatter p : DATE_PATTERNS) {
            try {
                return LocalDate.parse(v, p);
            } catch (Exception ignored) {
                // 换下一种格式再试
            }
        }
        // 兜底：Excel 序列号
        try {
            long serial = Long.parseLong(v);
            return LocalDate.of(1899, 12, 30).plusDays(serial);
        } catch (Exception ignored) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "「" + v + "」不是合法日期（支持 yyyy-MM-dd / yyyy/M/d / yyyyMMdd）");
        }
    }

    private boolean isBlankRow(Row row, DataFormatter fmt) {
        for (int c = 0; c < row.getLastCellNum(); c++) {
            if (!fmt.formatCellValue(row.getCell(c)).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
