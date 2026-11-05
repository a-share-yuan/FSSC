// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 违规原因字典（信用扣分用，md_violation_reason）。
 *
 * <p>把「扣多少分」从代码里的两个写死数字（驳回 -15 / 质检不合格 -20）变成可维护的数据：
 * 管理口径调整只改这张表，不发版。
 *
 * <p><b>只收「行为类」违规</b>——电子发票（数电票）时代，凡是系统能判的都不该记成员工违规：
 * 发票重复报销由预检硬拦截（{@code INVOICE_DUP}）、真伪由 {@code verifyStatus} + 异常巡检、
 * 作废红冲有 {@code VOID}/{@code RED_FLUSHED} 状态。把这类问题计入信用扣分，
 * 等于让员工替系统缺陷背锅。纸质票时代的「粘贴单据规范」随实物粘贴一并废弃。
 *
 * <p>预置 6 条：超期报销 5 / 费用归属错误 5 / 非发票类附件缺失 5 / 票面与业务不符 10 /
 * 拆分报销规避审批 20 / 虚假业务 30。
 *
 * <p><b>扣分是正数</b>（表示扣多少），使用时取负。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_violation_reason")
public class MdViolationReason extends TenantAuditBase {

    /** 原因码（租户内唯一，如 VR_TRUTH） */
    private String reasonCode;

    /** 原因名称（展示用） */
    private String reasonName;

    /** 分类：DOC 原始单据 / FORM 填写规范 / PASTE 粘贴规范 / TRUTH 真实性 */
    private String reasonCategory;

    /** 扣分（正数） */
    private Integer deductPoints;

    /** 严重度：LOW/MEDIUM/HIGH */
    private String severity;

    private Integer sort;

    /** NORMAL/DISABLED */
    private String status;

    private String remark;
}
