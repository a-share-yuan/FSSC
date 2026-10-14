// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.treasury.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.treasury.entity.TrFundPlan;
import org.apache.ibatis.annotations.Mapper;

/** 资金计划 Mapper */
@Mapper
public interface TrFundPlanMapper extends BaseMapper<TrFundPlan> {
}