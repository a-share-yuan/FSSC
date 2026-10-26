// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.document.entity.ArchiveDestroyApproval;
import com.fssc.document.service.ArchiveDestroyService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresFeature;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.framework.datasource.FsscDataSource;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/tenant/compliance/archive-destroy")
@RequiredArgsConstructor
@FsscDataSource("document")
public class ArchiveDestroyController {

    private final ArchiveDestroyService archiveDestroyService;

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archivedestroy:create")
    @OperLog(module = "ARCHIVE_DESTROY", operType = "INSERT", desc = "创建档案销毁申请")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody Map<String, Object> req) {
        List<Long> archiveIds = req.get("archiveIds") != null ? ((List<Integer>) req.get("archiveIds")).stream().map(Long::valueOf).toList() : List.of();
        String reason = req.get("reason") != null ? req.get("reason").toString() : "";
        Long applicantId = req.get("applicantId") != null ? Long.valueOf(req.get("applicantId").toString()) : 0L;
        return Result.success(archiveDestroyService.createDestroyRequest(archiveIds, reason, applicantId));
    }

    @SuppressWarnings("unchecked")
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archivedestroy:approve")
    @OperLog(module = "ARCHIVE_DESTROY", operType = "UPDATE", desc = "审批档案销毁申请")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestBody Map<String, Object> req) {
        Long approverId = req.get("approverId") != null ? Long.valueOf(req.get("approverId").toString()) : 0L;
        boolean approved = req.get("approved") != null ? Boolean.parseBoolean(req.get("approved").toString()) : false;
        archiveDestroyService.approveDestroy(id, approverId, approved);
        return Result.success();
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archivedestroy:execute")
    @OperLog(module = "ARCHIVE_DESTROY", operType = "DELETE", desc = "执行档案销毁")
    @PostMapping("/{id}/execute")
    public Result<Void> execute(@PathVariable Long id) {
        archiveDestroyService.executeDestroy(id);
        return Result.success();
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archivedestroy:list")
    @GetMapping("/list")
    public Result<List<ArchiveDestroyApproval>> list(@RequestParam(required = false) String approvalStatus) {
        return Result.success(archiveDestroyService.listByStatus(approvalStatus));
    }

    /** P0-2：销毁申请分页（原 /list 保留兼容） */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archivedestroy:list")
    @GetMapping("/list/page")
    public Result<PageResult<ArchiveDestroyApproval>> page(@RequestParam(required = false) String approvalStatus,
                                                           @RequestParam(defaultValue = "1") long pageNum,
                                                           @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(archiveDestroyService.pageByStatus(approvalStatus, pageNum, pageSize));
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archivedestroy:list")
    @GetMapping("/{id}")
    public Result<ArchiveDestroyApproval> getById(@PathVariable Long id) {
        return Result.success(archiveDestroyService.getById(id));
    }
}
