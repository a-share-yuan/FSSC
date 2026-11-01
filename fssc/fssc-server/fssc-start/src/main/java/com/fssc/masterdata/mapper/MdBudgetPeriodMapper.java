// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdBudgetPeriod;
import org.apache.ibatis.annotations.Mapper;

/** 预算期间（需求 3.6） */
@Mapper
public interface MdBudgetPeriodMapper extends BaseMapper<MdBudgetPeriod> {
}
