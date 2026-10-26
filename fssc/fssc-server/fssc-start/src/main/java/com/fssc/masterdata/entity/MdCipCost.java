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
 * 在建工程成本归集明细（md_cip_cost）。
 * 记录在建工程累计发生的购建成本（领料/设备/人工/待摊/资本化利息），是转固进固定资产的原值来源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_cip_cost")
public class MdCipCost extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 在建工程ID（md_cip.id） */
    private Long cipId;

    /** 归集类型：MATERIAL领料 / EQUIPMENT设备 / LABOR人工 / DEFERRED待摊 / INTEREST资本化利息 */
    private String costType;

    /** 发生金额 */
    private BigDecimal amount;

    /** 发生日期 */
    private LocalDate occurDate;

    /** 说明 */
    private String description;

    /** 附件（预留） */
    private String attachment;
}