// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.payplan.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.payplan.entity.PayPlan;
import com.fssc.payplan.service.PayPlanService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 采购付款计划（/api/tenant/pay-plan/**）：基地/板块采购付款计划申请（PAY_PLAN_BASE / PAY_PLAN_PLATE） */
@RestController
@RequestMapping("/api/tenant/pay-plan")
@RequiredArgsConstructor
public class PayPlanController {

    private final PayPlanService service;

    @RequiresPerm("payplan:list")
    @GetMapping("/page")
    public Result<PageResult<PayPlan>> page(PageQuery query, String keyword, String planType) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword, planType));
    }

    /** 新建付款计划并发起审批（planType=BASE→PAY_PLAN_BASE / PLATE→PAY_PLAN_PLATE），返回单据ID */
    @RequiresPerm("payplan:add")
    @OperLog(module = "PAY_PLAN", operType = "ADD", desc = "提交采购付款计划并发起审批")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody PayPlan plan) {
        return Result.success(service.create(plan));
    }

    /** 批次1：驳回计划重报（REJECTED→重新审批），替代"驳回只能新建绕过"的死单出口 */
    @RequiresPerm("payplan:add")
    @OperLog(module = "PAY_PLAN", operType = "UPDATE", desc = "驳回付款计划重新提交审批")
    @PostMapping("/{id}/resubmit")
    public Result<Void> resubmit(@PathVariable Long id) {
        service.resubmit(id);
        return Result.success(null);
    }

    /**
     * 提单人作废在途计划（APPROVING→VOID，同步终止审批链）。
     * 权限码复用 {@code payplan:add}＝与建单/重报同一批角色，不新增码（新码无人持有＝端点作废）。
     */
    @RequiresPerm("payplan:add")
    @OperLog(module = "PAY_PLAN", operType = "UPDATE", desc = "作废采购付款计划并终止审批")
    @PostMapping("/{id}/void")
    public Result<Void> voidPlan(@PathVariable Long id,
                                 @RequestBody(required = false) Map<String, String> body) {
        service.voidPlan(id, body == null ? null : body.get("reason"));
        return Result.success(null);
    }
}