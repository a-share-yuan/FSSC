// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdBudgetOrg;
import org.apache.ibatis.annotations.Mapper;

/** 预算组织（责任组织）（需求 3.4） */
@Mapper
public interface MdBudgetOrgMapper extends BaseMapper<MdBudgetOrg> {
}
