// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.budget.entity.BgAdjust;
import com.fssc.budget.entity.BgAdjustLine;
import com.fssc.budget.mapper.BgAdjustMapper;
import com.fssc.budget.service.BudgetService;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 预算调整单（/api/tenant/budget/adjust/**）：增拨/调减/调剂 */
@RestController
@RequestMapping("/api/tenant/budget/adjust")
@RequiredArgsConstructor
public class BudgetAdjustController {

    private final BudgetService budgetService;
    private final BgAdjustMapper bgAdjustMapper;

    @RequiresPerm("budget:adjust:list")
    @GetMapping("/page")
    public Result<PageResult<BgAdjust>> page(com.fssc.common.core.PageQuery query, String keyword) {
        LambdaQueryWrapper<BgAdjust> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(BgAdjust::getAdjustNo, keyword);
        }
        qw.orderByDesc(BgAdjust::getId);
        Page<BgAdjust> p = bgAdjustMapper.selectPage(Page.of(query.getPageNum(), query.getPageSize()), qw);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), query.getPageNum(), query.getPageSize()));
    }

    @RequiresPerm("budget:adjust:create")
    @OperLog(module = "BUDGET", operType = "INSERT", desc = "新建预算调整单")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody AdjustReq req) {
        return Result.success(budgetService.adjustCreate(req.getAdjust(), req.getLines()));
    }

    @RequiresPerm("budget:adjust:list")
    @GetMapping("/lines/{adjustId}")
    public Result<List<BgAdjustLine>> lines(@PathVariable Long adjustId) {
        return Result.success(budgetService.adjustLines(adjustId));
    }

    @RequiresPerm("budget:adjust:create")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "编辑预算调整单（草稿）")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody AdjustReq req) {
        budgetService.adjustUpdate(req.getAdjust(), req.getLines());
        return Result.success();
    }

    @RequiresPerm("budget:adjust:approve")
    @OperLog(module = "BUDGET", operType = "APPROVE", desc = "审批预算调整单")
    @PostMapping("/approve/{id}")
    public Result<Void> approve(@PathVariable Long id) {
        budgetService.adjustApprove(id);
        return Result.success();
    }
    @Data
    public static class AdjustReq {
        private BgAdjust adjust;
        private List<BgAdjustLine> lines;
    }
}
