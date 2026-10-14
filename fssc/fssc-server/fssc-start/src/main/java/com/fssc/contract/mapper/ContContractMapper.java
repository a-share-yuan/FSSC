// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.contract.entity.ContContract;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContContractMapper extends BaseMapper<ContContract> {
}
