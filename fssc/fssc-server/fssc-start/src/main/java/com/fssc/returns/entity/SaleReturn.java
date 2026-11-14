package com.fssc.returns.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售退货单（AR 退货退款冲减应收）。
 * 销售退货单模型：金额表内存正数（对账规则由 BCP 适配层转负），
 * 状态机 DRAFT→PENDING→APPROVED→VOIDED，退货方式 REFUND（进对账）/EXCHANGE（换货补货，不进对账）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_return")
public class SaleReturn extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 销售退货单号（XT 前缀，sys_number_rule 取号） */
    private String billNo;
    private Long companyId;
    private String companyCode;
    private String companyName;
    /** 客户代码（与 bcp_partner.partner_code 对齐，对齐 ar_pre_invoice 范式） */
    private String customerCode;
    private String customerName;
    private String ccy;
    /** 业务日期（退货日期，对账 receiptDate） */
    private LocalDate billDate;
    /** 含税退货总额（恒正数，对账接口按负数返回） */
    private BigDecimal amount;
    /** 税额合计 */
    private BigDecimal taxAmount;
    /** REFUND 退货退款（进对账）/ EXCHANGE 退货补货（不进对账） */
    private String returnMode;
    /** 源单类型：CCP_SHIPPING 发运单 / SALES_ORDER 销售订单 / MANUAL 手工 */
    private String sourceDocType;
    private Long sourceDocId;
    private String sourceBillNo;
    /** 退货原因：质量不合格/破损/错发/客户取消/合同终止/其他 */
    private String returnReason;
    /** 关联红字发票号（二期红字联动，预留） */
    private String redInvoiceNo;
    private Integer lineCount;
    private String status;
    private String approver;
    private LocalDateTime approveTime;
    private String voidReason;
    private String remark;

    /** 明细行（仅用于接口传输，不入库） */
    @TableField(exist = false)
    private List<SaleReturnLine> lines;

    // 状态常量
    public static final String ST_DRAFT = "DRAFT";
    public static final String ST_PENDING = "PENDING";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_REJECTED = "REJECTED";
    public static final String ST_VOIDED = "VOIDED";

    // 退货方式常量
    public static final String MODE_REFUND = "REFUND";
    public static final String MODE_EXCHANGE = "EXCHANGE";
}
