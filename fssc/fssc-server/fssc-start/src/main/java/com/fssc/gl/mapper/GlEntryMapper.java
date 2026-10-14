// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.gl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.gl.entity.GlEntry;
import org.apache.ibatis.annotations.Mapper;

/** 总账分录 Mapper */
@Mapper
public interface GlEntryMapper extends BaseMapper<GlEntry> {
}