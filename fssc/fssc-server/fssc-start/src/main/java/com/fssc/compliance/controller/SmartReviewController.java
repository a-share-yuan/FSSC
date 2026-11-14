package com.fssc.compliance.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.compliance.entity.ReviewLog;
import com.fssc.compliance.entity.ReviewRule;
import com.fssc.compliance.service.SmartReviewService;
import com.fssc.compliance.service.SmartReviewService.ReviewResult;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 智能审单引擎（/api/tenant/compliance/review/**）。审单规则 CRUD + 执行审单 + 日志查询。 */
@RestController
@RequestMapping("/api/tenant/compliance/review")
@RequiredArgsConstructor
public class SmartReviewController {

    private final SmartReviewService smartReviewService;

    // 规则 CRUD

    @RequiresPerm("compliance:review:list")
    @GetMapping("/rules")
    public Result<PageResult<ReviewRule>> pageRules(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String ruleCategory) {
        return Result.success(smartReviewService.pageRules(pageNum, pageSize, ruleCategory));
    }

    @RequiresPerm("compliance:review:list")
    @GetMapping("/rules/{id}")
    public Result<ReviewRule> getRule(@PathVariable Long id) {
        return Result.success(smartReviewService.getRule(id));
    }

    @RequiresPerm("compliance:review:add")
    @OperLog(module = "REVIEW_RULE", operType = "INSERT", desc = "新增审单规则")
    @PostMapping("/rules")
    public Result<Void> createRule(@RequestBody ReviewRule rule) {
        smartReviewService.createRule(rule);
        return Result.success();
    }

    @RequiresPerm("compliance:review:update")
    @OperLog(module = "REVIEW_RULE", operType = "UPDATE", desc = "修改审单规则")
    @PutMapping("/rules/{id}")
    public Result<Void> updateRule(@PathVariable Long id, @RequestBody ReviewRule rule) {
        rule.setId(id);
        smartReviewService.updateRule(rule);
        return Result.success();
    }

    @RequiresPerm("compliance:review:delete")
    @OperLog(module = "REVIEW_RULE", operType = "DELETE", desc = "删除审单规则")
    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        smartReviewService.deleteRule(id);
        return Result.success();
    }

    @RequiresPerm("compliance:review:update")
    @OperLog(module = "REVIEW_RULE", operType = "UPDATE", desc = "启用审单规则")
    @PostMapping("/rules/{id}/enable")
    public Result<Void> enableRule(@PathVariable Long id) {
        smartReviewService.enableRule(id);
        return Result.success();
    }

    @RequiresPerm("compliance:review:update")
    @OperLog(module = "REVIEW_RULE", operType = "UPDATE", desc = "停用审单规则")
    @PostMapping("/rules/{id}/disable")
    public Result<Void> disableRule(@PathVariable Long id) {
        smartReviewService.disableRule(id);
        return Result.success();
    }

    // 批量操作

    @RequiresPerm("compliance:review:update")
    @OperLog(module = "REVIEW_RULE", operType = "UPDATE", desc = "批量启用")
    @PostMapping("/rules/batch-enable")
    public Result<Void> batchEnable(@RequestBody Map<String, Object> req) {
        List<Long> ids = ((List<Integer>) req.get("ids")).stream().map(Long::valueOf).toList();
        smartReviewService.batchEnable(ids);
        return Result.success();
    }

    @RequiresPerm("compliance:review:update")
    @OperLog(module = "REVIEW_RULE", operType = "UPDATE", desc = "批量停用")
    @PostMapping("/rules/batch-disable")
    public Result<Void> batchDisable(@RequestBody Map<String, Object> req) {
        List<Long> ids = ((List<Integer>) req.get("ids")).stream().map(Long::valueOf).toList();
        smartReviewService.batchDisable(ids);
        return Result.success();
    }

    @RequiresPerm("compliance:review:delete")
    @OperLog(module = "REVIEW_RULE", operType = "DELETE", desc = "批量删除")
    @PostMapping("/rules/batch-delete")
    public Result<Void> batchDelete(@RequestBody Map<String, Object> req) {
        List<Long> ids = ((List<Integer>) req.get("ids")).stream().map(Long::valueOf).toList();
        smartReviewService.batchDelete(ids);
        return Result.success();
    }

    // 执行审单

    @RequiresPerm("compliance:review:execute")
    @OperLog(module = "REVIEW_RULE", operType = "OTHER", desc = "执行审单")
    @PostMapping("/execute")
    public Result<ReviewResult> executeReview(@RequestBody Map<String, Object> req) {
        Long billId = req.get("billId") != null ? Long.valueOf(req.get("billId").toString()) : null;
        String billNo = (String) req.get("billNo");
        @SuppressWarnings("unchecked")
        Map<String, Object> billData = (Map<String, Object>) req.get("billData");
        return Result.success(smartReviewService.executeReview(billId, billNo, billData));
    }

    // 审单日志

    @RequiresPerm("compliance:review:log")
    @GetMapping("/logs")
    public Result<PageResult<ReviewLog>> pageLogs(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long billId,
            @RequestParam(required = false) String action) {
        return Result.success(smartReviewService.pageLogs(pageNum, pageSize, billId, action));
    }

    @RequiresPerm("compliance:review:log")
    @GetMapping("/logs/bill/{billId}")
    public Result<List<ReviewLog>> listLogsByBill(@PathVariable Long billId) {
        return Result.success(smartReviewService.listLogsByBill(billId));
    }

    // 统计

    @RequiresPerm("compliance:review:list")
    @GetMapping("/stats")
    public Result<Map<String, Object>> reviewStats() {
        return Result.success(smartReviewService.reviewStats());
    }
}