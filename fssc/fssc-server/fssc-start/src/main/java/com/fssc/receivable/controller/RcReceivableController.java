// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.receivable.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.service.RcReceivableService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 应收单管理（/api/tenant/receivable/**）：销售订单生成应收、按往来单位查询、逾期计提 */
@RestController
@RequestMapping("/api/tenant/receivable")
@RequiredArgsConstructor
public class RcReceivableController {

    private final RcReceivableService service;

    @RequiresPerm("receivable:list")
    @GetMapping("/page")
    public Result<PageResult<RcReceivable>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("receivable:create")
    @OperLog(module = "RECEIVABLE", operType = "INSERT", desc = "按销售订单生成应收单")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody RcReceivable e) {
        return Result.success(service.createSingle(e));
    }

    @RequiresPerm("receivable:list")
    @GetMapping("/listByCustomer")
    public Result<List<RcReceivable>> listByCustomer(@RequestParam Long customerId) {
        return Result.success(service.listByCustomer(customerId));
    }

    /**
     * 逾期坏账计提批处理（D-DEC-407）：补两个可选账期入参，不改任何判定语义。
     * <ul>
     *   <li>{@code asOfDate}=yyyy-MM-dd 跑批基准日（补跑某一天的到期扫描）；</li>
     *   <li>{@code period}=yyyyMM／yyyy-MM 账期，按期末日跑；</li>
     *   <li>两者皆不传＝系统当前日，与改造前逐字等价（前端旧按钮零改动仍可用）。</li>
     * </ul>
     */
    @RequiresPerm("receivable:overdue")
    @OperLog(module = "RECEIVABLE", operType = "UPDATE", desc = "逾期坏账计提批处理")
    @PostMapping("/accrueOverdue")
    public Result<Integer> accrueOverdueBadDebt(@RequestParam(required = false) String asOfDate,
                                                @RequestParam(required = false) String period) {
        return Result.success(service.accrueOverdueBadDebt(
                RcReceivableService.resolveAsOfDate(asOfDate, period)));
    }

    /** 应收管理分析报告（OTC05 AR_REPORT）：按期间发起分析报告审批流 */
    @RequiresPerm("receivable:report")
    @OperLog(module = "RECEIVABLE", operType = "INSERT", desc = "应收管理分析报告")
    @PostMapping("/report")
    public Result<Void> report(@RequestParam String period) {
        service.generateReport(period);
        return Result.success();
    }

    /** 应收账款对账（OTC04 AR_RECON）：发起对账审批流 */
    @RequiresPerm("receivable:report")
    @OperLog(module = "RECEIVABLE", operType = "INSERT", desc = "应收账款对账")
    @PostMapping("/recon/start")
    public Result<Void> reconStart(String period, Long customerId) {
        service.reconStart(period, customerId);
        return Result.success();
    }
}