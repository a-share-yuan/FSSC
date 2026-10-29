// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.apflow.service;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import java.math.RoundingMode;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 付款计划表 Excel 解析（任务3.2：真实 POI 解析，蓝图附录四 A.4.1 列定义）。
 * 列：发票编号/事业部/供应商名称/付款方法/银行账户/付款日期/折扣额/汇票到期日期/付款额/PO编号/备注。
 */
@Slf4j
@Service
public class ApPlanExcelService {

    private static final String[] HEADERS = {
            "发票编号", "事业部", "供应商名称", "付款方法", "银行账户",
            "付款日期", "折扣额", "汇票到期日期", "付款额", "PO编号", "备注",
    };

    public record ParseResult(List<Map<String, Object>> rows, List<String> errors) {
    }

    public ParseResult parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请上传付款计划表 Excel 文件");
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
                if (r != null && "发票编号".equals(text(fmt, r.getCell(0)))) {
                    header = r;
                    headerRowIdx = i;
                    break;
                }
            }
            if (header == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "未找到表头行（第一列须为「发票编号」），请使用标准模板");
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
                String invoiceNo = text(fmt, cell(r, colIdx, "发票编号"));
                String vendorName = text(fmt, cell(r, colIdx, "供应商名称"));
                String paymentWay = text(fmt, cell(r, colIdx, "付款方法"));
                if (invoiceNo == null || vendorName == null || paymentWay == null) {
                    errors.add("第" + excelRow + "行：发票编号/供应商名称/付款方法为必填，该行已跳过");
                    continue;
                }
                if (!"支票".equals(paymentWay) && !"银行转账".equals(paymentWay) && !"应付票据".equals(paymentWay)) {
                    errors.add("第" + excelRow + "行：付款方法「" + paymentWay + "」非法，该行已跳过");
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("invoiceNo", invoiceNo);
                row.put("sbu", text(fmt, cell(r, colIdx, "事业部")));
                row.put("vendorName", vendorName);
                row.put("paymentWay", paymentWay);
                row.put("bankNo", text(fmt, cell(r, colIdx, "银行账户")));
                row.put("paymentDate", date(text(fmt, cell(r, colIdx, "付款日期"))));
                row.put("rebateAmount", num(cell(r, colIdx, "折扣额")));
                row.put("expireDate", date(text(fmt, cell(r, colIdx, "汇票到期日期"))));
                row.put("paymentAmount", num(cell(r, colIdx, "付款额")));
                row.put("poNo", text(fmt, cell(r, colIdx, "PO编号")));
                row.put("remark", text(fmt, cell(r, colIdx, "备注")));
                row.put("_excelRow", excelRow);
                if ("应付票据".equals(paymentWay) && row.get("expireDate") == null) {
                    errors.add("第" + excelRow + "行：应付票据必须录入汇票到期日期，该行已跳过");
                    continue;
                }
                rows.add(row);
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "付款计划表解析失败：" + e.getMessage());
        }
        if (rows.isEmpty() && !errors.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款计划表全部行校验失败：" + String.join("；", errors));
        }
        log.info("[apflow] 付款计划表解析：{} 行有效，{} 行错误", rows.size(), errors.size());
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
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
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

    private LocalDate date(String s) {
        if (s == null || s.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }
}
