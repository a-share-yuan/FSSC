// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.receivable.entity.RcBadDebt;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcBadDebtMapper;
import com.fssc.receivable.mapper.RcReceivableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 坏账核销模板 Excel 解析（蓝图附件 11.4.2，9列，真实 POI）：
 * 客户编码/客户名称/发票号或应收单号/应收余额/已计提坏账准备/本次核销金额/币种/核销原因/坏账认定依据编号。
 * 逐行定位应收单（billNo 或 AR-INV-{发票号}）→ 定位该应收的 PROVISIONED 坏账记录 →
 * 校验 核销金额≤已计提坏账准备 → 走 writeOff 生成借1231/贷1122 凭证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RcBadDebtExcelService {

    private static final String[] HEADERS = {
            "客户编码", "客户名称", "发票号或应收单号", "应收余额", "已计提坏账准备", "本次核销金额", "币种", "核销原因", "坏账认定依据编号",
    };

    public record ParseResult(List<Map<String, Object>> rows, List<String> errors) {
    }

    private final RcBadDebtMapper badDebtMapper;
    private final RcReceivableMapper receivableMapper;
    /** Excel 通道公司维度守卫（模板导入与列表/回显同一规则，不绕过数据范围） */
    private final OrgGuard orgGuard;

    public ParseResult parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请上传坏账核销模板 Excel 文件");
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
                if (r != null && "客户编码".equals(text(fmt, r.getCell(0)))) {
                    header = r;
                    headerRowIdx = i;
                    break;
                }
            }
            if (header == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "未找到表头行（第一列须为「客户编码」），请使用标准模板");
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
                if (r == null || isBlank(r, fmt)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("customerCode", text(fmt, cell(r, colIdx, "客户编码")));
                row.put("customerName", text(fmt, cell(r, colIdx, "客户名称")));
                row.put("invoiceNo", text(fmt, cell(r, colIdx, "发票号或应收单号")));
                row.put("receivableRemain", dec(fmt, cell(r, colIdx, "应收余额")));
                row.put("provisioned", dec(fmt, cell(r, colIdx, "已计提坏账准备")));
                row.put("writeoffAmount", dec(fmt, cell(r, colIdx, "本次核销金额")));
                row.put("ccy", text(fmt, cell(r, colIdx, "币种")));
                row.put("reason", text(fmt, cell(r, colIdx, "核销原因")));
                row.put("basisNo", text(fmt, cell(r, colIdx, "坏账认定依据编号")));
                row.put("_excelRow", i + 1);
                rows.add(row);
            }
            return new ParseResult(rows, errors);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "坏账核销模板解析失败：" + e.getMessage());
        }
    }

    /** 逐行执行核销（OA 仅推申请人，明细由模板导入——蓝图流程22）。返回 {writtenOff, errors}。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importAndWriteOff(MultipartFile file, RcBadDebtService writeOffService) {
        ParseResult parsed = parse(file);
        int writtenOff = 0;
        for (Map<String, Object> row : parsed.rows()) {
            int excelRow = (Integer) row.get("_excelRow");
            try {
                String invoiceNo = String.valueOf(row.get("invoiceNo"));
                String billNo = invoiceNo.startsWith("AR-INV-") ? invoiceNo : "AR-INV-" + invoiceNo;
                RcReceivable rec = receivableMapper.selectOne(
                        new LambdaQueryWrapper<RcReceivable>()
                                .and(w -> w.eq(RcReceivable::getBillNo, billNo)
                                        .or().eq(RcReceivable::getBillNo, invoiceNo))
                                .last("LIMIT 1"));
                if (rec == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "发票号/应收单号 " + invoiceNo + " 未找到应收单");
                }
                // Excel 通道同步办结——模板行指向他公司应收时按可见性守卫行级拒绝，
                // 坏账列表/导入/回显规则一致（集团账号与 data_scope=1 仍按 OrgGuard 既有豁免）
                orgGuard.assertVisible(rec.getCompanyId(), null);
                RcBadDebt debt = badDebtMapper.selectOne(
                        new LambdaQueryWrapper<RcBadDebt>()
                                .eq(RcBadDebt::getReceivableId, rec.getId())
                                .eq(RcBadDebt::getStatus, "PROVISIONED")
                                .orderByDesc(RcBadDebt::getId)
                                .last("LIMIT 1"));
                if (debt == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "应收单 " + rec.getBillNo() + " 无已计提(PROVISIONED)坏账记录，不可核销（流程22前提）");
                }
                writeOffService.writeOff(debt.getId(),
                        (BigDecimal) row.get("writeoffAmount"),
                        (String) row.get("reason"),
                        (String) row.get("basisNo"));
                writtenOff++;
            } catch (Exception e) {
                // 单行核销失败记入 errors 随结果返回，不中断整批导入
                parsed.errors().add("第" + excelRow + "行核销失败：" + e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("writtenOff", writtenOff);
        out.put("errors", parsed.errors());
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
