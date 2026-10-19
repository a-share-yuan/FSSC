// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.controller;

import com.fssc.common.core.Result;
import com.fssc.compliance.entity.ExpensePolicyStandard;
import com.fssc.compliance.service.ExpensePolicyStandardService;
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

@RestController
@RequestMapping("/api/tenant/compliance/policy-standard")
@RequiredArgsConstructor
public class ExpensePolicyStandardController {

    private final ExpensePolicyStandardService expensePolicyStandardService;

    @RequiresPerm("compliance:policystandard:list")
    @GetMapping("/list")
    public Result<List<ExpensePolicyStandard>> list(@RequestParam Long policyId) {
        return Result.success(expensePolicyStandardService.listByPolicy(policyId));
    }

    @RequiresPerm("compliance:policystandard:list")
    @GetMapping("/{id}")
    public Result<ExpensePolicyStandard> getById(@PathVariable Long id) {
        return Result.success(expensePolicyStandardService.getById(id));
    }

    @RequiresPerm("compliance:policystandard:create")
    @OperLog(module = "POLICY_STANDARD", operType = "INSERT", desc = "新增费用政策标准")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody ExpensePolicyStandard standard) {
        return Result.success(expensePolicyStandardService.create(standard));
    }

    @RequiresPerm("compliance:policystandard:update")
    @OperLog(module = "POLICY_STANDARD", operType = "UPDATE", desc = "更新费用政策标准")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody ExpensePolicyStandard standard) {
        expensePolicyStandardService.update(standard);
        return Result.success();
    }

    @RequiresPerm("compliance:policystandard:delete")
    @OperLog(module = "POLICY_STANDARD", operType = "DELETE", desc = "删除费用政策标准")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        expensePolicyStandardService.delete(id);
        return Result.success();
    }
}
