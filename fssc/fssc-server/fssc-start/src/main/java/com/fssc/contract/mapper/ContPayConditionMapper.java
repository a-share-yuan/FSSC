// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.contract.entity.ContPayCondition;
import org.apache.ibatis.annotations.Mapper;

/** 合同付款条件 */
@Mapper
public interface ContPayConditionMapper extends BaseMapper<ContPayCondition> {
}
