// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报销单据主表（exp_bill，带 tenant_id）。五类共用：
 * APPLY 事前申请 / CLAIM 费用报销 / LOAN 借款 / REPAY 还款 / PAY 付款。
 * ADR-6：统一单据主表，差异化走 bill_sub_type + ext_json。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_bill")
public class ExpBill extends TenantAuditBase {

    private String billNo;
    /** APPLY/CLAIM/LOAN/REPAY/PAY */
    private String billType;
    private String billSubType;
    private String title;
    /** 费用承担单位（四维度之一） */
    private Long companyId;
    /** 结算(报销)单位：跨组织场景下与费用承担单位分离（跨组织四维度） */
    private Long settleCompanyId;
    /** 支付单位：实际付款主体（跨组织四维度） */
    private Long payCompanyId;
    /** 借款单位：跨组织借款时款项借入的公司（#18 四分离·借款维，默认同费用承担单位） */
    private Long loanCompanyId;
    private Long deptId;
    private Long costCenterId;
    private Long projectId;
    private Long applyUserId;
    private String applyUserName;
    /**
     * 代理人（代他人报销，代填单）。本列由 ExpenseBillService 保存/提交环节显式落值——
     * 代理人恒等于填单登录人，且与报销人（apply_user_id）不同人。
     */
    private Long agentUserId;
    /** 共享审核人（AUDIT 环节完成经办人） */
    private Long auditUserId;
    /** 收款人：代理报销场景下报销人≠收款人（张三填、李四报、赵五收），款项付给本字段 */
    private Long receiverUserId;
    private LocalDate applyDate;
    private Integer periodYear;
    private Integer periodMonth;
    private String currencyCode;
    /** 录入规则：TAX_IN含税录入(默认)/TAX_EX不含税录入（#9；行未指定时继承表头） */
    private String priceType;
    private BigDecimal exchangeRate;
    private BigDecimal totalAmount;
    /** 金额大写（中文大写，打印/OA 用；新增，随 totalAmount 写入数据库） */
    private String amountCn;
    private BigDecimal taxAmount;
    private BigDecimal netAmount;
    /** 审批核定金额（核定报销） */
    private BigDecimal approvedAmount;
    /** 核定付款金额：≤核定报销，付款控制规则（核定三层） */
    private BigDecimal approvedPayAmount;
    /** 核定退款金额：=核定报销 − 核定付款（核定三层） */
    private BigDecimal approvedRefundAmount;
    /** 借款冲抵金额 */
    private BigDecimal offsetAmount;
    /** 应付金额 */
    private BigDecimal payableAmount;
    private BigDecimal paidAmount;
    /** 关键字段校验和（SHA-256，提交时生成；金额防篡改） */
    private String amountChecksum;
    /** 借款余额（借款单用） */
    private BigDecimal loanBalance;
    private LocalDate expectRepayDate;
    /** DRAFT/SUBMITTED/APPROVING/APPROVED/PAID/REJECTED/WITHDRAWN/CLOSED/VOID */
    private String status;
    /** Flowable 流程实例ID */
    private String procInstId;
    private LocalDateTime submitTime;
    /**
     * 审批通过时刻（TRC-E / D-TRC 批次：列自建表即存在但全库 0 行赋值，
     * 由 ExpenseWfEventListener 在 status→APPROVED 时写入）。
     */
    private LocalDateTime approveTime;
    /** 支付完成时刻，由 PayBatchService.writeBackPayment / ExpenseBillService.markPaid 写入（TRC-E） */
    private LocalDateTime payTime;
    /** 共享中心 AUDIT 环节完成时刻，由 SharingService 写入（TRC-E；语义=共享审核，非审批） */
    private LocalDateTime auditTime;
    private String auditRemark;
    /** NONE/PARTIAL/COMPLETE */
    private String imageStatus;
    /** UNARCHIVED未归档/ARCHIVED已归档（档案归档） */
    private String archiveStatus;
    private LocalDateTime archiveTime;
    private Long archiveBy;
    private String voucherNo;
    private LocalDate voucherDate;
    private Integer urgent;
    /** 单据渠道。写入数据库白名单只有 PC / H5 两值（校验点见 ExpenseBillService#normalizeBillSource）；
     *  APP 为原生端预留码，当前全仓零产生方，传入会被回落成 PC——开通 APP 端时要连同该白名单与列表筛选一起扩。 */
    private String source;
    /** 以下为查询回填字段（不写入数据库）：当前流程名 + 当前节点（单据流程感知） */
    @TableField(exist = false)
    private String flowName;
    @TableField(exist = false)
    private String currentNode;
    /** 查询回填字段（不写入数据库）：是否已被关联核销（机票查询页：exp_bill_relation 中出现即已核销） */
    @TableField(exist = false)
    private Boolean relationUsed;
    private Long sourceBillId;
    /** 收款方名称（对公支付/付款单） */
    private String payeeName;
    /** 收款对象：EMPLOYEE员工/SUPPLIER供应商/CUSTOMER客户——驱动收款账户必填校验（防付错） */
    private String payTarget;
    /** 收款账号：按 payTarget 驱动必填（员工→个人卡，供应商/客户→对公卡） */
    private String payeeAccountNo;
    /** 关联往来单位（crm_customer） */
    private Long customerId;
    /** 关联合同（cont_contract，框架协议） */
    private Long contractId;
    /** 关联收货单（po_receipt，三单匹配） */
    private Long poReceiptId;
    /** 关联采购订单（po_order，三单匹配） */
    private Long poId;
    private String remark;
    private String extJson;

    /** 驳回类型：THROW_BACK 漏单驳回 / GRADE_BY_GRADE 逐级驳回（仅被驳回时置位） */
    private String rejectType;
    /** 无纸化标志：Y 无纸化（免实物）/ N 需实物 */
    private String paperless;
    /** 实物流签收状态：NOT_REQUIRED 无需（无纸化）/ UNSIGNED 待签收 / SIGNED 已签收 */
    private String deliveryStatus;
}
