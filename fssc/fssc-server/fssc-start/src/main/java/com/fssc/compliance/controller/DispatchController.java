// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.compliance.entity.DispatchTask;
import com.fssc.compliance.service.DispatchService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/compliance/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    @RequiresPerm("compliance:dispatch:run")
    @OperLog(module = "DISPATCH", operType = "INSERT", desc = "执行派单")
    @PostMapping(value = "/run", consumes = "application/json")
    public Result<DispatchTask> dispatch(@RequestBody Map<String, Object> req) {
        String billId = req.get("billId") != null ? String.valueOf(req.get("billId")) : null;
        String billType = req.get("billType") != null ? req.get("billType").toString() : null;
        String expenseType = req.get("expenseType") != null ? req.get("expenseType").toString() : null;
        BigDecimal amount = req.get("amount") != null ? new BigDecimal(req.get("amount").toString()) : null;
        Long legalEntityId = req.get("legalEntityId") != null ? Long.valueOf(req.get("legalEntityId").toString()) : null;
        return Result.success(dispatchService.dispatch(billId, billType, expenseType, amount, legalEntityId));
    }

    @RequiresPerm("compliance:dispatch:assign")
    @OperLog(module = "DISPATCH", operType = "UPDATE", desc = "手动指派审核员")
    @PostMapping("/{taskId}/assign")
    public Result<Void> assign(@PathVariable Long taskId,
                               @RequestParam Long reviewerId) {
        dispatchService.assignManual(taskId, reviewerId);
        return Result.success();
    }

    @RequiresPerm("compliance:dispatch:assign")
    @OperLog(module = "DISPATCH", operType = "UPDATE", desc = "完成派单任务")
    @PostMapping("/{taskId}/complete")
    public Result<Void> complete(@PathVariable Long taskId) {
        dispatchService.completeTask(taskId);
        return Result.success();
    }

    @RequiresPerm("compliance:dispatch:assign")
    @OperLog(module = "DISPATCH", operType = "UPDATE", desc = "升级派单任务")
    @PostMapping(value = "/{taskId}/escalate", consumes = "application/json")
    public Result<Void> escalate(@PathVariable Long taskId,
                                 @RequestBody(required = false) Map<String, String> req) {
        String reason = req != null ? req.get("reason") : null;
        dispatchService.escalateTask(taskId, reason);
        return Result.success();
    }

    @RequiresPerm("compliance:dispatch:list")
    @GetMapping("/by-status")
    public Result<List<DispatchTask>> listByStatus(@RequestParam(required = false) String status) {
        return Result.success(dispatchService.listByStatus(status));
    }

    /** P0-2：派单任务分页（原 /by-status 保留兼容） */
    @RequiresPerm("compliance:dispatch:list")
    @GetMapping("/by-status/page")
    public Result<PageResult<DispatchTask>> pageByStatus(@RequestParam(required = false) String status,
                                                         @RequestParam(defaultValue = "1") long pageNum,
                                                         @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(dispatchService.pageByStatus(status, pageNum, pageSize));
    }

    @RequiresPerm("compliance:dispatch:list")
    @GetMapping("/{taskId}")
    public Result<DispatchTask> getById(@PathVariable Long taskId) {
        return Result.success(dispatchService.getById(taskId));
    }

    @RequiresPerm("compliance:dispatch:list")
    @GetMapping("/my-tasks")
    public Result<List<DispatchTask>> listMyTasks(@RequestParam(required = false) Long reviewerId,
                                                   @RequestParam(required = false) String status) {
        Long effectiveReviewerId = reviewerId;
        if (effectiveReviewerId == null) {
            return Result.success(List.of());
        }
        return Result.success(dispatchService.listMyTasks(effectiveReviewerId, status));
    }

    @RequiresPerm("compliance:dispatch:run")
    @OperLog(module = "DISPATCH", operType = "UPDATE", desc = "自动派单检查")
    @PostMapping("/auto-check")
    public Result<Void> autoCheck() {
        dispatchService.autoDispatchCheck();
        return Result.success();
    }
}