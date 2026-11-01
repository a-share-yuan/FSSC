// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 单据关联（exp_bill_relation，带 tenant_id）。无 del_flag 无 create_by。 */
@Data
@TableName("exp_bill_relation")
public class ExpBillRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long sourceBillId;
    private Long targetBillId;
    /** APPLY_CLAIM事前申请关联报销 / LOAN_CLAIM借款关联报销 */
    private String relationType;
    private BigDecimal useAmount;
    private LocalDateTime createTime;

    /** 展示：源/目标单据号（transient，关系表只存 id，草稿重开与详情抽屉要能认出是哪张单） */
    @TableField(exist = false)
    private String sourceBillNo;
    @TableField(exist = false)
    private String targetBillNo;
}
