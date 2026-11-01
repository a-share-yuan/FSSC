// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.match.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.match.entity.MatchTolerance;
import org.apache.ibatis.annotations.Mapper;

/**  三单匹配容差参数 Mapper（tenant_id 过滤由 FsscTenantLineHandler 自动追加） */
@Mapper
public interface MatchToleranceMapper extends BaseMapper<MatchTolerance> {
}
