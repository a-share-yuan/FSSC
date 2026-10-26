// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.controller;

import com.fssc.common.core.Result;
import com.fssc.compliance.entity.Reviewer;
import com.fssc.compliance.service.ReviewerService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/compliance/reviewer")
@RequiredArgsConstructor
public class ReviewerController {

    private final ReviewerService reviewerService;

    @RequiresPerm("compliance:reviewer:list")
    @GetMapping("/list")
    public Result<List<Reviewer>> list() {
        return Result.success(reviewerService.listAllActive());
    }

    @RequiresPerm("compliance:reviewer:list")
    @GetMapping("/{id}")
    public Result<Reviewer> getById(@PathVariable Long id) {
        return Result.success(reviewerService.getById(id));
    }

    @RequiresPerm("compliance:reviewer:list")
    @GetMapping("/by-group/{groupId}")
    public Result<List<Reviewer>> listByGroup(@PathVariable Long groupId) {
        return Result.success(reviewerService.listByGroup(groupId));
    }

    @RequiresPerm("compliance:reviewer:list")
    @GetMapping("/by-user/{userId}")
    public Result<Reviewer> getByUserId(@PathVariable Long userId) {
        return Result.success(reviewerService.getByUserId(userId));
    }

    @RequiresPerm("compliance:reviewer:create")
    @OperLog(module = "REVIEWER", operType = "INSERT", desc = "新增审核员")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody Reviewer reviewer) {
        return Result.success(reviewerService.create(reviewer));
    }

    @RequiresPerm("compliance:reviewer:update")
    @OperLog(module = "REVIEWER", operType = "UPDATE", desc = "更新审核员")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody Reviewer reviewer) {
        reviewerService.update(reviewer);
        return Result.success();
    }

    @RequiresPerm("compliance:reviewer:update")
    @OperLog(module = "REVIEWER", operType = "UPDATE", desc = "启用审核员")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        reviewerService.enable(id);
        return Result.success();
    }

    @RequiresPerm("compliance:reviewer:update")
    @OperLog(module = "REVIEWER", operType = "UPDATE", desc = "停用审核员")
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        reviewerService.disable(id);
        return Result.success();
    }

    @RequiresPerm("compliance:reviewer:delete")
    @OperLog(module = "REVIEWER", operType = "DELETE", desc = "删除审核员")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reviewerService.delete(id);
        return Result.success();
    }

    @SuppressWarnings("unchecked")
    @RequiresPerm("compliance:reviewer:update")
    @OperLog(module = "REVIEWER", operType = "UPDATE", desc = "批量启用")
    @PostMapping("/batch-enable")
    public Result<Void> batchEnable(@RequestBody Map<String, Object> req) {
        List<Long> ids = ((List<Integer>) req.get("ids")).stream().map(Long::valueOf).toList();
        reviewerService.batchEnable(ids);
        return Result.success();
    }

    @SuppressWarnings("unchecked")
    @RequiresPerm("compliance:reviewer:update")
    @OperLog(module = "REVIEWER", operType = "UPDATE", desc = "批量停用")
    @PostMapping("/batch-disable")
    public Result<Void> batchDisable(@RequestBody Map<String, Object> req) {
        List<Long> ids = ((List<Integer>) req.get("ids")).stream().map(Long::valueOf).toList();
        reviewerService.batchDisable(ids);
        return Result.success();
    }

    @SuppressWarnings("unchecked")
    @RequiresPerm("compliance:reviewer:delete")
    @OperLog(module = "REVIEWER", operType = "DELETE", desc = "批量删除")
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody Map<String, Object> req) {
        List<Long> ids = ((List<Integer>) req.get("ids")).stream().map(Long::valueOf).toList();
        reviewerService.batchDelete(ids);
        return Result.success();
    }
}