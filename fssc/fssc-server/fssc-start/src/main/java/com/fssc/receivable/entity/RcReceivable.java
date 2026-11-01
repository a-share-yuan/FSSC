// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.receivable.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收单（rc_receivable，带 tenant_id，逻辑删除 del_flag）。
 * 通常由销售订单（orderId）生成；收款核销按来源冲减余额。
 * 状态机：NONE 部分（未结清）/ DONE 结清 / OVERDUE 逾期。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rc_receivable")
public class RcReceivable extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 单据号 */
    private String billNo;
    /** 往来单位（crm_customer.id） */
    private Long customerId;
    /** 单据日期 */
    private LocalDate billDate;
    /** 来源订单id */
    private Long orderId;
    /** 批次号 */
    private String batchNo;
    /** 币种 */
    private String currencyCode;
    /** 应收金额 */
    private BigDecimal amount;
    /** 已收金额 */
    private BigDecimal receivedAmount;
    /** 应收余额 = 应收金额 - 已收金额 */
    private BigDecimal remainAmount;
    /** 到期日 */
    private LocalDate dueDate;
    /** NONE 部分 / DONE 结清 / OVERDUE 逾期 */
    private String status;
    /** 期间（yyyy-MM 或 yyyyMM） */
    private String period;
    /** 所属公司（多分子公司审批路由） */
    private Long companyId;
    /** 溯源：来源单据类型 SALES_ORDER销售订单/MANUAL手工 */
    private String sourceDocType;
    /** 溯源：来源单据ID */
    private Long sourceDocId;

    // 流程态投影（非表列，只出参、不进 SQL、不改状态机）

    /**
     * 审批流状态（wf_instance.status）：RUNNING／APPROVED／REJECTED／CANCELLED／TERMINATED；
     * 未起流程为 null。
     * <p>本表 status 是<b>收款规则</b>（NONE 未结清／PARTIAL 部分收款／RECEIVED／DONE 结清／OVERDUE 逾期），
     * 不承载审批语义：应收单生成即起 OTC 收款审批流，但审批期间 status 仍是 NONE，
     * 界面直译成"未开始"，看起来像流程没起。
     * 补一个"审批中"单据态要动全仓按 status 过滤的读取点（核销候选、账龄、对账圈单），
     * 风险远大于收益 ⇒ 规则取"台账另列显示流程态"，status 名称表不变。
     */
    @TableField(exist = false)
    private String flowStatus;

    /** 流程态白话：未起流程／审批中／审批通过／已驳回／已撤销／已终止（当前节点与办理人走「流程进度」面板，不塞进本列） */
    @TableField(exist = false)
    private String flowStatusText;
}