// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdItemBudgetMap;
import org.apache.ibatis.annotations.Mapper;

/** 费用类型与预算科目映射（需求 2.34） */
@Mapper
public interface MdItemBudgetMapMapper extends BaseMapper<MdItemBudgetMap> {
}
