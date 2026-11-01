// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.contract.entity.ContPayCondition;
import org.apache.ibatis.annotations.Mapper;

/** 合同付款条件 */
@Mapper
public interface ContPayConditionMapper extends BaseMapper<ContPayCondition> {
}
