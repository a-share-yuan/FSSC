// SPDX-License-Identifier: Apache-2.0
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

/** 客户开票规则配置（蓝图附件一 11.2.2）：拆分合并依据，配置即生效。 */
@Data
@TableName("ar_invoice_rule")
public class ArInvoiceRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String customerCode;
    private String customerName;
    /** 专票/普票（同一销售订单不混开） */
    private String invoiceType;
    /** 单张发票限额（默认100万，受托代销1000万） */
    private BigDecimal limitAmount;
    /** 最大明细行数（销货清单受限客户如8） */
    private Integer maxLines;
    private Integer allowSummary;
    /** PO/DELIVERY/LINE_GROUP/SINGLE_LINE */
    private String splitDim;
    /** ORIGINAL/SIMPLIFIED */
    private String specRule;
    /** 备注栏模板 {年月}{地址} */
    private String remarkTemplate;
    /** 是否需商务确认（蓝图功能保留；实际启用还受参数 ar.bizconfirm.enabled 控制） */
    private Integer needBizConfirm;
    /** ENABLED/DISABLED */
    private String status;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String ST_ENABLED = "ENABLED";
}
