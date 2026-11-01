// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final ReportMapper reportMapper;
    /**
     * 预算执行导出的取数口＝页面「预算执行」表同一个方法。
     * 起因（10-31 真跑）：本类原先自行按 bg_item 逐行取 {@code dim_key}，导出的首列是内部拼装键
     * {@code 2026-COMPANY1-DEPTnull-ITEM333}，而页面显示的是费用项名称 ⇒ 同名异值即不同源。
     */
    private final DashboardService dashboardService;
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
     * 补 {@code @DataScope} 与部门/本人两参——只做公司隔离时两参恒 null，
     * scope=5 的账号导出件里会出现本人部门以外的其他部门。
     * 范围规则与 DashboardService.scopeArgs／ReportQueryController.resolveScope 一致。 */
    @DataScope("部门排行导出数据范围")
    public byte[] exportDeptRanking() {
        Long mine = orgGuard.enforceCompany();
        ScopeArgs sc = reportScopeArgs();
        List<Map<String, Object>> rows = reportMapper.expenseByDept(mine, sc.deptIds(), sc.userId());
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("部门报销排行");
            String[] heads = {"部门", "单量", "金额"};   // 与页面「部门报销排名」表头逐字一致（同名异称＝不同源）
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

    /** 导出预算执行（列名/列序/取值与页面「预算执行」表同源：同一取数口＋同一聚合粒度＝按费用项） */
    @DataScope("预算导出数据范围")
    public byte[] exportBudgetExecution() {
        List<Map<String, Object>> rows = dashboardService.budgetExecution();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("预算执行");
            String[] heads = {"预算维度", "总额", "已用", "占用", "执行率(%)"};   // 逐字对齐页面表头
            Row head = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) {
                head.createCell(i).setCellValue(heads[i]);
            }
            int r = 1;
            for (Map<String, Object> m : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(str(m.get("dimKey")));
                row.createCell(1).setCellValue(toDouble(m.get("totalAmount")));
                row.createCell(2).setCellValue(toDouble(m.get("usedAmount")));
                row.createCell(3).setCellValue(toDouble(m.get("occupyAmount")));
                row.createCell(4).setCellValue(Math.round(toDouble(m.get("executionRate")) * 10) / 10.0);
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("导出预算执行失败", e);
        }
    }

    /**
     * 部门/本人两维的数据范围入参，规则与 {@code DashboardService.scopeArgs}、
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
