// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.OpDispatchRule;
import org.apache.ibatis.annotations.Mapper;

/** 抢派单规则（需求 8.2.1） */
@Mapper
public interface OpDispatchRuleMapper extends BaseMapper<OpDispatchRule> {
}
