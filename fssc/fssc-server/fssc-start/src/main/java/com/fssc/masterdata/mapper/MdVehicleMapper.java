// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdVehicle;
import org.apache.ibatis.annotations.Mapper;

/** 交通工具（需求 2.23：交通工具类型及对应舱位等级、适用员工等级范围） */
@Mapper
public interface MdVehicleMapper extends BaseMapper<MdVehicle> {
}
