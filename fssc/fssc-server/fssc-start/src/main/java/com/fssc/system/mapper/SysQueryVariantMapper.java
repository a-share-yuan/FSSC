// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysQueryVariant;
import org.apache.ibatis.annotations.Mapper;

/** 查询方案（Variant）Mapper */
@Mapper
public interface SysQueryVariantMapper extends BaseMapper<SysQueryVariant> {
}