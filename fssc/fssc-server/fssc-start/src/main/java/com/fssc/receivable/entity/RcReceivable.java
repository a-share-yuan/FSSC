package com.fssc.receivable.entity;

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
    /** 所属公司（多分子公司审批路由 V1.3.17） */
    private Long companyId;
    /** P0-4 溯源：来源单据类型 SALES_ORDER销售订单/MANUAL手工 */
    private String sourceDocType;
    /** P0-4 溯源：来源单据ID */
    private Long sourceDocId;
}