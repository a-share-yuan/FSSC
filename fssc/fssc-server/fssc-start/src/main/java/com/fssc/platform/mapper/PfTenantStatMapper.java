// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.platform.entity.PfTenantStat;
import org.apache.ibatis.annotations.Mapper;

/** 租户使用量日统计（平台表，仅聚合） */
@Mapper
public interface PfTenantStatMapper extends BaseMapper<PfTenantStat> {
}
