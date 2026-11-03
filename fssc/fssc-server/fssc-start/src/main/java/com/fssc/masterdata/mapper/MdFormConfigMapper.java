// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdFormConfig;
import org.apache.ibatis.annotations.Mapper;

/** 表单详情配置（配置各业务表单的字段/布局/规则） */
@Mapper
public interface MdFormConfigMapper extends BaseMapper<MdFormConfig> {
}
