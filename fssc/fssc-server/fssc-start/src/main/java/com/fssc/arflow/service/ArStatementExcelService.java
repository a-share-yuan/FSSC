package com.fssc.arflow.service;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户对账单模板 Excel 解析（蓝图附件 11.4.4，12列，真实 POI）：
 * 客户代码/客户名称/物料名称/型号规格/数量/单价/金额/税额/价税合计/PO号或交货单号/是否提前开票/备注。
 * 按客户代码分组：每个客户一张对账单（明细落 lines_json，表头金额=行合计）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArStatementExcelService {

    private static final String[] HEADERS = {
            "客户代码", "客户名称", "物料名称", "型号规格", "数量", "单价", "金额", "税额", "价税合计", "PO号或交货单号", "是否提前开票", "备注",
    };

    public record ParseResult(List<Map<String, Object>> statements, List<String> errors) {
    }

    private final ArStatementService statementService;

    public ParseResult parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请上传客户对账单模板 Excel 文件");
        }
        List<String> errors = new ArrayList<>();
        try (InputStream in = file.getInputStream(); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row header = null;
            int headerRowIdx = -1;
            for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 5); i++) {
                Row r = sheet.getRow(i);
                if (r != null && "客户代码".equals(text(fmt, r.getCell(0)))) {
                    header = r;
                    headerRowIdx = i;
                    break;
                }
            }
            if (header == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "未找到表头行（第一列须为「客户代码」），请使用标准模板");
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
            // 按客户代码分组
            Map<String, Map<String, Object>> byCustomer = new LinkedHashMap<>();
            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row r = sheet.getRow(i);
                if (r == null || isBlank(r, fmt)) {
                    continue;
                }
                int excelRow = i + 1;
                String customerCode = text(fmt, cell(r, colIdx, "客户代码"));
                if (customerCode == null || customerCode.isBlank()) {
                    errors.add("第" + excelRow + "行客户代码为空，跳过");
                    continue;
                }
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("materialName", text(fmt, cell(r, colIdx, "物料名称")));
                line.put("spec", text(fmt, cell(r, colIdx, "型号规格")));
                line.put("qty", dec(fmt, cell(r, colIdx, "数量")));
                line.put("price", dec(fmt, cell(r, colIdx, "单价")));
                line.put("amount", dec(fmt, cell(r, colIdx, "金额")));
                line.put("taxAmount", dec(fmt, cell(r, colIdx, "税额")));
                line.put("totalAmount", dec(fmt, cell(r, colIdx, "价税合计")));
                line.put("poNo", text(fmt, cell(r, colIdx, "PO号或交货单号")));
                line.put("preInvoiceFlag", "是".equalsIgnoreCase(text(fmt, cell(r, colIdx, "是否提前开票"))) ? 1 : 0);
                line.put("remark", text(fmt, cell(r, colIdx, "备注")));
                line.put("_excelRow", excelRow);
                Map<String, Object> stmt = byCustomer.computeIfAbsent(customerCode, k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("customerCode", k);
                    m.put("customerName", text(fmt, cell(r, colIdx, "客户名称")));
                    m.put("lines", new ArrayList<Map<String, Object>>());
                    return m;
                });
                ((List<Map<String, Object>>) stmt.get("lines")).add(line);
            }
            // 组装对账单（金额=行合计）
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> stmt : byCustomer.values()) {
                List<Map<String, Object>> lines = (List<Map<String, Object>>) stmt.get("lines");
                BigDecimal amount = BigDecimal.ZERO;
                BigDecimal tax = BigDecimal.ZERO;
                for (Map<String, Object> l : lines) {
                    amount = amount.add((BigDecimal) l.getOrDefault("amount", BigDecimal.ZERO));
                    tax = tax.add((BigDecimal) l.getOrDefault("taxAmount", BigDecimal.ZERO));
                }
                stmt.put("amount", amount);
                stmt.put("taxAmount", tax);
                stmt.put("billType", "DOMESTIC");
                boolean preFlag = lines.stream().anyMatch(l -> Integer.valueOf(1).equals(l.get("preInvoiceFlag")));
                stmt.put("preInvoiceFlag", preFlag ? 1 : 0);
                out.add(stmt);
            }
            return new ParseResult(out, errors);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单模板解析失败：" + e.getMessage());
        }
    }

    /** 导入并落库（每客户一张对账单，走既有容差校验逻辑）。返回 {created, errors, billNos}。 */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importAndSave(MultipartFile file, Long companyId) {
        ParseResult parsed = parse(file);
        int created = 0;
        List<String> billNos = new ArrayList<>();
        for (Map<String, Object> stmt : parsed.statements()) {
            try {
                com.fssc.arflow.entity.ArStatement bill = new com.fssc.arflow.entity.ArStatement();
                bill.setCustomerCode(String.valueOf(stmt.get("customerCode")));
                Object name = stmt.get("customerName");
                bill.setCustomerName(name == null ? null : String.valueOf(name));
                bill.setAmount((BigDecimal) stmt.get("amount"));
                bill.setTaxAmount((BigDecimal) stmt.get("taxAmount"));
                bill.setBillType("DOMESTIC");
                bill.setCcy("CNY");
                if (Integer.valueOf(1).equals(stmt.get("preInvoiceFlag"))) {
                    bill.setPreInvoiceFlag(1);
                }
                if (companyId != null) {
                    bill.setCompanyId(companyId);
                }
                com.fssc.arflow.entity.ArStatement saved = statementService.importBill(bill);
                // 明细回写
                try {
                    com.fssc.arflow.entity.ArStatement u = new com.fssc.arflow.entity.ArStatement();
                    u.setId(saved.getId());
                    u.setLinesJson(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                            .writeValueAsString(stmt.get("lines")));
                    statementService.updateLinesJson(u);
                } catch (Exception ignore) {
                    // 明细回写失败不影响对账单主档
                }
                billNos.add(saved.getBillNo());
                created++;
            } catch (Exception e) {
                // 单个客户对账单导入失败记入 errors，不中断整批导入
                parsed.errors().add("客户 " + stmt.get("customerCode") + " 导入失败：" + e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", created);
        out.put("errors", parsed.errors());
        out.put("billNos", billNos);
        return out;
    }

    private Cell cell(Row r, Map<String, Integer> colIdx, String name) {
        Integer idx = colIdx.get(name);
        return idx == null ? null : r.getCell(idx);
    }

    private String text(DataFormatter fmt, Cell c) {
        if (c == null) {
            return null;
        }
        String v = fmt.formatCellValue(c).trim();
        return v.isEmpty() ? null : v;
    }

    private BigDecimal dec(DataFormatter fmt, Cell c) {
        String v = text(fmt, c);
        if (v == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(v.replace(",", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private boolean isBlank(Row r, DataFormatter fmt) {
        for (int c = r.getFirstCellNum(); c < r.getLastCellNum(); c++) {
            String v = text(fmt, r.getCell(c));
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
