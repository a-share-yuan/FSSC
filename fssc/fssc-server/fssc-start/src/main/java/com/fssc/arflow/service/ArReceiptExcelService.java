package com.fssc.arflow.service;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 收款核销模板 Excel 解析（任务3.2：真实 POI 解析，蓝图附件三 11.4.1 列定义）。
 * 列：收款单号/收款日期/客户编码/客户名称/OU/币种/收款金额/发票号/核销金额/备注。
 * 国内收款（无发票号）按客户自动核销；国外收款必须导入发票号。
 */
@Slf4j
@Service
public class ArReceiptExcelService {

    private static final String[] HEADERS = {
            "收款单号", "收款日期", "客户编码", "客户名称", "OU", "币种", "收款金额", "发票号", "核销金额", "备注",
    };

    public record ParseResult(List<Map<String, Object>> rows, List<String> errors) {
    }

    public ParseResult parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请上传收款核销模板 Excel 文件");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (InputStream in = file.getInputStream(); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row header = null;
            int headerRowIdx = -1;
            for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 5); i++) {
                Row r = sheet.getRow(i);
                if (r != null && "收款单号".equals(text(fmt, r.getCell(0)))) {
                    header = r;
                    headerRowIdx = i;
                    break;
                }
            }
            if (header == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "未找到表头行（第一列须为「收款单号」），请使用标准模板");
            }
            Map<String, Integer> colIdx = new LinkedHashMap<>();
            for (int c = 0; c < header.getLastCellNum(); c++) {
                String name = text(fmt, header.getCell(c));
                if (name != null && !name.isBlank()) {
                    colIdx.put(name, c);
                }
            }
            for (String h : HEADERS) {
                if (!colIdx.containsKey(h)) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "模板缺少必需列：「" + h + "」，请使用标准模板");
                }
            }
            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row r = sheet.getRow(i);
                if (r == null || isBlank(r)) {
                    continue;
                }
                int excelRow = i + 1;
                String customerName = text(fmt, cell(r, colIdx, "客户名称"));
                BigDecimal amount = num(cell(r, colIdx, "收款金额"));
                if (customerName == null || amount == null || amount.signum() <= 0) {
                    errors.add("第" + excelRow + "行：客户名称与收款金额（>0）必填，该行已跳过");
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("receiptNo", text(fmt, cell(r, colIdx, "收款单号")));
                String dateStr = text(fmt, cell(r, colIdx, "收款日期"));
                row.put("receiptDate", dateStr == null ? LocalDate.now() : LocalDate.parse(dateStr.substring(0, 10)));
                row.put("customerCode", text(fmt, cell(r, colIdx, "客户编码")));
                row.put("customerName", customerName);
                row.put("ou", text(fmt, cell(r, colIdx, "OU")));
                row.put("ccy", text(fmt, cell(r, colIdx, "币种")));
                row.put("amount", amount);
                row.put("invoiceNo", text(fmt, cell(r, colIdx, "发票号")));
                BigDecimal offset = num(cell(r, colIdx, "核销金额"));
                row.put("offsetAmount", offset == null ? amount : offset);
                row.put("remark", text(fmt, cell(r, colIdx, "备注")));
                row.put("_excelRow", excelRow);
                String ccy = text(fmt, cell(r, colIdx, "币种"));
                boolean foreign = ccy != null && !ccy.isBlank() && !"CNY".equalsIgnoreCase(ccy);
                if (foreign && row.get("invoiceNo") == null) {
                    errors.add("第" + excelRow + "行：外币收款核销必须导入发票号，该行已跳过");
                    continue;
                }
                rows.add(row);
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "收款核销模板解析失败：" + e.getMessage());
        }
        if (rows.isEmpty() && !errors.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收款核销模板全部行校验失败：" + String.join("；", errors));
        }
        log.info("[arflow] 收款核销模板解析：{} 行有效，{} 行错误", rows.size(), errors.size());
        return new ParseResult(rows, errors);
    }

    private Cell cell(Row r, Map<String, Integer> colIdx, String name) {
        Integer idx = colIdx.get(name);
        return idx == null ? null : r.getCell(idx);
    }

    private boolean isBlank(Row r) {
        for (int c = r.getFirstCellNum(); c < r.getLastCellNum(); c++) {
            Cell cell = r.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && cell.toString() != null && !cell.toString().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String text(DataFormatter fmt, Cell cell) {
        if (cell == null) {
            return null;
        }
        String v = fmt.formatCellValue(cell).trim();
        return v.isEmpty() ? null : v;
    }

    private BigDecimal num(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        String s = String.valueOf(cell).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
