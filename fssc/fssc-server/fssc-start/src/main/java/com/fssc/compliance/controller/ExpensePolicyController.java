package com.fssc.compliance.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.compliance.entity.ExpensePolicy;
import com.fssc.compliance.service.ExpensePolicyService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/compliance/expense-policy")
@RequiredArgsConstructor
public class ExpensePolicyController {

    private final ExpensePolicyService expensePolicyService;

    @RequiresPerm("compliance:policy:list")
    @GetMapping("/page")
    public Result<PageResult<ExpensePolicy>> page(PageQuery query,
                                                  @RequestParam(required = false) String expenseType,
                                                  @RequestParam(required = false) Integer status) {
        return Result.success(expensePolicyService.page(query.getPageNum(), query.getPageSize(), expenseType, status));
    }

    @RequiresPerm("compliance:policy:list")
    @GetMapping("/{id}")
    public Result<ExpensePolicy> getById(@PathVariable Long id) {
        return Result.success(expensePolicyService.getById(id));
    }

    @RequiresPerm("compliance:policy:create")
    @OperLog(module = "EXPENSE_POLICY", operType = "INSERT", desc = "新增费用政策")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody ExpensePolicy policy) {
        return Result.success(expensePolicyService.create(policy));
    }

    @RequiresPerm("compliance:policy:update")
    @OperLog(module = "EXPENSE_POLICY", operType = "UPDATE", desc = "更新费用政策")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody ExpensePolicy policy) {
        expensePolicyService.update(policy);
        return Result.success();
    }

    @RequiresPerm("compliance:policy:update")
    @OperLog(module = "EXPENSE_POLICY", operType = "UPDATE", desc = "启用费用政策")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        expensePolicyService.enable(id);
        return Result.success();
    }

    @RequiresPerm("compliance:policy:update")
    @OperLog(module = "EXPENSE_POLICY", operType = "UPDATE", desc = "停用费用政策")
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        expensePolicyService.disable(id);
        return Result.success();
    }

    @RequiresPerm("compliance:policy:delete")
    @OperLog(module = "EXPENSE_POLICY", operType = "DELETE", desc = "删除费用政策")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        expensePolicyService.delete(id);
        return Result.success();
    }

    @RequiresPerm("compliance:policy:list")
    @GetMapping("/effective")
    public Result<List<ExpensePolicy>> effective(@RequestParam(required = false) String expenseType,
                                                 @RequestParam(required = false) Long orgId) {
        return Result.success(expensePolicyService.listEffective(expenseType, orgId));
    }

    @SuppressWarnings("unchecked")
    @RequiresPerm("compliance:policy:update")
    @OperLog(module = "EXPENSE_POLICY", operType = "UPDATE", desc = "批量启用")
    @PostMapping("/batch-enable")
    public Result<Void> batchEnable(@RequestBody Map<String, Object> req) {
        List<Long> ids = ((List<Integer>) req.get("ids")).stream().map(Long::valueOf).toList();
        expensePolicyService.batchEnable(ids);
        return Result.success();
    }

    @SuppressWarnings("unchecked")
    @RequiresPerm("compliance:policy:update")
    @OperLog(module = "EXPENSE_POLICY", operType = "UPDATE", desc = "批量停用")
    @PostMapping("/batch-disable")
    public Result<Void> batchDisable(@RequestBody Map<String, Object> req) {
        List<Long> ids = ((List<Integer>) req.get("ids")).stream().map(Long::valueOf).toList();
        expensePolicyService.batchDisable(ids);
        return Result.success();
    }

    @SuppressWarnings("unchecked")
    @RequiresPerm("compliance:policy:delete")
    @OperLog(module = "EXPENSE_POLICY", operType = "DELETE", desc = "批量删除")
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody Map<String, Object> req) {
        List<Long> ids = ((List<Integer>) req.get("ids")).stream().map(Long::valueOf).toList();
        expensePolicyService.batchDelete(ids);
        return Result.success();
    }
}