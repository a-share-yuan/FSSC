// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.ExtInterfaceConfig;
import org.apache.ibatis.annotations.Mapper;

/** 外部接口统一配置（ext_interface_config，带 tenant_id） */
@Mapper
public interface ExtInterfaceConfigMapper extends BaseMapper<ExtInterfaceConfig> {
}
