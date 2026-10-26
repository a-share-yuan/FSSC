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

/**
 * 核销记录（蓝图核销查询页 + 状态机4.3）：收款/票据/对冲三类核销的执行记录，
 * 关联生成的收款核销单号与凭证号。
 */
@Data
@TableName("ar_writeoff_record")
public class ArWriteoffRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String recordNo;
    /** RECEIPT收款核销/NOTE票据核销/OFFSET对冲核销 */
    private String writeoffType;
    private Long fundId;
    private String fundNo;
    private Long customerId;
    private String customerName;
    /** 核销应收单号（逗号分隔） */
    private String invoiceNos;
    private BigDecimal writeoffAmount;
    /** 1=自动核销(FIFO) */
    private Integer autoFlag;
    private String receiptNo;
    private String voucherNo;
    /** PENDING待核销/DONE已完成/FAILED已失败 */
    private String status;
    private String failReason;
    private String operator;
    private String note;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String T_RECEIPT = "RECEIPT";
    public static final String T_NOTE = "NOTE";
    public static final String T_OFFSET = "OFFSET";

    public static final String ST_PENDING = "PENDING";
    public static final String ST_DONE = "DONE";
    public static final String ST_FAILED = "FAILED";
}
