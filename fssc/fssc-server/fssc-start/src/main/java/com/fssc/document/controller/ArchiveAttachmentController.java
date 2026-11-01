// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.controller;

import com.fssc.common.core.Result;
import com.fssc.document.entity.ArchiveAttachment;
import com.fssc.document.service.ArchiveAttachmentService;
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
@RequestMapping("/api/tenant/compliance/archive-attachment")
@RequiredArgsConstructor
@FsscDataSource("document")
public class ArchiveAttachmentController {

    private final ArchiveAttachmentService archiveAttachmentService;

    @RequiresPerm("compliance:archiveattachment:create")
    @OperLog(module = "ARCHIVE_ATTACHMENT", operType = "INSERT", desc = "新增档案附件")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody Map<String, Object> req) {
        Long archiveId = req.get("archiveId") != null ? Long.valueOf(req.get("archiveId").toString()) : 0L;
        String fileType = req.get("fileType") != null ? req.get("fileType").toString() : "DOCUMENT";
        String fileName = req.get("fileName") != null ? req.get("fileName").toString() : "unknown";
        String fileUrl = req.get("fileUrl") != null ? req.get("fileUrl").toString() : null;
        Long fileSize = req.get("fileSize") != null ? Long.valueOf(req.get("fileSize").toString()) : null;
        String contentHash = req.get("contentHash") != null ? req.get("contentHash").toString() : null;
        return Result.success(archiveAttachmentService.addAttachment(archiveId, fileType, fileName, fileUrl, fileSize, contentHash));
    }

    @RequiresPerm("compliance:archiveattachment:list")
    @GetMapping("/by-archive/{archiveId}")
    public Result<List<ArchiveAttachment>> listByArchive(@PathVariable Long archiveId) {
        return Result.success(archiveAttachmentService.listByArchive(archiveId));
    }

    @RequiresPerm("compliance:archiveattachment:list")
    @GetMapping("/{id}")
    public Result<ArchiveAttachment> getById(@PathVariable Long id) {
        return Result.success(archiveAttachmentService.getById(id));
    }

    @RequiresPerm("compliance:archiveattachment:delete")
    @OperLog(module = "ARCHIVE_ATTACHMENT", operType = "DELETE", desc = "删除档案附件")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        archiveAttachmentService.deleteAttachment(id);
        return Result.success();
    }
}
