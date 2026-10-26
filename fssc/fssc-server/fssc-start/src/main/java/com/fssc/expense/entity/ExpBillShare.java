// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用分摊（exp_bill_share，带 tenant_id）。
 * 跨部门/公司/项目分摊。无 create_by/update_by，按真实列独立声明。
 */
@Data
@TableName("exp_bill_share")
public class ExpBillShare implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long billId;
    /** 费用行ID，空表示整单分摊 */
    private Long lineId;
    private Long companyId;
    private Long deptId;
    private Long costCenterId;
    private Long projectId;
    /** 预算明细ID（bg_item，本行摊到哪个预算项，D-DEC-374） */
    private Long budgetItemId;
    /** 分摊比例(%) */
    private BigDecimal shareRatio;
    /** 分摊金额 */
    private BigDecimal shareAmount;
    /** 分摊规则：COST_CENTER 成本中心承担/PROJECT 项目承担（与单据页「费用承担类型」同码，D-DEC-374） */
    private String shareType;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
