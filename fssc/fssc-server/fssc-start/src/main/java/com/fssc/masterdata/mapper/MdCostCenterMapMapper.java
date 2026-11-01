// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdCostCenterMap;
import org.apache.ibatis.annotations.Mapper;

/** 成本中心预算组织映射（需求 2.17） */
@Mapper
public interface MdCostCenterMapMapper extends BaseMapper<MdCostCenterMap> {
}
