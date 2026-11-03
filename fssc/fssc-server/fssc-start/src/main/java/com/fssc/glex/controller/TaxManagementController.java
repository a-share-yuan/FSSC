// SPDX-License-Identifier: AGPL-3.0-or-later
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
import com.fssc.glex.entity.TaxRiskIndicator;
import com.fssc.glex.service.TaxDeclarationService;
import com.fssc.glex.service.TaxRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 税务管理统一入口（税务一体化）。
 * <p>
 * 覆盖两大模块：
 * 1. 全税种申报（/api/tenant/tax/declaration/*）
 * 2. 税务风险指标（/api/tenant/tax/risk/*）
 * <p>
 * <b>进项认证不在这里</b>：认证的对象是发票池里的进项发票，唯一写入口是
 * {@code InvoiceService.certify/cancelCertify/transferOut}（{@code /api/tenant/invoice/certify/**}，
 * 页面在「发票管理 → 进项发票认证」）。税务侧此前另有一套 {@code /tax/certify/**} 与独立页面，
 * 与本域重复且状态口径不一致，已于 2026-11-03 合并删除——一套动作只能有一个入口。
 * <p>
 * WS-6 安全加固：全部端点补 {@code @DataScope}，由切面写入登录人数据范围，
 * 服务层据此按公司维度办结（读过滤 + 写归属校验），杜绝同租户内跨公司读写。
 * <p>
 * <b></b>：base path 由 {@code /api/tax} 收到 {@code /api/tenant/tax}。
 * 原前缀既不在 {@code /api/platform/**} 也不在 {@code /api/tenant/**}，等于游离在两级路由闸之外
 * （{@code JwtAuthFilter} 只对这两个前缀做身份维度拦截）；同时它与发票能力网关的中性契约
 * {@code /api/tax/v1/**}（{@code TaxInvoiceAdapter}、{@code QxyGatewayOcrChannel}）撞面，
 * 路由归属含糊。收进 tenant 前缀后平台账号调这些端点会被 TENANT_ONLY 直接拒。
 */
@RestController
@RequestMapping("/api/tenant/tax")
@RequiredArgsConstructor
public class TaxManagementController {

    private final TaxDeclarationService declarationService;
    private final TaxRiskService riskService;

    // 全税种申报

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

    /** 补出/重出应缴凭证：申报确认时出证失败或历史单从未出证时重试，幂等 */
    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxdecl:file")
    @DataScope("税务申报数据范围")
    @OperLog(module = "TAX_DECLARATION", operType = "UPDATE", desc = "生成应缴凭证")
    @PostMapping("/declaration/{id}/voucher")
    public Result<Long> declarationVoucher(@PathVariable Long id) {
        return Result.success(declarationService.generateVoucher(id));
    }

    /** 更正（调整）申报：复制原申报单到新属期并置 ADJUST，关联原单 */
    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxdecl:create")
    @DataScope("税务申报数据范围")
    @OperLog(module = "TAX_DECLARATION", operType = "INSERT", desc = "更正申报")
    @PostMapping("/declaration/{id}/adjust-copy")
    public Result<Long> adjustCopy(@PathVariable Long id, @RequestParam(required = false) String period) {
        return Result.success(declarationService.copyForAdjust(id, period));
    }

    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxdecl:list")
    @DataScope("税务申报数据范围")
    @GetMapping("/declaration/sum")
    public Result<BigDecimal> sumTaxAmount(@RequestParam String period, @RequestParam(required = false) String taxType) {
        return Result.success(declarationService.sumTaxAmount(period, taxType));
    }

    // 税务风险指标

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

    /** 税负率自动算（唯一自动指标），数据源与「税负分析」报表同一条 SQL */
    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("gl:taxrisk:resolve")
    @DataScope("税务风险数据范围")
    @OperLog(module = "TAX_RISK", operType = "UPDATE", desc = "计算税负率指标")
    @PostMapping("/risk/compute-burden")
    public Result<TaxRiskIndicator> computeBurden(@RequestParam String period) {
        return Result.success(riskService.computeTaxBurden(period));
    }
}
