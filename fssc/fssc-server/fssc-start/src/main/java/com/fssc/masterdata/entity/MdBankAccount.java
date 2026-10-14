// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 公司银行账户（md_bank_account，带 tenant_id，company_id 归属）。付款出账账户。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_bank_account")
public class MdBankAccount extends TenantAuditBase {

    private Long companyId;
    private String accountCode;
    private String accountName;
    private String bankName;
    private String bankBranch;
    private String bankAccount;
    private String bankCode;
    private String currencyCode;
    private BigDecimal balance;
    /** 账户性质（D-DEC-158）：BASIC 基本存款户/GENERAL 一般存款户/SPECIAL 专用存款户/TEMPORARY 临时存款户 */
    private String accountType;
    /** 关联会计科目（资金科目） */
    private Long subjectId;
    /** 1 默认出账账户 */
    private Integer isDefault;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}
