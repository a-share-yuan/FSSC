// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.framework.security.annotation.RequiresFeature;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.glex.entity.TaxDeclaration;
import com.fssc.glex.entity.TaxInputCertify;
import com.fssc.glex.entity.TaxRiskIndicator;
import com.fssc.glex.service.TaxCertifyService;
import com.fssc.glex.service.TaxDeclarationService;
import com.fssc.glex.service.TaxRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 税务管理统一入口（P1-3 税务一体化）。
 * <p>
 * 覆盖三大模块：
 * 1. 进项认证台账（/api/tax/certify/*）
 * 2. 全税种申报（/api/tax/declaration/*）
 * 3. 税务风险指标（/api/tax/risk/*）
 * <p>
 * WS-6 安全加固：全部端点补 {@code @DataScope}，由切面写入登录人数据范围，
 * 服务层据此按公司维度收口（读过滤 + 写归属校验），杜绝同租户内跨公司读写。
 */
@RestController
@RequestMapping("/api/tax")
@RequiredArgsConstructor
public class TaxManagementController {

    private final TaxCertifyService certifyService;
    private final TaxDeclarationService declarationService;
    private final TaxRiskService riskService;

    // ================ 进项认证台账 ================

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxcertify:list")
    @DataScope("税务进项认证数据范围")
    @GetMapping("/certify/page")
    public Result<PageResult<TaxInputCertify>> certifyPage(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(certifyService.page(query.getPageNum(), query.getPageSize(), keyword, status));
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxcertify:create")
    @DataScope("税务进项认证数据范围")
    @OperLog(module = "TAX_CERTIFY", operType = "INSERT", desc = "新增进项认证台账")
    @PostMapping("/certify")
    public Result<Long> createCertify(@RequestBody TaxInputCertify record) {
        return Result.success(certifyService.create(record));
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxcertify:certify")
    @DataScope("税务进项认证数据范围")
    @OperLog(module = "TAX_CERTIFY", operType = "UPDATE", desc = "认证发票")
    @PutMapping("/certify/{id}/certify")
    public Result<Void> certify(@PathVariable Long id, @RequestParam(required = false) String period) {
        certifyService.certify(id, period);
        return Result.success();
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxcertify:certify")
    @DataScope("税务进项认证数据范围")
    @OperLog(module = "TAX_CERTIFY", operType = "UPDATE", desc = "批量认证")
    @PutMapping("/certify/batch-certify")
    public Result<Integer> batchCertify(@RequestBody List<Long> ids, @RequestParam(required = false) String period) {
        return Result.success(certifyService.batchCertify(ids, period));
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxcertify:deduct")
    @DataScope("税务进项认证数据范围")
    @OperLog(module = "TAX_CERTIFY", operType = "UPDATE", desc = "抵扣进项税")
    @PutMapping("/certify/{id}/deduct")
    public Result<Void> deduct(@PathVariable Long id, @RequestParam(required = false) String period) {
        certifyService.deduct(id, period);
        return Result.success();
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxcertify:transfer")
    @DataScope("税务进项认证数据范围")
    @OperLog(module = "TAX_CERTIFY", operType = "UPDATE", desc = "进项转出")
    @PutMapping("/certify/{id}/transfer")
    public Result<Void> transfer(@PathVariable Long id, @RequestParam String reason) {
        certifyService.transfer(id, reason);
        return Result.success();
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxcertify:revoke")
    @DataScope("税务进项认证数据范围")
    @OperLog(module = "TAX_CERTIFY", operType = "UPDATE", desc = "撤销认证")
    @PutMapping("/certify/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id) {
        certifyService.revoke(id);
        return Result.success();
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxcertify:list")
    @DataScope("税务进项认证数据范围")
    @GetMapping("/certify/sum-deductible")
    public Result<BigDecimal> sumDeductible(@RequestParam String period) {
        return Result.success(certifyService.sumDeductible(period));
    }

    // ================ 全税种申报 ================

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxdecl:list")
    @DataScope("税务申报数据范围")
    @GetMapping("/declaration/page")
    public Result<PageResult<TaxDeclaration>> declarationPage(
            PageQuery query,
            @RequestParam(required = false) String taxType,
            @RequestParam(required = false) String period) {
        return Result.success(declarationService.page(query.getPageNum(), query.getPageSize(), taxType, period));
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxdecl:create")
    @DataScope("税务申报数据范围")
    @OperLog(module = "TAX_DECLARATION", operType = "INSERT", desc = "创建税务申报")
    @PostMapping("/declaration")
    public Result<Long> createDeclaration(@RequestBody TaxDeclaration record) {
        return Result.success(declarationService.create(record));
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxdecl:submit")
    @DataScope("税务申报数据范围")
    @OperLog(module = "TAX_DECLARATION", operType = "UPDATE", desc = "提交申报")
    @PutMapping("/declaration/{id}/submit")
    public Result<Void> submitDeclaration(@PathVariable Long id) {
        declarationService.submit(id);
        return Result.success();
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxdecl:approve")
    @DataScope("税务申报数据范围")
    @OperLog(module = "TAX_DECLARATION", operType = "UPDATE", desc = "审批通过")
    @PutMapping("/declaration/{id}/approve")
    public Result<Void> approveDeclaration(@PathVariable Long id) {
        declarationService.approve(id);
        return Result.success();
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxdecl:file")
    @DataScope("税务申报数据范围")
    @OperLog(module = "TAX_DECLARATION", operType = "UPDATE", desc = "确认申报")
    @PutMapping("/declaration/{id}/file")
    public Result<Void> fileDeclaration(@PathVariable Long id) {
        declarationService.file(id);
        return Result.success();
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxdecl:pay")
    @DataScope("税务申报数据范围")
    @OperLog(module = "TAX_DECLARATION", operType = "UPDATE", desc = "确认缴纳")
    @PutMapping("/declaration/{id}/pay")
    public Result<Void> payDeclaration(@PathVariable Long id, @RequestParam(required = false) BigDecimal paidAmount) {
        declarationService.pay(id, paidAmount);
        return Result.success();
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxdecl:list")
    @DataScope("税务申报数据范围")
    @GetMapping("/declaration/sum")
    public Result<BigDecimal> sumTaxAmount(@RequestParam String period, @RequestParam(required = false) String taxType) {
        return Result.success(declarationService.sumTaxAmount(period, taxType));
    }

    // ================ 税务风险指标 ================

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxrisk:list")
    @DataScope("税务风险数据范围")
    @GetMapping("/risk/page")
    public Result<PageResult<TaxRiskIndicator>> riskPage(
            PageQuery query,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) Boolean unresolvedOnly) {
        return Result.success(riskService.page(query.getPageNum(), query.getPageSize(), riskLevel, unresolvedOnly));
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxrisk:list")
    @DataScope("税务风险数据范围")
    @GetMapping("/risk/high")
    public Result<List<TaxRiskIndicator>> highRiskList() {
        return Result.success(riskService.listHighRisk());
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxrisk:resolve")
    @DataScope("税务风险数据范围")
    @OperLog(module = "TAX_RISK", operType = "UPDATE", desc = "处理风险")
    @PutMapping("/risk/{id}/resolve")
    public Result<Void> resolveRisk(@PathVariable Long id, @RequestParam String remark) {
        riskService.resolve(id, remark);
        return Result.success();
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxrisk:list")
    @DataScope("税务风险数据范围")
    @GetMapping("/risk/summary")
    public Result<TaxRiskService.RiskSummary> riskSummary(@RequestParam(required = false) String period) {
        return Result.success(riskService.summary(period));
    }
}
