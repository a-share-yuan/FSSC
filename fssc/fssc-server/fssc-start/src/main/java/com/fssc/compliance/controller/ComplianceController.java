// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.compliance.entity.ExpensePolicy;
import com.fssc.compliance.service.PrecheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 第一阶段合规补全：费用政策管理 + 合规预检。 */
@RestController
@RequestMapping("/api/tenant/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final PrecheckService precheckService;

    // 费用政策管理
    @RequiresPerm("policy:list")
    @GetMapping("/policy/page")
    public Result<PageResult<ExpensePolicy>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                  @RequestParam(defaultValue = "10") long pageSize,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status) {
        return Result.success(precheckService.page(pageNum, pageSize, keyword, status));
    }

    @RequiresPerm("policy:list")
    @PostMapping("/policy/create")
    public Result<Long> create(@RequestBody ExpensePolicy p) {
        return Result.success(precheckService.create(p));
    }

    @RequiresPerm("policy:list")
    @PostMapping("/policy/update")
    public Result<Void> update(@RequestBody ExpensePolicy p) {
        precheckService.update(p);
        return Result.success();
    }

    // 合规预检（提交前/提交时）
    @RequiresPerm("exp:bill:submit")
    @GetMapping("/precheck")
    public Result<List<PrecheckService.PrecheckItem>> precheck(@RequestParam Long billId) {
        return Result.success(precheckService.precheck(billId));
    }
}
