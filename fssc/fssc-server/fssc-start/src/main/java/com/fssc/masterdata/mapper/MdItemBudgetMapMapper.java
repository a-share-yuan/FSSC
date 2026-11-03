// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdItemBudgetMap;
import org.apache.ibatis.annotations.Mapper;

/** 费用类型与预算科目映射 */
@Mapper
public interface MdItemBudgetMapMapper extends BaseMapper<MdItemBudgetMap> {
}
