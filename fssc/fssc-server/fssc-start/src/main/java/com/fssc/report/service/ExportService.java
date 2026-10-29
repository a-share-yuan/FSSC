// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.budget.entity.BgItem;
import com.fssc.budget.mapper.BgItemMapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.DataScopeHolder;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.report.mapper.ReportMapper;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 报表 Excel 导出（POI）。租户拦截器自动隔离；只读导出不写入数据库。
 */
@Service
@RequiredArgsConstructor
@FsscDataSource("replica")
public class ExportService {

    private final ExpBillMapper expBillMapper;
    private final BgItemMapper bgItemMapper;
    private final ReportMapper reportMapper;
    /** 组织合法性守卫（安全审计①）：导出规则公司隔离 */
    private final OrgGuard orgGuard;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 导出报销单据列表（① 导出规则 + ③ data_scope：仅导出登录人数据范围内的单据） */
    @DataScope("报销导出数据范围")
    public byte[] exportBills(String billType, String status) {
        LambdaQueryWrapper<ExpBill> qw = new LambdaQueryWrapper<>();
        if (billType != null && !billType.isBlank()) {
            qw.eq(ExpBill::getBillType, billType);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(ExpBill::getStatus, status);
        }
        DataScopeKit.apply(qw, ExpBill::getCompanyId, ExpBill::getDeptId, ExpBill::getApplyUserId);
        qw.orderByDesc(ExpBill::getId);
        List<ExpBill> bills = expBillMapper.selectList(qw);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("报销单据");
            String[] heads = {"单号", "类型", "标题", "申请人", "金额", "状态", "申请日期", "创建时间"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) {
                head.createCell(i).setCellValue(heads[i]);
            }
            int r = 1;
            for (ExpBill b : bills) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(str(b.getBillNo()));
                row.createCell(1).setCellValue(str(b.getBillType()));
                row.createCell(2).setCellValue(str(b.getTitle()));
                row.createCell(3).setCellValue(str(b.getApplyUserName()));
                row.createCell(4).setCellValue(nvl(b.getTotalAmount()).doubleValue());
                row.createCell(5).setCellValue(str(b.getStatus()));
                row.createCell(6).setCellValue(b.getApplyDate() == null ? "" : b.getApplyDate().toString());
                row.createCell(7).setCellValue(b.getCreateTime() == null ? "" : b.getCreateTime().format(FMT));
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("导出报销单据失败", e);
        }
    }

    /** 导出部门报销排行（① 导出规则：仅本登录人公司）
     * F-07：补 {@code @DataScope} 与部门/本人两参——原方法只做公司隔离，部门/本人两参恒 null，
     * scope=5 的账号导出件里出现本人部门以外的其他部门（屏幕 31 单 vs 导出 166 单）。
     * 范围规则与 DashboardService.scopeArgs／ReportQueryController.resolveScope 一致。 */
    @DataScope("部门排行导出数据范围")
    public byte[] exportDeptRanking() {
        Long mine = orgGuard.enforceCompany();
        ScopeArgs sc = reportScopeArgs();
        List<Map<String, Object>> rows = reportMapper.expenseByDept(mine, sc.deptIds(), sc.userId());
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("部门报销排行");
            String[] heads = {"部门", "单据数", "报销金额"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) {
                head.createCell(i).setCellValue(heads[i]);
            }
            int r = 1;
            for (Map<String, Object> m : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(str(m.get("dept_name")));
                row.createCell(1).setCellValue(toLong(m.get("cnt")));
                row.createCell(2).setCellValue(toDouble(m.get("amount")));
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("导出部门排行失败", e);
        }
    }

    /** 导出预算执行率（① 导出规则 + ③ data_scope：仅本登录人数据范围） */
    @DataScope("预算导出数据范围")
    public byte[] exportBudgetExecution() {
        LambdaQueryWrapper<BgItem> qw = new LambdaQueryWrapper<BgItem>()
                .select(BgItem::getDimKey,
                        BgItem::getTotalAmount,
                        BgItem::getAdjustAmount,
                        BgItem::getOccupyAmount,
                        BgItem::getUsedAmount);
        // F-08：改 applyWithNullDept——bg_item 公司级下达行 dept_id 为 NULL，原 apply 的
        // dept_id = 本人部门 会把它们整批滤掉（屏幕 6 行 / 导出 0 行）。
        DataScopeKit.applyWithNullDept(qw, BgItem::getCompanyId, BgItem::getDeptId, null);
        List<Map<String, Object>> rows = bgItemMapper.selectMaps(qw);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("预算执行率");
            String[] heads = {"预算项", "总额", "调整", "占用", "已用", "执行率%"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) {
                head.createCell(i).setCellValue(heads[i]);
            }
            int r = 1;
            for (Map<String, Object> m : rows) {
                double total = toDouble(m.get("total_amount")) + toDouble(m.get("adjust_amount"));
                double used = toDouble(m.get("used_amount"));
                double rate = total > 0 ? used / total * 100 : 0;
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(str(m.get("dim_key")));
                row.createCell(1).setCellValue(toDouble(m.get("total_amount")));
                row.createCell(2).setCellValue(toDouble(m.get("adjust_amount")));
                row.createCell(3).setCellValue(toDouble(m.get("occupy_amount")));
                row.createCell(4).setCellValue(used);
                row.createCell(5).setCellValue(Math.round(rate * 10) / 10.0);
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("导出预算执行率失败", e);
        }
    }

    /**
     * 部门/本人两维的数据范围入参（F-07），规则与 {@code DashboardService.scopeArgs}、
     * {@code ReportQueryController.resolveScope} 一致：1/2 档不收窄；3 档取本部门及以下；
     * 4 档只取本部门；5 档取本人（无本人维时由调用方降级公司）。
     */
    private ScopeArgs reportScopeArgs() {
        DataScopeHolder.Scope s = DataScopeHolder.get();
        if (s == null) {
            return new ScopeArgs(null, null);
        }
        if (s.getScope() <= com.fssc.common.enums.DataScope.COMPANY.getCode()) {
            return new ScopeArgs(null, null);
        }
        if (s.getScope() == com.fssc.common.enums.DataScope.SELF.getCode()) {
            return new ScopeArgs(null, s.getUserId());
        }
        if (s.getScope() == com.fssc.common.enums.DataScope.DEPT.getCode()) {
            return new ScopeArgs(s.getDeptId() == null ? null : List.of(s.getDeptId()), null);
        }
        return new ScopeArgs(s.getDeptAndChild(), null);
    }

    private record ScopeArgs(Collection<Long> deptIds, Long userId) {
    }

    private byte[] toBytes(XSSFWorkbook wb) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private long toLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v));
    }

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
