// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.payplan.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购付款计划（pay_plan）。
 * <p>对应设计稿「基地采购付款计划申请流程(PAY_PLAN_BASE)/板块采购付款计划申请流程(PAY_PLAN_PLATE)」，
 * 由业务部门/基地采购部经办人在合同/采购订单签订后提交付款计划，经审批后流转付款申请。</p>
 * <p>planType 类型：BASE=基地采购付款计划，PLATE=板块采购付款计划。</p>
 * <p>status 状态：APPROVING=审批中 / APPROVED=已通过 / REJECTED=已驳回。</p>
 */
@Data
@TableName("pay_plan")
public class PayPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 付款计划单号（PB/PP + 时间戳） */
    private String planNo;
    /** 计划类型：BASE=基地采购付款计划 / PLATE=板块采购付款计划 */
    private String planType;
    /** 所属公司（多分子公司审批路由） */
    private Long companyId;
    /** 关联项目（项目内付款计划，可空） */
    private Long projectId;
    /** 供应商 */
    private String supplier;
    /** 供应商档案ID（md_supplier.id）；提交侧选档案带入，取不到为 NULL */
    private Long supplierId;
    /** 付款计划期间 yyyyMM */
    private String period;
    /** 计划付款金额 */
    private BigDecimal amount;
    /** 用途说明 */
    private String purpose;
    /** 经办人用户ID */
    private Long applicantId;
    /** 状态：APPROVING/APPROVED/REJECTED；付款批次核销全额后置 DONE（见 PlanWritebackService，D6） */
    private String status;
    /** 累计已核销金额（付款批次执行成功后由 PlanWritebackService 回写计划已付款） */
    private BigDecimal paidAmount;
    /** 备注 */
    private String remark;
    /** 流程实例ID */
    private String procInstId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}