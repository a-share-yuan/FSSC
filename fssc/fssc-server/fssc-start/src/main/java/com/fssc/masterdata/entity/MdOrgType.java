// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 组织类型（需求 2.30：定义组织的分类类型）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_org_type")
public class MdOrgType extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 类型编码 */
    private String typeCode;

    /** 类型名称 */
    private String typeName;

    /** 排序 */
    private Integer sort;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

}
