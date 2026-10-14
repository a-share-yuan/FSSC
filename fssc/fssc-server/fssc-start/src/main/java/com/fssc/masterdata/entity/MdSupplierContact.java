// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** md_supplier_contact 供应商联系人(N)(41号审查 P1-8 供应商专属页子表) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_supplier_contact")
public class MdSupplierContact extends TenantAuditBase {

    private Long supplierId;
    private String contactName;
    private String position;
    private String phone;
    private String email;
    private Integer isPrimary;
    private String remark;
}
