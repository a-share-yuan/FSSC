// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.entity;

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
 * AR服务申请单（蓝图附件二 11.3，10类统一状态机）：
 * 索赔/向客户索赔/收入暂估/事物调整/应收应付对冲/其它业务收入/支出/对冲/订单解挂/汇票贴息。
 * 科目默认（附件五）与厂别组织映射（附件四：厂别00不做事业部凭证、2400/2500跨账套折本位币）在创建时落地。
 */
@Data
@TableName("ar_claim")
public class ArClaim implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String billNo;
    private Long companyId;
    private String companyCode;
    private String companyName;
    /** CLAIM_IN/CLAIM_OUT/REVENUE_EST/THING_ADJ/AR_AP_OFFSET/OTHER_REV/OTHER_EXP/OTHER_OFFSET/ORDER_UNFRZ/NOTE_DISCOUNT */
    private String claimType;
    private String customerCode;
    private String customerName;
    private String ccy;
    private BigDecimal fxRate;
    private BigDecimal amount;
    /** 成本金额（向客户索赔） */
    private BigDecimal costAmount;
    private BigDecimal taxRate;
    /** 总部事务处理金额（原币） */
    private BigDecimal headquartersAmount;
    /** 事业部事务处理金额（跨账套折本位币；厂别00不生成） */
    private BigDecimal buAmount;
    private String debitAccount;
    private String creditAccount;
    private String taxAccount;
    /** 其它业务对冲借贷金额（必须平衡） */
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String reason;
    private String ekpNo;
    /** 厂别（附件四） */
    private String factoryCode;
    private String orderNo;
    /** 解冻结果（ERP返回） */
    private String unfreezeResult;
    /** 暂估冲回 1是 */
    private Integer reverseFlag;
    /** EKP_REVIEW/PENDING_DISPATCH/DISPATCHED/POSTED/REJECTED/COMPLETED */
    private String status;
    private String handler;
    /** 选单池（蓝图5.2：同一岗位多人派组长/组内抢单） */
    private String handlerPool;
    /** AR组长（SLA升级） */
    private String groupLeader;
    /** 审核/派工记录 */
    private String modifyNote;
    private String voucherNo;
    /** Flowable流程实例ID（EKP_REVIEW审批流，任务2补强接线） */
    private String procInstId;
    private String linesJson;
    private String attachmentRef;
    private String remark;
    private LocalDateTime dispatchTime;
    private LocalDateTime postTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String T_CLAIM_IN = "CLAIM_IN";
    public static final String T_CLAIM_OUT = "CLAIM_OUT";
    public static final String T_REVENUE_EST = "REVENUE_EST";
    public static final String T_THING_ADJ = "THING_ADJ";
    public static final String T_AR_AP_OFFSET = "AR_AP_OFFSET";
    public static final String T_OTHER_REV = "OTHER_REV";
    public static final String T_OTHER_EXP = "OTHER_EXP";
    public static final String T_OTHER_OFFSET = "OTHER_OFFSET";
    public static final String T_ORDER_UNFRZ = "ORDER_UNFRZ";
    public static final String T_NOTE_DISCOUNT = "NOTE_DISCOUNT";

    public static final String ST_EKP_REVIEW = "EKP_REVIEW";
    public static final String ST_PENDING_DISPATCH = "PENDING_DISPATCH";
    public static final String ST_DISPATCHED = "DISPATCHED";
    public static final String ST_POSTED = "POSTED";
    public static final String ST_REJECTED = "REJECTED";
    public static final String ST_COMPLETED = "COMPLETED";
}
