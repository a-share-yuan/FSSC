package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArClaim;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.mapper.MdSubjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
 * 其它业务对冲借贷 Excel 模板（蓝图附件 11.4.3，7列，行业方案"合法性校验报告"范式）：
 * 序号/借方科目/借方金额/贷方科目/贷方金额/摘要/往来对象编码。
 * 引入前强校验（全绿才入库）：①行级借贷平衡；②科目在 md_subject 存在；
 * ③科目段值 A0.00.XXXXXX.00.00.XX.0 第6段(下标5)≠'00' 视为往来科目 → 往来对象编码必填；④金额>0。
 * 校验通过生成 OTHER_OFFSET 服务申请单（分录落 lines_json），审单过账按分录生成多行凭证。
 */
@Service
@RequiredArgsConstructor
public class ArOffsetExcelService {

    private static final String[] HEADERS = {
            "序号", "借方科目", "借方金额", "贷方科目", "贷方金额", "摘要", "往来对象编码",
    };

    private final MdSubjectMapper mdSubjectMapper;
    private final ArClaimService claimService;

    @lombok.Data
    public static class OffsetRow {
        private Integer lineNo;
        private String debitCode;
        private BigDecimal debitAmount;
        private String creditCode;
        private BigDecimal creditAmount;
        private String summary;
        private String partnerCode;
    }

    public record ParseResult(List<OffsetRow> rows, List<String> report) {
    }

    public ParseResult parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请上传其它业务对冲借贷模板 Excel 文件");
        }
        List<OffsetRow> rows = new ArrayList<>();
        List<String> report = new ArrayList<>();
        try (InputStream in = file.getInputStream(); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row header = null;
            int headerRowIdx = -1;
            for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 5); i++) {
                Row r = sheet.getRow(i);
                if (r != null && "借方科目".equals(text(fmt, r.getCell(1)))) {
                    header = r;
                    headerRowIdx = i;
                    break;
                }
            }
            if (header == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "未找到表头行（第二列须为「借方科目」），请使用标准模板");
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
            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row r = sheet.getRow(i);
                if (r == null || isBlank(r, fmt)) {
                    continue;
                }
                int excelRow = i + 1;
                OffsetRow row = new OffsetRow();
                row.setLineNo((int) num(fmt, cell(r, colIdx, "序号")));
                row.setDebitCode(text(fmt, cell(r, colIdx, "借方科目")));
                row.setDebitAmount(dec(fmt, cell(r, colIdx, "借方金额")));
                row.setCreditCode(text(fmt, cell(r, colIdx, "贷方科目")));
                row.setCreditAmount(dec(fmt, cell(r, colIdx, "贷方金额")));
                row.setSummary(text(fmt, cell(r, colIdx, "摘要")));
                row.setPartnerCode(text(fmt, cell(r, colIdx, "往来对象编码")));
                // ① 行级借贷平衡
                if (row.getDebitAmount().compareTo(row.getCreditAmount()) != 0) {
                    report.add("第" + excelRow + "行借贷不平（借 " + row.getDebitAmount() + " ≠ 贷 " + row.getCreditAmount() + "）");
                }
                // ② 科目存在性
                checkSubject(row.getDebitCode(), "借方", excelRow, report, row);
                checkSubject(row.getCreditCode(), "贷方", excelRow, report, row);
                // ④ 金额>0
                if (row.getDebitAmount().signum() <= 0) {
                    report.add("第" + excelRow + "行借方金额必须大于0");
                }
                totalDebit = totalDebit.add(row.getDebitAmount());
                totalCredit = totalCredit.add(row.getCreditAmount());
                rows.add(row);
            }
            if (rows.isEmpty()) {
                report.add("模板无有效分录行");
            }
            // 合计平衡（行级平衡时天然成立，双保险）
            if (totalDebit.compareTo(totalCredit) != 0) {
                report.add("分录合计借贷不平（借合计 " + totalDebit + " ≠ 贷合计 " + totalCredit + "）");
            }
            return new ParseResult(rows, report);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对冲借贷模板解析失败：" + e.getMessage());
        }
    }

    /** 引入并生成 OTHER_OFFSET 服务申请单（校验报告非空则整单拒绝——先校验后入库）。 */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importAndCreate(MultipartFile file, String reason) {
        ParseResult parsed = parse(file);
        if (!parsed.report().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "引入校验未通过：" + String.join("；", parsed.report()));
        }
        BigDecimal totalDebit = parsed.rows().stream()
                .map(ArOffsetExcelService.OffsetRow::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 分录落 lines_json
        String linesJson;
        try {
            List<Map<String, Object>> lines = new ArrayList<>();
            for (OffsetRow r : parsed.rows()) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("debitCode", r.getDebitCode());
                l.put("debitAmount", r.getDebitAmount());
                l.put("creditCode", r.getCreditCode());
                l.put("creditAmount", r.getCreditAmount());
                l.put("summary", r.getSummary());
                l.put("partnerCode", r.getPartnerCode());
                lines.add(l);
            }
            linesJson = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(lines);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "分录序列化失败：" + e.getMessage());
        }
        com.fssc.arflow.entity.ArClaim c = new com.fssc.arflow.entity.ArClaim();
        c.setClaimType(com.fssc.arflow.entity.ArClaim.T_OTHER_OFFSET);
        c.setCompanyId(1L);
        c.setAmount(totalDebit);
        c.setDebitAmount(totalDebit);
        c.setCreditAmount(totalDebit);
        c.setDebitAccount(parsed.rows().get(0).getDebitCode());
        c.setCreditAccount(parsed.rows().get(0).getCreditCode());
        c.setReason("对冲借贷Excel引入：" + (reason == null ? "" : reason));
        c.setLinesJson(linesJson);
        ArClaim created = claimService.create(c);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("claimId", created.getId());
        out.put("billNo", created.getBillNo());
        out.put("lines", parsed.rows().size());
        out.put("totalDebit", totalDebit);
        return out;
    }

    /** ③ 往来段校验：科目段值第6段(下标5)≠'00' 视为往来科目 → 往来对象编码必填。 */
    private void checkSubject(String code, String side, int excelRow, List<String> report, OffsetRow row) {
        if (code == null || code.isBlank()) {
            report.add("第" + excelRow + "行" + side + "科目为空");
            return;
        }
        MdSubject s = mdSubjectMapper.selectOne(new LambdaQueryWrapper<MdSubject>()
                .eq(MdSubject::getSubjectCode, code).last("LIMIT 1"));
        if (s == null) {
            report.add("第" + excelRow + "行" + side + "科目 " + code + " 不存在于科目表");
            return;
        }
        String[] segs = code.split("\\.");
        if (segs.length >= 6 && !"00".equals(segs[5]) && (row.getPartnerCode() == null || row.getPartnerCode().isBlank())) {
            report.add("第" + excelRow + "行" + side + "科目 " + code + " 含往来段，往来对象编码必填");
        }
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

    private double num(DataFormatter fmt, Cell c) {
        return dec(fmt, c).doubleValue();
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
