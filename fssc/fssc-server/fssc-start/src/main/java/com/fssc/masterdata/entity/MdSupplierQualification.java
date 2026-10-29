// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** md_supplier_qualification 供应商资质证照(N)(41号审查 供应商专属页子表) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_supplier_qualification")
public class MdSupplierQualification extends TenantAuditBase {

    private Long supplierId;
    private String qualName;
    private String qualNo;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String remark;
}
