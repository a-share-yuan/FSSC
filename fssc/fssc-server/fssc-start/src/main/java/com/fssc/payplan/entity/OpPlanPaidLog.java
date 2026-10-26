// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.payplan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 付款批次→付款计划 核销台账（op_plan_paid_log，D6）。
 * <p>批次执行打款成功后由 {@link com.fssc.payplan.service.PlanWritebackService} 逐计划记录本次核销额；
 * (batch_no, plan_id) 唯一键保证批次重试/重执行不重复核销（去重）。</p>
 */
@Data
@TableName("op_plan_paid_log")
public class OpPlanPaidLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 付款计划 id */
    private Long planId;

    /** 付款计划单号 */
    private String planNo;

    /** 付款批次号 */
    private String batchNo;

    /** 本次核销金额 */
    private BigDecimal amount;

    /** 核销时间 */
    private LocalDateTime paidAt;
}
