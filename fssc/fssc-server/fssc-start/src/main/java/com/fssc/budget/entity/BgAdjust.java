// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 预算调整单（bg_adjust，带 tenant_id）。ADD增拨/CUT调减/TRANSFER调剂。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bg_adjust")
public class BgAdjust extends TenantAuditBase {

    private String adjustNo;
    private Long schemeId;
    /** ADD增拨 / CUT调减 / TRANSFER调剂 */
    private String adjustType;
    private Long applyUserId;
    private Long applyDeptId;
    private LocalDate applyDate;
    private String reason;
    /** DRAFT草稿 / APPROVED已审批 / REJECTED已驳回 */
    private String status;
    /** Flowable 流程实例ID（报销主线接入后使用） */
    private String procInstId;
}
