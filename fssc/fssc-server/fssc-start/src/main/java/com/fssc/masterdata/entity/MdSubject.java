// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 会计科目（md_subject，带 tenant_id，树形）。company_id 为空表示租户共享科目。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_subject")
public class MdSubject extends TenantAuditBase {

    /** 所属公司（null=租户级共享） */
    private Long companyId;
    private String subjectCode;
    private String subjectName;
    private Long parentId;
    private Integer subjectLevel;
    /** COST费用/ASSET资产/LIABILITY负债/EQUITY权益/REVENUE收入 */
    private String subjectType;
    /** DEBIT借方/CREDIT贷方 */
    private String direction;
    private Integer isLeaf;
    /** 辅助核算项，逗号分隔：company,dept,project */
    private String auxItems;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}
