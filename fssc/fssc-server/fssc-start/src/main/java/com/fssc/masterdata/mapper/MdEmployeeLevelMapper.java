// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdEmployeeLevel;
import org.apache.ibatis.annotations.Mapper;

/** 员工等级（差旅标准控制）（需求 2.18） */
@Mapper
public interface MdEmployeeLevelMapper extends BaseMapper<MdEmployeeLevel> {
}
