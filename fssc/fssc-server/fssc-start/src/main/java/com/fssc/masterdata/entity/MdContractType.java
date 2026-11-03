// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 合同类型（PO/非PO 合同分类）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_contract_type")
public class MdContractType extends TenantAuditBase {
    private static final long serialVersionUID = 1L;
    /** 类型编码 */
    private String typeCode;
    /** 类型名称 */
    private String typeName;
    /** PO/非PO 区分: PO 采购订单 / NONPO 费用类 */
    private String isPo;
    /** 状态 NORMAL/DISABLED */
    private String status;
    /** 备注 */
    private String remark;
}
