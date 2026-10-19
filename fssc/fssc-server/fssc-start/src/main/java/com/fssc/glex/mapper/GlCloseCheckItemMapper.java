// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.glex.entity.GlCloseCheckItem;
import org.apache.ibatis.annotations.Mapper;

/** 月结检查闸清单（V2.2.38 K128/K129） */
@Mapper
public interface GlCloseCheckItemMapper extends BaseMapper<GlCloseCheckItem> {
}
