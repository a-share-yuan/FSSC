// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.document.entity.ArchiveAttachment;
import com.fssc.document.mapper.ArchiveAttachmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveAttachmentService {

    private final ArchiveAttachmentMapper archiveAttachmentMapper;

    @Transactional(rollbackFor = Exception.class)
    public Long addAttachment(Long archiveId, String fileType, String fileName,
                               String fileUrl, Long fileSize, String contentHash) {
        ArchiveAttachment attachment = new ArchiveAttachment();
        attachment.setArchiveId(archiveId);
        attachment.setFileType(fileType);
        attachment.setFileName(fileName);
        attachment.setFileUrl(fileUrl);
        attachment.setFileSize(fileSize);
        attachment.setContentHash(contentHash);
        archiveAttachmentMapper.insert(attachment);
        log.info("添加档案附件: archiveId={}, attachmentId={}, fileName={}",
                archiveId, attachment.getId(), fileName);
        return attachment.getId();
    }

    public List<ArchiveAttachment> listByArchive(Long archiveId) {
        LambdaQueryWrapper<ArchiveAttachment> qw = new LambdaQueryWrapper<>();
        qw.eq(ArchiveAttachment::getArchiveId, archiveId)
                .orderByDesc(ArchiveAttachment::getCreateTime);
        return archiveAttachmentMapper.selectList(qw);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAttachment(Long attachmentId) {
        ArchiveAttachment attachment = archiveAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "附件不存在: " + attachmentId);
        }
        archiveAttachmentMapper.deleteById(attachmentId);
        log.info("删除档案附件: attachmentId={}", attachmentId);
    }

    public ArchiveAttachment getById(Long id) {
        ArchiveAttachment attachment = archiveAttachmentMapper.selectById(id);
        if (attachment == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "附件不存在: " + id);
        }
        return attachment;
    }
}