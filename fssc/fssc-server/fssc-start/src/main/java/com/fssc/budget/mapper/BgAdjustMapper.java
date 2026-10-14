// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.budget.entity.BgAdjust;
import org.apache.ibatis.annotations.Mapper;

/** 预算调整单 */
@Mapper
public interface BgAdjustMapper extends BaseMapper<BgAdjust> {
}
