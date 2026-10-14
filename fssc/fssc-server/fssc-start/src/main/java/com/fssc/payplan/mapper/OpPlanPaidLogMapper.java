// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.payplan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.payplan.entity.OpPlanPaidLog;
import org.apache.ibatis.annotations.Mapper;

/** 付款批次→付款计划 核销台账 Mapper（op_plan_paid_log，D6） */
@Mapper
public interface OpPlanPaidLogMapper extends BaseMapper<OpPlanPaidLog> {
}
