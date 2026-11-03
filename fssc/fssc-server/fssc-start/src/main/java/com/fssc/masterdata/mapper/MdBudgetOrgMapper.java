// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdBudgetOrg;
import org.apache.ibatis.annotations.Mapper;

/** 预算组织（责任组织） */
@Mapper
public interface MdBudgetOrgMapper extends BaseMapper<MdBudgetOrg> {
}
