// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdCip;
import org.apache.ibatis.annotations.Mapper;

/** 在建工程（基础数据-配置） */
@Mapper
public interface MdCipMapper extends BaseMapper<MdCip> {
}
