// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.document.entity.ArchiveAccessLog;
import com.fssc.document.service.ArchiveAccessService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.framework.datasource.FsscDataSource;
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
@RequestMapping("/api/tenant/compliance/archive-access")
@RequiredArgsConstructor
@FsscDataSource("document")
public class ArchiveAccessController {

    private final ArchiveAccessService archiveAccessService;

    @RequiresPerm("compliance:archiveaccess:request")
    @OperLog(module = "ARCHIVE_ACCESS", operType = "INSERT", desc = "申请档案调阅")
    @PostMapping("/request")
    public Result<Long> request(@RequestBody Map<String, Object> req) {
        Long archiveId = req.get("archiveId") != null ? Long.valueOf(req.get("archiveId").toString()) : 0L;
        String accessType = req.get("accessType") != null ? req.get("accessType").toString() : "READ";
        String reason = req.get("reason") != null ? req.get("reason").toString() : null;
        Long requesterId = req.get("requesterId") != null ? Long.valueOf(req.get("requesterId").toString()) : 0L;
        return Result.success(archiveAccessService.requestAccess(archiveId, accessType, reason, requesterId));
    }

    @RequiresPerm("compliance:archiveaccess:approve")
    @OperLog(module = "ARCHIVE_ACCESS", operType = "UPDATE", desc = "审批调阅申请")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestBody Map<String, Object> req) {
        Long approverId = req.get("approverId") != null ? Long.valueOf(req.get("approverId").toString()) : 0L;
        boolean approved = req.get("approved") != null ? Boolean.parseBoolean(req.get("approved").toString()) : false;
        archiveAccessService.approveAccess(id, approverId, approved);
        return Result.success();
    }

    @RequiresPerm("compliance:archiveaccess:approve")
    @OperLog(module = "ARCHIVE_ACCESS", operType = "UPDATE", desc = "授予档案访问")
    @PostMapping("/{id}/grant")
    public Result<Void> grant(@PathVariable Long id) {
        archiveAccessService.grantAccess(id);
        return Result.success();
    }

    @RequiresPerm("compliance:archiveaccess:list")
    @GetMapping("/by-archive/{archiveId}")
    public Result<List<ArchiveAccessLog>> listByArchive(@PathVariable Long archiveId) {
        return Result.success(archiveAccessService.listByArchive(archiveId));
    }

    @RequiresPerm("compliance:archiveaccess:list")
    @GetMapping("/my-requests")
    public Result<List<ArchiveAccessLog>> listMyRequests(@RequestParam(required = false) Long requesterId) {
        Long effectiveRequesterId = requesterId;
        if (effectiveRequesterId == null) {
            return Result.success(List.of());
        }
        return Result.success(archiveAccessService.listMyRequests(effectiveRequesterId));
    }

    @RequiresPerm("compliance:archiveaccess:list")
    @GetMapping("/pending")
    public Result<List<ArchiveAccessLog>> listPending() {
        return Result.success(archiveAccessService.listPendingApproval());
    }

    /**
     * 调阅申请分页（原 /pending 保留兼容）。
     * 加 status 入参（ALL=全部记录，PENDING/APPROVED/REJECTED=按状态）；
     * 默认值仍是 PENDING ⇒ 未显式传参的老调用方行为不变。
     */
    @RequiresPerm("compliance:archiveaccess:list")
    @GetMapping("/pending/page")
    public Result<PageResult<ArchiveAccessLog>> pagePending(@RequestParam(defaultValue = "1") long pageNum,
                                                            @RequestParam(defaultValue = "20") long pageSize,
                                                            @RequestParam(required = false, defaultValue = "PENDING") String status) {
        return Result.success(archiveAccessService.pageApproval(status, pageNum, pageSize));
    }

    @RequiresPerm("compliance:archiveaccess:list")
    @GetMapping("/{id}")
    public Result<ArchiveAccessLog> getById(@PathVariable Long id) {
        return Result.success(archiveAccessService.getById(id));
    }

    @RequiresPerm("compliance:archiveaccess:delete")
    @OperLog(module = "ARCHIVE_ACCESS", operType = "DELETE", desc = "删除调阅申请")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        archiveAccessService.delete(id);
        return Result.success();
    }
}