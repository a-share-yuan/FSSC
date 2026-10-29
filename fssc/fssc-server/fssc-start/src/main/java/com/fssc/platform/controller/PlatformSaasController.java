// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.platform.entity.PfBill;
import com.fssc.platform.entity.PfOrder;
import com.fssc.platform.entity.PfTenantStat;
import com.fssc.platform.service.PlatformSaasService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 平台 SaaS 运营（/api/platform/saas/**）：订单、账单、租户用量统计。
 * 平台端 tenantId 恒为 null，只碰 pf_* 表。
 */
@RestController
@RequestMapping("/api/platform/saas")
@RequiredArgsConstructor
public class PlatformSaasController {

    private final PlatformSaasService saasService;

    // 订单

    @RequiresPerm("platform:order:list")
    @GetMapping("/order/page")
    public Result<PageResult<PfOrder>> orderPage(PageQuery query,
                                                 @RequestParam(required = false) Long refTenantId,
                                                 @RequestParam(required = false) String payStatus) {
        return Result.success(saasService.orderPage(query.getPageNum(), query.getPageSize(), refTenantId, payStatus));
    }

    @RequiresPerm("platform:order:create")
    @OperLog(module = "PF_ORDER", operType = "INSERT", desc = "创建订购订单")
    @PostMapping("/order/create")
    public Result<Long> orderCreate(@RequestBody PfOrder order) {
        return Result.success(saasService.createOrder(order));
    }

    @RequiresPerm("platform:order:update")
    @OperLog(module = "PF_ORDER", operType = "UPDATE", desc = "订单确认收款")
    @PostMapping("/order/{id}/paid")
    public Result<Void> orderPaid(@PathVariable Long id, @RequestParam String payMethod) {
        saasService.confirmPaid(id, payMethod);
        return Result.success();
    }

    // 账单

    @RequiresPerm("platform:bill:list")
    @GetMapping("/bill/page")
    public Result<PageResult<PfBill>> billPage(PageQuery query,
                                               @RequestParam(required = false) Long refTenantId,
                                               @RequestParam(required = false) String status) {
        return Result.success(saasService.billPage(query.getPageNum(), query.getPageSize(), refTenantId, status));
    }

    @RequiresPerm("platform:bill:create")
    @OperLog(module = "PF_BILL", operType = "INSERT", desc = "创建账单")
    @PostMapping("/bill/create")
    public Result<Long> billCreate(@RequestBody PfBill bill) {
        return Result.success(saasService.createBill(bill));
    }

    @RequiresPerm("platform:bill:update")
    @OperLog(module = "PF_BILL", operType = "UPDATE", desc = "账单收款核销")
    @PostMapping("/bill/{id}/receive")
    public Result<Void> billReceive(@PathVariable Long id, @RequestParam BigDecimal paid) {
        saasService.receivePayment(id, paid);
        return Result.success();
    }

    // 租户用量统计

    @RequiresPerm("platform:stat:list")
    @GetMapping("/stat/page")
    public Result<PageResult<PfTenantStat>> statPage(PageQuery query,
                                                     @RequestParam(required = false) Long refTenantId,
                                                     @RequestParam(required = false) LocalDate statDate) {
        return Result.success(saasService.statPage(query.getPageNum(), query.getPageSize(), refTenantId, statDate));
    }

    @RequiresPerm("platform:stat:update")
    @OperLog(module = "PF_TENANT_STAT", operType = "UPDATE", desc = "写入租户用量快照")
    @PostMapping("/stat/upsert")
    public Result<Void> statUpsert(@RequestBody PfTenantStat stat) {
        saasService.upsertStat(stat);
        return Result.success();
    }

    @RequiresPerm("platform:stat:list")
    @GetMapping("/stat/aggregate")
    public Result<List<Map<String, Object>>> statAggregate(@RequestParam LocalDate statDate) {
        return Result.success(saasService.aggregateUsage(statDate));
    }
}
