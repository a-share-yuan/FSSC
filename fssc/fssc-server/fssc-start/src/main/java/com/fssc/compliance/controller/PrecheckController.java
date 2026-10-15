package com.fssc.compliance.controller;

import com.fssc.common.core.Result;
import com.fssc.compliance.entity.PrecheckResult;
import com.fssc.compliance.entity.ReviewRule;
import com.fssc.compliance.service.PrecheckEngineService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tenant/compliance/precheck")
@RequiredArgsConstructor
public class PrecheckController {

    private final PrecheckEngineService precheckEngineService;

    @RequiresPerm("compliance:precheck:list")
    @GetMapping("/rules")
    public Result<List<ReviewRule>> listRules(@RequestParam(required = false) String category,
                                                 @RequestParam(required = false) String status) {
        return Result.success(precheckEngineService.listRules(category, status));
    }

    @RequiresPerm("compliance:precheck:list")
    @GetMapping("/rules/by-expense")
    public Result<List<ReviewRule>> listRulesByExpense(@RequestParam(required = false) String expenseType) {
        return Result.success(precheckEngineService.listRulesByExpenseType(expenseType));
    }

    @RequiresPerm("compliance:precheck:run")
    @OperLog(module = "PRECHECK", operType = "INSERT", desc = "执行合规预检")
    @PostMapping(value = "/run", consumes = "application/json")
    public Result<String> runPrecheck(@RequestBody Map<String, Object> req) {
        Long billId = req.get("billId") != null ? Long.valueOf(req.get("billId").toString()) : null;
        String billType = (String) req.get("billType");
        String expenseType = (String) req.get("expenseType");
        Long employeeId = req.get("employeeId") != null ? Long.valueOf(req.get("employeeId").toString()) : null;
        return Result.success(precheckEngineService.runPrecheck(billId, billType, expenseType, employeeId));
    }

    @RequiresPerm("compliance:precheck:run")
    @PostMapping(value = "/evaluate", consumes = "application/json")
    public Result<PrecheckResult> evaluate(@RequestBody Map<String, Object> req) {
        String ruleCode = (String) req.get("ruleCode");
        @SuppressWarnings("unchecked")
        Map<String, Object> inputData = (Map<String, Object>) req.get("inputData");
        ReviewRule rule = precheckEngineService.findRuleByCode(ruleCode);
        if (rule == null) {
            return Result.fail(404, "规则不存在: " + ruleCode);
        }
        return Result.success(precheckEngineService.evaluateRule(rule, inputData));
    }

    @RequiresPerm("compliance:precheck:list")
    @GetMapping("/batch/{batchId}")
    public Result<PrecheckResult> getBatch(@PathVariable String batchId) {
        return Result.success(precheckEngineService.getBatch(batchId));
    }

    @RequiresPerm("compliance:precheck:list")
    @GetMapping("/batch/{batchId}/results")
    public Result<List<PrecheckResult>> getResults(@PathVariable String batchId) {
        return Result.success(precheckEngineService.getResults(batchId));
    }

    // === Precheck Rule CRUD ===

    @RequiresPerm("compliance:precheck:list")
    @GetMapping("/rules/{id}")
    public Result<ReviewRule> getRule(@PathVariable Long id) {
        return Result.success(precheckEngineService.getRuleById(id));
    }

    @RequiresPerm("compliance:precheck:create")
    @OperLog(module = "PRECHECK_RULE", operType = "INSERT", desc = "新增预检规则")
    @PostMapping("/rules/create")
    public Result<Long> createRule(@RequestBody ReviewRule rule) {
        return Result.success(precheckEngineService.createRule(rule));
    }

    @RequiresPerm("compliance:precheck:update")
    @OperLog(module = "PRECHECK_RULE", operType = "UPDATE", desc = "更新预检规则")
    @PostMapping("/rules/update")
    public Result<Void> updateRule(@RequestBody ReviewRule rule) {
        precheckEngineService.updateRule(rule);
        return Result.success();
    }

    @RequiresPerm("compliance:precheck:update")
    @OperLog(module = "PRECHECK_RULE", operType = "UPDATE", desc = "启用预检规则")
    @PostMapping("/rules/{id}/enable")
    public Result<Void> enableRule(@PathVariable Long id) {
        precheckEngineService.enableRule(id);
        return Result.success();
    }

    @RequiresPerm("compliance:precheck:update")
    @OperLog(module = "PRECHECK_RULE", operType = "UPDATE", desc = "停用预检规则")
    @PostMapping("/rules/{id}/disable")
    public Result<Void> disableRule(@PathVariable Long id) {
        precheckEngineService.disableRule(id);
        return Result.success();
    }

    @RequiresPerm("compliance:precheck:delete")
    @OperLog(module = "PRECHECK_RULE", operType = "DELETE", desc = "删除预检规则")
    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        precheckEngineService.deleteRule(id);
        return Result.success();
    }

    // === 脚本验证 ===

    @RequiresPerm("compliance:precheck:list")
    @OperLog(module = "PRECHECK_RULE", operType = "OTHER", desc = "验证脚本")
    @PostMapping("/validate-script")
    public Result<Map<String, Object>> validateScript(@RequestBody Map<String, Object> req) {
        String scriptContent = (String) req.get("scriptContent");
        @SuppressWarnings("unchecked")
        Map<String, Object> sampleData = (Map<String, Object>) req.get("sampleData");
        return Result.success(precheckEngineService.validateScript(scriptContent, sampleData));
    }

    // === 批量操作 ===

    @RequiresPerm("compliance:precheck:update")
    @OperLog(module = "PRECHECK_RULE", operType = "UPDATE", desc = "批量启用")
    @PostMapping("/rules/batch-enable")
    public Result<Void> batchEnable(@RequestBody Map<String, Object> req) {
        List<Long> ids = ((List<Integer>) req.get("ids")).stream().map(Long::valueOf).toList();
        precheckEngineService.batchEnable(ids);
        return Result.success();
    }

    @RequiresPerm("compliance:precheck:update")
    @OperLog(module = "PRECHECK_RULE", operType = "UPDATE", desc = "批量停用")
    @PostMapping("/rules/batch-disable")
    public Result<Void> batchDisable(@RequestBody Map<String, Object> req) {
        List<Long> ids = ((List<Integer>) req.get("ids")).stream().map(Long::valueOf).toList();
        precheckEngineService.batchDisable(ids);
        return Result.success();
    }

    @RequiresPerm("compliance:precheck:delete")
    @OperLog(module = "PRECHECK_RULE", operType = "DELETE", desc = "批量删除")
    @PostMapping("/rules/batch-delete")
    public Result<Void> batchDelete(@RequestBody Map<String, Object> req) {
        List<Long> ids = ((List<Integer>) req.get("ids")).stream().map(Long::valueOf).toList();
        precheckEngineService.batchDelete(ids);
        return Result.success();
    }
}