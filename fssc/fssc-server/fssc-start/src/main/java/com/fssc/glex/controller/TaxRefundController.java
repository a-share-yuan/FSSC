// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresFeature;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.glex.entity.TaxRefund;
import com.fssc.glex.service.TaxRefundService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** 税务退税三场景（/api/tenant/glex/tax-refund/**）：出口免抵退/留抵退税/即征即退 */
@RestController
@RequestMapping("/api/tenant/glex/tax-refund")
@RequiredArgsConstructor
public class TaxRefundController {

    private final TaxRefundService service;

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxrefund:list")
    @GetMapping("/page")
    public Result<PageResult<TaxRefund>> page(PageQuery query, String refundType, String status,
                                              String period, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), refundType, status, period, keyword));
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxrefund:create")
    @OperLog(module = "TAX_REFUND", operType = "INSERT", desc = "创建退税申请")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody TaxRefund t) {
        return Result.success(service.create(t));
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxrefund:create")
    @OperLog(module = "TAX_REFUND", operType = "UPDATE", desc = "提交退税申请")
    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody IdReq req) {
        service.submit(req.getId());
        return Result.success(null);
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxrefund:approve")
    @OperLog(module = "TAX_REFUND", operType = "UPDATE", desc = "退税申请状态流转")
    @PostMapping("/status")
    public Result<Void> changeStatus(@RequestBody StatusReq req) {
        service.changeStatus(req.getId(), req.getTo(), req.getRemark(), req.getRefundedAmount());
        return Result.success(null);
    }

    /** 留抵退税：可退留抵上限（Σ认证进项 − 已批留抵退税），供表单只读提示。 */
    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxrefund:list")
    @GetMapping("/credit-quota")
    public Result<BigDecimal> creditQuota() {
        return Result.success(service.creditQuota());
    }

    @Data
    public static class IdReq {
        private Long id;
    }

    @Data
    public static class StatusReq {
        private Long id;
        private String to;
        private String remark;
        /** REFUNDING→REFUNDED 到账时登记的实际到账金额（≥0、≤可退税额） */
        private BigDecimal refundedAmount;
    }
}