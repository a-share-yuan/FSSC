// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bank.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 银行账户余额快照（bank_balance，带 tenant_id）。
 * 银企对账·日终/月终结存，产出未达账项调节表；同一账户同一日期唯一（uk_tenant_account_date）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bank_balance")
public class BankBalance extends TenantAuditBase {

    private Long accountId;
    /** 银行账号快照 */
    private String accountNo;
    /** 余额日期 */
    private LocalDate balanceDate;
    /** 账户余额 */
    private BigDecimal balance;
    /** SAVED已存 / CHECKED已对账 */
    private String reconStatus;
    private String remark;
}