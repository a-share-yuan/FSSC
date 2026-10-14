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

/** 档案访问日志（archive_access_log）。记录档案的访问/下载/打印行为。 */
@Data
@TableName("archive_access_log")
public class ArchiveAccessLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 档案ID */
    private Long archiveId;

    /** 访问类型：以 {@code ArchiveAccessService.ACCESS_TYPES}（READ/COPY/VIEW）为准，列注释里的 DOWNLOAD/PRINT 无写入点 */
    private String accessType;

    /** 访问原因 */
    private String accessReason;

    /** 申请人ID */
    private Long requesterId;

    /** 审批人ID */
    private Long approverId;

    /** 审批状态：PENDING/APPROVED/REJECTED */
    private String approvalStatus;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** 实际访问时间 */
    private LocalDateTime accessedAt;

    /** IP地址 */
    private String ipAddress;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 申请人姓名（sys_user.nick_name，缺失回退登录号），由 requester_id 反查（非表列）。
     * RA-D248b：调阅列表「申请人」列此前只拿到裸 ID，前端本地 join 出来的是工号。
     */
    @TableField(exist = false)
    private String requesterName;

    /** 申请人登录号/工号（sys_user.user_name，非表列）：与姓名并存，不互相替换 */
    @TableField(exist = false)
    private String requesterUserName;
}