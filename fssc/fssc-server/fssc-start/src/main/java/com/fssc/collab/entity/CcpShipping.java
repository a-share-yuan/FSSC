package com.fssc.collab.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
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
 * CCP 发运/应收明细数据（AR 蓝图接口1：ERP发运数据推送，DB视图定时抓取）：
 * 客户对账确认后商务助理导入对账单，本表承接 ERP 侧发运/系统发票数据，
 * 与对账单按金额/税额合计核对（容差可配，ar.recon.tolerance-*）。
 */
@Data
@TableName("collab_ccp_shipping")
public class CcpShipping implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 发运单号 */
    private String shippingNo;
    /** ERP系统发票号 */
    private String soInvoiceNo;
    private String customerCode;
    private String customerName;
    private String ccy;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private LocalDate shippingDate;
    /** 关联客户对账单号（ar_statement.bill_no） */
    private String statementNo;
    /** RECEIVED 已接收 / CONSUMED 已被对账消耗 */
    private String status;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String ST_RECEIVED = "RECEIVED";
    public static final String ST_CONSUMED = "CONSUMED";
}
