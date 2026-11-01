// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** GL 主数据维护（gl_shared_master，带 tenant_id）。科目/成本中心/银行等主数据变更申请。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_shared_master")
public class GlSharedMaster extends TenantAuditBase {

    /** 维护申请单号 */
    private String reqNo;
    /** SUBJECT科目/COST_CENTER成本中心/BANK银行/OTHER其他 */
    private String type;
    /** 会计期间 yyyyMM */
    private String period;
    /** 关联科目 md_subject.id */
    private Long subjectId;
    /** 科目编码 */
    private String subjectCode;
    /** 科目名称 */
    private String subjectName;
    /** ADD/MODIFY/DELETE */
    private String action;
    /** 变更说明 */
    private String description;
    /** DRAFT/APPROVED */
    private String status;
    /** 记账凭证号 */
    private String voucherNo;
}