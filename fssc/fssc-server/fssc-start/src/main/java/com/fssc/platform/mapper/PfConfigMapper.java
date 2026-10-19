// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.platform.entity.PfConfig;
import org.apache.ibatis.annotations.Mapper;

/** 平台全局配置（平台表，无 tenant_id） */
@Mapper
public interface PfConfigMapper extends BaseMapper<PfConfig> {
}
