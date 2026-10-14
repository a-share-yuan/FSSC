// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** md_supplier_bank 供应商银行账户(N)(41号审查 P1-8 供应商专属页子表) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_supplier_bank")
public class MdSupplierBank extends TenantAuditBase {

    private Long supplierId;
    private String bankName;
    private String bankBranch;
    private String bankAccount;
    private String currencyCode;
    private Integer isDefault;
    private String status;
    private String remark;
}
