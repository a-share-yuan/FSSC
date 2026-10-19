// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.report.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 报表看板（/api/tenant/dashboard/**）：报销统计/预算执行率/付款进度（租户内聚合） */
@RestController
@RequestMapping("/api/tenant/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @RequiresPerm("dashboard:view")
    @DataScope
    @GetMapping("/expense/stats")
    public Result<List<Map<String, Object>>> expenseStats(@RequestParam(required = false) String billType) {
        return Result.success(dashboardService.expenseByStatus(billType));
    }

    /**
     * R5-D-020：费用概览四枚指标（本月报销总额/待审单据/已付金额/预算执行率），
     * 单枚 camelCase Map 返回。与 /expense/stats 的分组明细数组并存——后者的既有契约不动。
     */
    @RequiresPerm("dashboard:view")
    @DataScope("费用概览数据范围")
    @GetMapping("/expense/overview")
    public Result<Map<String, Object>> expenseOverview() {
        return Result.success(dashboardService.expenseOverview());
    }

    @RequiresPerm("dashboard:view")
    @DataScope
    @GetMapping("/expense/trend")
    public Result<List<Map<String, Object>>> expenseTrend(@RequestParam(required = false) String billType,
                                                          @RequestParam(defaultValue = "6") int months) {
        return Result.success(dashboardService.expenseTrend(billType, months));
    }

    @RequiresPerm("dashboard:view")
    @DataScope
    @GetMapping("/budget/execution")
    public Result<List<Map<String, Object>>> budgetExecution() {
        return Result.success(dashboardService.budgetExecution());
    }

    @RequiresPerm("dashboard:view")
    @GetMapping("/pay/progress")
    public Result<Map<String, Object>> payProgress() {
        return Result.success(dashboardService.payProgress());
    }

    @RequiresPerm("dashboard:view")
    @DataScope("部门报销排行数据范围") // D-F1R-04：原缺注解 ⇒ 普通员工看到与共享会计完全相同的全公司排名
    @GetMapping("/dept/ranking")
    public Result<List<Map<String, Object>>> deptRanking() {
        return Result.success(dashboardService.expenseByDept());
    }

    @RequiresPerm("dashboard:view")
    @DataScope("审批时效数据范围") // D-F1R-04：同 deptRanking，缺注解即无部门/本人维收口
    @GetMapping("/approval/efficiency")
    public Result<List<Map<String, Object>>> approvalEfficiency() {
        return Result.success(dashboardService.approvalEfficiency());
    }

    // ========== 业务实时进度看板：合同/发票/档案（WS-4 看板完整性） ==========

    @RequiresPerm("dashboard:view")
    @DataScope("业务进度看板数据范围")
    @GetMapping("/contract/progress")
    public Result<Map<String, Object>> contractProgress() {
        return Result.success(dashboardService.contractProgress());
    }

    @RequiresPerm("dashboard:view")
    @DataScope("业务进度看板数据范围")
    @GetMapping("/invoice/progress")
    public Result<Map<String, Object>> invoiceProgress() {
        return Result.success(dashboardService.invoiceProgress());
    }

    @RequiresPerm("dashboard:view")
    @DataScope("业务进度看板数据范围")
    @GetMapping("/archive/progress")
    public Result<Map<String, Object>> archiveProgress() {
        return Result.success(dashboardService.archiveProgress());
    }

    // ========== 合规风险看板（P1 增强） ==========

    @RequiresPerm("dashboard:compliance:view")
    @GetMapping("/compliance-risk")
    public Result<Map<String, Object>> complianceRisk() {
        return Result.success(dashboardService.complianceRisk());
    }

    // ========== 共享运营看板（P1 增强） ==========

    @RequiresPerm("dashboard:ops:view")
    @GetMapping("/ops/overview")
    public Result<Map<String, Object>> opsOverview() {
        return Result.success(dashboardService.opsOverview());
    }
}
