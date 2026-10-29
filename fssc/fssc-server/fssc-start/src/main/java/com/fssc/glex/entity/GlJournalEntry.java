// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 一般总账记账（gl_journal_entry，带 tenant_id）。四类：ACCRUAL 费用预提/DEFER 费用待摊/MANUAL 手工凭证/OTHER_RECEIPT 其他收款。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_journal_entry")
public class GlJournalEntry extends TenantAuditBase {

    /** 记账公司（组织合法性守卫） */
    private Long companyId;
    /** 记账单号 */
    private String entryNo;
    /** ACCRUAL/DEFER/MANUAL/OTHER_RECEIPT */
    private String entryType;
    /** 会计期间 yyyyMM */
    private String period;
    /** 凭证摘要 */
    private String summary;
    /** 借方科目 md_subject.id */
    private Long debitSubjectId;
    /** 贷方科目 md_subject.id */
    private Long creditSubjectId;
    /** 金额 */
    private BigDecimal amount;
    /** DRAFT/POSTED */
    private String status;
    /** 记账凭证号 */
    private String voucherNo;
}