// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 档案附件（archive_attachment）。归档单据关联的附件文件信息。 */
@Data
@TableName("archive_attachment")
public class ArchiveAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 档案ID */
    private Long archiveId;

    /** 文件类型：PDF/IMAGE/DOC */
    private String fileType;

    /** 文件名 */
    private String fileName;

    /** 文件URL地址 */
    private String fileUrl;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 内容哈希值（SHA-256） */
    private String contentHash;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}