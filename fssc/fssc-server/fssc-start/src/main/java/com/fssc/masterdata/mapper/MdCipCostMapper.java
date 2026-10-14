// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdCipCost;
import org.apache.ibatis.annotations.Mapper;

/** 在建工程成本归集明细 */
@Mapper
public interface MdCipCostMapper extends BaseMapper<MdCipCost> {
}