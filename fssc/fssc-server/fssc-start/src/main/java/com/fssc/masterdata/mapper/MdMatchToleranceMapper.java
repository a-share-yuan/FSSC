// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdMatchTolerance;
import org.apache.ibatis.annotations.Mapper;

/** 三单匹配容差配置 Mapper */
@Mapper
public interface MdMatchToleranceMapper extends BaseMapper<MdMatchTolerance> {
}