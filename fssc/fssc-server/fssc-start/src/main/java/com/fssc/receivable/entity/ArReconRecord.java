// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.receivable.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 应收对账 / 分析报告记录（ar_recon_record，OTC04/OTC05，收尾）。
 * <p>
 * AR_RECON / AR_REPORT 原以 bizId=NULL 发起，流程结束不触发收尾事件；
 * 现发起时落一条本记录（RUNNING）并以记录 id 作为流程 bizId，
 * 审批通过/驳回由 ArReconBizAction 翻转状态 → CONFIRMED / REJECTED，实现闭环与可验证。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ar_recon_record")
public class ArReconRecord extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** RECON 应收账款对账 / REPORT 管理分析报告 */
    private String recordType;
    /** 期间（yyyy-MM） */
    private String period;
    /** 往来单位（crm_customer.id） */
    private Long customerId;
    /** 所属公司 */
    private Long companyId;
    /** 对账/报告金额（元） */
    private BigDecimal amount;
    /** RUNNING 审批中 / CONFIRMED 已确认 / REJECTED 已驳回 */
    private String status;
    /** 流程实例ID */
    private String procInstId;
    /** 备注 */
    private String remark;
}
