// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.controller;

import com.fssc.common.core.Result;
import com.fssc.compliance.entity.Reviewer;
import com.fssc.sharing.entity.ShGroup;
import com.fssc.compliance.service.ReviewerGroupService;
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

@RestController
@RequestMapping("/api/tenant/compliance/reviewer-group")
@RequiredArgsConstructor
public class ReviewerGroupController {

    private final ReviewerGroupService reviewerGroupService;

    @RequiresPerm("compliance:reviewergroup:list")
    @GetMapping("/list")
    public Result<List<ShGroup>> list() {
        return Result.success(reviewerGroupService.listAll());
    }

    @RequiresPerm("compliance:reviewergroup:list")
    @GetMapping("/{id}")
    public Result<ShGroup> getById(@PathVariable Long id) {
        return Result.success(reviewerGroupService.getById(id));
    }

    @RequiresPerm("compliance:reviewergroup:create")
    @OperLog(module = "REVIEWER_GROUP", operType = "INSERT", desc = "新增审核组")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody ShGroup group) {
        return Result.success(reviewerGroupService.create(group));
    }

    @RequiresPerm("compliance:reviewergroup:update")
    @OperLog(module = "REVIEWER_GROUP", operType = "UPDATE", desc = "更新审核组")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody ShGroup group) {
        reviewerGroupService.update(group);
        return Result.success();
    }

    @RequiresPerm("compliance:reviewergroup:delete")
    @OperLog(module = "REVIEWER_GROUP", operType = "DELETE", desc = "删除审核组")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reviewerGroupService.delete(id);
        return Result.success();
    }

    @RequiresPerm("compliance:reviewergroup:list")
    @GetMapping("/{id}/members")
    public Result<List<Reviewer>> listMembers(@PathVariable Long id) {
        return Result.success(reviewerGroupService.listMembers(id));
    }
}