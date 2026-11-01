// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @NotBlank(message = "账户名称(accountName)不能为空")
    private String accountName;
    @NotBlank(message = "开户银行(bankName)不能为空")
    private String bankName;
    private String bankBranch;
    @NotBlank(message = "银行账号(bankAccount)不能为空")
    @Pattern(regexp = "^\\d{6,30}$", message = "银行账号应为 6-30 位数字")
    private String bankAccount;
    /** 联行号（选填，填了就必须是 12 位数字）；空值放行 */
    @Pattern(regexp = "^(\\d{12})?$", message = "联行号应为 12 位数字")
    private String bankCode;
    private String currencyCode;
    @DecimalMin(value = "0.00", message = "期初余额不能为负")
    private BigDecimal balance;
    /** 账户性质：BASIC 基本存款户/GENERAL 一般存款户/SPECIAL 专用存款户/TEMPORARY 临时存款户 */
    private String accountType;
    /** 关联会计科目（资金科目） */
    private Long subjectId;
    /** 1 默认出账账户 */
    private Integer isDefault;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}
