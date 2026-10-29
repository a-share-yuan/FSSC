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
import java.util.List;

/** 采购订单（po_order：三单匹配的订单侧） */
@Data
@TableName("po_order")
public class PoOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String poNo;
    /** 关联合同（框架协议，可选） */
    private Long contractId;
    private Long supplierId;
    private String supplierName;
    private LocalDate orderDate;
    private BigDecimal amount;
    /** 累计收货金额（收货单联动回写） */
    private BigDecimal receivedAmount;
    /** 币种（CNY/USD/EUR等） */
    private String currency;
    /** 预计交货日期 */
    private LocalDate deliveryDate;
    /** 付款条件（如：30天净额、预付30%货到付款等） */
    private String paymentTerms;
    /** 采购员 */
    private String buyerName;
    /** 联系电话 */
    private String contactPhone;
    /** 交货地址 */
    private String deliveryAddress;
    private String remark;
    /** OPEN执行中/APPROVED已审批/PAID已付款/CLOSED已关闭/ARCHIVED已归档 */
    private String status;
    /** 所属公司（多分子公司审批路由） */
    private Long companyId;
    /** 资本化标识：1=固定资产投资(收货即转固建卡) 0=普通采购 */
    private Integer capitalFlag;
    /**  关联在建工程项目（md_cip.id，可空；收货/付款成本自动归集到该 CIP） */
    private Long cipId;
    private Long createBy;
    private LocalDateTime createTime;
    /** 裸 update（new PoOrder()+updateById）也须盖审计戳：不标 fill 则 MP 不纳入该列，update_by 恒 NULL */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Integer delFlag;

    /**
     * 行项目（po_order_line）。前端一直提交 lines，本实体若没有该字段 ⇒ 反序列化时整块丢弃
     * （见 sql/V1.4.47_po_order_line_seed.sql 头部），故必须显式接收。
     * null = 调用方未带行（不动既有行）；空集合 = 明确清空。
     */
    @TableField(exist = false)
    private List<PoOrderLine> lines;
}
