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

/**
 * 预算占用流水（bg_occupy，带 tenant_id）。
 * 【铁律：只增不改】：占用/释放/核销全部追加流水，不允许 UPDATE/DELETE。
 * 因此无 del_flag，Mapper 只提供 insert。bg_item 的 occupy_amount/used_amount
 * 由 BudgetService 在事务中同步汇总（流水是账，汇总是视图）。
 */
@Data
@TableName("bg_occupy")
public class BgOccupy implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 预算明细ID（bg_item） */
    private Long budgetItemId;
    /** OCCUPY占用 / RELEASE释放 / WRITE_OFF核销 */
    private String operType;
    /** 正数占用，负数为释放/冲减 */
    private BigDecimal amount;
    /** 业务类型：EXP_CLAIM费用报销等 */
    private String bizType;
    /** 业务单据ID */
    private Long bizId;
    /** 业务单据号 */
    private String bizNo;
    private Long bizLineId;
    private Long operUserId;
    private String remark;
    private LocalDateTime createTime;

    /** R5-D-269：操作人姓名（非表列）——page 时由控制器批查 sys_user 填充（nickName 缺省回退 userName），
     *  查不到时留空由前端回退显示裸 id，禁自造码值。 */
    @TableField(exist = false)
    private String operUserName;

    /** R5-D-269：操作人工号（非表列，取 sys_user.emp_no），供「姓名（工号）」可读规则展示与导出。 */
    @TableField(exist = false)
    private String operUserNo;
}
