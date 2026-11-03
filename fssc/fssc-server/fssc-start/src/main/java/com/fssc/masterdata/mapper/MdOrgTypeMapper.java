// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdOrgType;
import org.apache.ibatis.annotations.Mapper;

/** 组织类型（定义组织的分类类型） */
@Mapper
public interface MdOrgTypeMapper extends BaseMapper<MdOrgType> {
}
