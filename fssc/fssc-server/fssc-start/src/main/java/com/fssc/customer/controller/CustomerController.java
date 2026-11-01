// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.customer.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.service.CounterpartyCandidateService;
import com.fssc.customer.service.CustomerService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 客户/往来单位管理（/api/tenant/customer/**） */
@RestController
@RequestMapping("/api/tenant/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;

    private final CounterpartyCandidateService counterpartyCandidateService;

    // 只读查询放开给所有登录租户用户：对公付款等单据发起页往来单位下拉必用（写操作仍需权限）
    @GetMapping("/page")
    public Result<PageResult<CrmCustomer>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    /**
     * 修复：往来单位候选（客户档案 ∪ 供应商主数据）。
     *
     * <p>原 {@code /page} 只读 {@code crm_customer}，而供应商落在 {@code md_supplier} 且两表零同步
     * ⇒ 对公结算的往来单位下拉里一个供应商都没有 ⇒ SETTLE 单据挂不上三单环节、根本提交不了。</p>
     *
     * <p>本端点把供应商按 {@code supplier_code} 去重建档为往来单位档案，
     * 返回的 {@code id} 是<b>语义正确的 crm_customer.id</b>（下游应收核销/坏账/对账都按这个解析），
     * 并顺带补出 {@code accountNo}/{@code bankName}（原表无账号字段，导致收款账号必填永远填不上）。</p>
     *
     * <p>只读语义放开给所有登录租户用户（同 {@code /page}），建档走按需最小写入。</p>
     */
    @GetMapping("/candidates")
    public Result<List<Map<String, Object>>> candidates(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "onlySupplier", defaultValue = "false") boolean onlySupplier,
            @RequestParam(value = "limit", defaultValue = "300") int limit) {
        return Result.success(counterpartyCandidateService.listCandidates(keyword, onlySupplier, limit));
    }

    @RequiresPerm("customer:create")
    @OperLog(module = "CUSTOMER", operType = "INSERT", desc = "新增客户")
    @PostMapping("/create")
    public Result<Long> create(@Validated @RequestBody CrmCustomer e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("customer:update")
    @OperLog(module = "CUSTOMER", operType = "UPDATE", desc = "更新客户")
    @PostMapping("/update")
    public Result<Void> update(@Validated @RequestBody CrmCustomer e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("customer:update")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @RequiresPerm("customer:update")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    /**
     * 建档即 PENDING，核准走状态位翻转（PENDING → ACTIVE，去重）。
     * 前端【核准】按钮一上线就调用本端点，但路由一直未登记——
     * service.approve() 成了无暴露路径的死代码，核准通路断头（调用即 404，落"目录外动作不可调用"）。
     * 权限沿用 customer:update（核准属档案维护动作，不新设权限点，避免目录漂移）。
     */
    @RequiresPerm("customer:update")
    @OperLog(module = "CUSTOMER", operType = "UPDATE", desc = "核准客户（PENDING→ACTIVE）")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        service.approve(id);
        return Result.success();
    }
}
