// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.apflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AP付款服务申请单（应收应付蓝图 SCC_AP_15~19 落地）：
 * 预付款/预付款核销/计划内付款/关联方付款/紧急付款 五类统一状态机，表单字段对齐蓝图附录三。
 */
@Data
@TableName("ap_service_request")
public class ApServiceRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 服务申请单号（前缀按类型：YF/HX/JH/GL/JJ） */
    private String billNo;
    /** PREPAY/PREPAY_WRITEOFF/PLAN_PAYMENT/RELATED_PAYMENT/URGENT_PAYMENT */
    private String reqType;
    private Long companyId;
    /** 公司代码（购方） */
    private String companyCode;
    private String companyName;
    /** 事业部 */
    private String sbu;
    /** 供应商代码（销方） */
    private String vendorCode;
    private String vendorName;
    /** 供应商地点 */
    private String vendorLocale;
    private String ccy;
    private BigDecimal exchangeRate;
    /** 付款类型 快速/人工/退款 */
    private String paymentType;
    /** 付款方法 支票/银行转账/应付票据（派工维度） */
    private String paymentWay;
    private String bankNo;
    private LocalDateTime paymentDate;
    private BigDecimal amount;
    /** 税额（预付款强制0） */
    private BigDecimal taxAmount;
    /** 折扣额（一票一折扣，默认0） */
    private BigDecimal rebateAmount;
    /** 汇票到期日期（应付票据必填） */
    private LocalDate expireDate;
    /** 付款额（背书付款=发票合计强校验） */
    private BigDecimal paymentAmount;
    private String paymentConfig;
    /** PO编号（预付款必填） */
    private String poNo;
    /** 发票编号（XXXXXX01-10 连续 / 逗号非连续） */
    private String invoiceNo;
    /** OA流水号（参考） */
    private String ekpNo;
    /** 付款计划表单号 */
    private String planTableNo;
    /** 状态 PENDING_DISPATCH/DISPATCHED/GROUP_REVIEW/POSTED/PAID/VOIDED/COMPLETED */
    private String status;
    /** 办理人 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String handler;
    /** 选单池（多负责人逗号分隔） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String handlerPool;
    /** AP组长 */
    private String groupLeader;
    /** 确认时是否修改 1是（修改付款方式转审） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer modifiedFlag;
    /** 修改/审核说明（记录） */
    private String modifyNote;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String voidReason;
    /** ERP付款凭证号 */
    private String voucherNo;
    /** 资金付款凭证号 */
    private String payVoucherNo;
    private String remark;
    private LocalDateTime dispatchTime;
    private LocalDateTime confirmTime;
    private LocalDateTime postTime;
    private LocalDateTime payTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String ST_PENDING_DISPATCH = "PENDING_DISPATCH";
    public static final String ST_DISPATCHED = "DISPATCHED";
    public static final String ST_GROUP_REVIEW = "GROUP_REVIEW";
    public static final String ST_POSTED = "POSTED";
    public static final String ST_PAID = "PAID";
    public static final String ST_VOIDED = "VOIDED";
    public static final String ST_COMPLETED = "COMPLETED";

    public static final String TYPE_PREPAY = "PREPAY";
    public static final String TYPE_PREPAY_WRITEOFF = "PREPAY_WRITEOFF";
    public static final String TYPE_PLAN_PAYMENT = "PLAN_PAYMENT";
    public static final String TYPE_RELATED_PAYMENT = "RELATED_PAYMENT";
    public static final String TYPE_URGENT_PAYMENT = "URGENT_PAYMENT";

    /** 单号前缀：预付款YF/核销HX/计划内JH/关联方GL/紧急JJ */
    public static String prefixOf(String reqType) {
        switch (reqType == null ? "" : reqType) {
            case TYPE_PREPAY: return "YF";
            case TYPE_PREPAY_WRITEOFF: return "HX";
            case TYPE_PLAN_PAYMENT: return "JH";
            case TYPE_RELATED_PAYMENT: return "GL";
            case TYPE_URGENT_PAYMENT: return "JJ";
            default: return "SR";
        }
    }
}
