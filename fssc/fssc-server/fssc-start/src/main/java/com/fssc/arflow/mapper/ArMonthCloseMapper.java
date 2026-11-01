// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.arflow.entity.ArMonthClose;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArMonthCloseMapper extends BaseMapper<ArMonthClose> {
}
