// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预算调整明细（bg_adjust_line，带 tenant_id）。只增不改，无 del_flag。 */
@Data
@TableName("bg_adjust_line")
public class BgAdjustLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long adjustId;
    /** 调入预算明细 */
    private Long budgetItemId;
    /** 调出预算明细（调剂时使用） */
    private Long outItemId;
    private BigDecimal amount;
    private String remark;
    private LocalDateTime createTime;
}
