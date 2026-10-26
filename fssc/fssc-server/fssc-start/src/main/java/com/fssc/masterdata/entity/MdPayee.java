// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 收款人（md_payee，带 tenant_id）。付款对象：员工/供应商/其他。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_payee")
public class MdPayee extends TenantAuditBase {

    /** EMPLOYEE员工/SUPPLIER供应商/OTHER其他 */
    private String payeeType;
    /** 关联ID（员工ID/供应商ID） */
    private Long refId;
    private String payeeName;
    private String bankName;
    private String bankBranch;
    private String bankAccount;
    private String bankCode;
    /** PUBLIC对公/PRIVATE对私 */
    private String accountType;
    private String currencyCode;
    /** 1 默认收款账户 */
    private Integer isDefault;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}
