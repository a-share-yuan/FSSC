// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/** 租户参数配置（带 tenant_id） */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}
