// SPDX-License-Identifier: AGPL-3.0-or-later
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

/** 档案销毁审批（archive_destroy_approval）。档案销毁申请与审批记录。 */
@Data
@TableName("archive_destroy_approval")
public class ArchiveDestroyApproval implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 销毁审批编码 */
    private String destroyCode;

    /** 档案ID列表（JSON格式） */
    private String archiveIds;

    /** 销毁原因 */
    private String destroyReason;

    /** 申请人ID */
    private Long applicantId;

    /** 第一审批人ID（双签第一人） */
    private Long approverId;

    /** 第二复核人ID（双签第二人，不得与第一审批人相同） */
    private Long secondApproverId;

    /** 第二复核通过时间 */
    private LocalDateTime secondApprovedAt;

    /** 审批状态：PENDING待签（含已签一人待复核）/APPROVED双签完成/REJECTED已否决 */
    private String approvalStatus;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** 实际销毁时间 */
    private LocalDateTime destroyedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 申请人姓名（nick_name，缺失回退登录号），由 applicant_id 反查（非表列） */
    @TableField(exist = false)
    private String applicantName;

    /** 申请人登录号/工号（非表列）：与姓名并存，不互相替换 */
    @TableField(exist = false)
    private String applicantUserName;
}