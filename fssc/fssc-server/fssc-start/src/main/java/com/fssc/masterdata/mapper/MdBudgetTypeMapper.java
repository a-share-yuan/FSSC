// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdBudgetType;
import org.apache.ibatis.annotations.Mapper;

/** 预算类型（预算管理） */
@Mapper
public interface MdBudgetTypeMapper extends BaseMapper<MdBudgetType> {
}
