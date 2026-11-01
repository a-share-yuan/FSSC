// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.payplan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.payplan.entity.PayPlan;
import org.apache.ibatis.annotations.Mapper;

/** 采购付款计划 Mapper（pay_plan） */
@Mapper
public interface PayPlanMapper extends BaseMapper<PayPlan> {
}