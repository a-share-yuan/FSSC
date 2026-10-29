// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** md_supplier_bank 供应商银行账户(N)(41号审查 供应商专属页子表) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_supplier_bank")
public class MdSupplierBank extends TenantAuditBase {

    private Long supplierId;
    @NotBlank(message = "开户行(bankName)不能为空")
    private String bankName;
    private String bankBranch;
    @NotBlank(message = "银行账号(bankAccount)不能为空")
    @Pattern(regexp = "^\\d{6,30}$", message = "银行账号应为 6-30 位数字")
    private String bankAccount;
    private String currencyCode;
    private Integer isDefault;
    private String status;
    private String remark;
}
