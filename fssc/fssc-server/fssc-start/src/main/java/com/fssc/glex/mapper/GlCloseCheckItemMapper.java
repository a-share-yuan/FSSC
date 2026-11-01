// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.glex.entity.GlCloseCheckItem;
import org.apache.ibatis.annotations.Mapper;

/** 月结检查校验清单 */
@Mapper
public interface GlCloseCheckItemMapper extends BaseMapper<GlCloseCheckItem> {
}
