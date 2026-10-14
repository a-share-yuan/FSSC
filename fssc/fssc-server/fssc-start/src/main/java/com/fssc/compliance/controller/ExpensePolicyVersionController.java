// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.controller;

import com.fssc.common.core.Result;
import com.fssc.compliance.entity.ExpensePolicyVersion;
import com.fssc.compliance.service.ExpensePolicyVersionService;
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
@RequestMapping("/api/tenant/compliance/policy-version")
@RequiredArgsConstructor
public class ExpensePolicyVersionController {

    private final ExpensePolicyVersionService expensePolicyVersionService;

    @RequiresPerm("compliance:policyversion:list")
    @GetMapping("/list")
    public Result<List<ExpensePolicyVersion>> list(@RequestParam Long policyId) {
        return Result.success(expensePolicyVersionService.listByPolicy(policyId));
    }

    @RequiresPerm("compliance:policyversion:list")
    @GetMapping("/{id}")
    public Result<ExpensePolicyVersion> getById(@PathVariable Long id) {
        return Result.success(expensePolicyVersionService.getById(id));
    }

    @RequiresPerm("compliance:policyversion:list")
    @GetMapping("/latest")
    public Result<ExpensePolicyVersion> getLatest(@RequestParam Long policyId) {
        return Result.success(expensePolicyVersionService.getLatest(policyId));
    }

    @RequiresPerm("compliance:policyversion:create")
    @OperLog(module = "POLICY_VERSION", operType = "INSERT", desc = "新增政策版本")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody ExpensePolicyVersion version) {
        return Result.success(expensePolicyVersionService.create(version));
    }

    @RequiresPerm("compliance:policyversion:approve")
    @OperLog(module = "POLICY_VERSION", operType = "UPDATE", desc = "审批政策版本")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestParam Long approverId) {
        expensePolicyVersionService.approve(id, approverId);
        return Result.success();
    }

    @RequiresPerm("compliance:policyversion:update")
    @OperLog(module = "POLICY_VERSION", operType = "UPDATE", desc = "激活政策版本")
    @PostMapping("/{id}/activate")
    public Result<Void> activate(@PathVariable Long id) {
        expensePolicyVersionService.activate(id);
        return Result.success();
    }

    @RequiresPerm("compliance:policyversion:update")
    @OperLog(module = "POLICY_VERSION", operType = "UPDATE", desc = "归档政策版本")
    @PostMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        expensePolicyVersionService.archive(id);
        return Result.success();
    }

    @RequiresPerm("compliance:policyversion:delete")
    @OperLog(module = "POLICY_VERSION", operType = "DELETE", desc = "删除政策版本")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        expensePolicyVersionService.delete(id);
        return Result.success();
    }
}
