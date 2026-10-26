// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.purchase.entity;

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

/** 收货单（po_receipt：三单匹配的收货侧） */
@Data
@TableName("po_receipt")
public class PoReceipt implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String receiptNo;
    private Long poId;
    private String poNo;
    private Long supplierId;
    private String supplierName;
    private LocalDate receiptDate;
    private BigDecimal amount;
    /** 累计已开票金额（发票关联回写） */
    private BigDecimal invoiceAmount;
    /** 收货人 */
    private String receiverName;
    /** 收货仓库 */
    private String warehouse;
    /** 质检状态（PENDING待检/PASSED合格/FAILED不合格） */
    private String qualityStatus;
    /** 送货单号 */
    private String deliveryNote;
    /**  关联在建工程项目（md_cip.id，从采购订单继承；收货成本自动归集到该 CIP） */
    private Long cipId;
    private String remark;
    /** RECEIVED已收货/INVOICED已开票/CLOSED已关闭 */
    private String status;
    private Long createBy;
    private LocalDateTime createTime;
    /** 裸 update（new PoReceipt()+updateById）也须盖审计戳：不标 fill 则 MP 不纳入该列，update_by 恒 NULL */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Integer delFlag;
}
