// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdIncomeExpense;
import org.apache.ibatis.annotations.Mapper;

/** 收支设置（资金计划） */
@Mapper
public interface MdIncomeExpenseMapper extends BaseMapper<MdIncomeExpense> {
}
