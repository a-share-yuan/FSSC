// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.GlStandard;
import org.apache.ibatis.annotations.Mapper;

/** GlStandard Mapper（多账簿体系，闭环差距③）。 */
@Mapper
public interface GlStandardMapper extends BaseMapper<GlStandard> {
}
