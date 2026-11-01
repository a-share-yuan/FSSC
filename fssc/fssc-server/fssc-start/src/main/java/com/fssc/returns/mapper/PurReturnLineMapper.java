// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.returns.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.returns.entity.PurReturnLine;
import org.apache.ibatis.annotations.Mapper;

/** 采购退货单明细行 Mapper。 */
@Mapper
public interface PurReturnLineMapper extends BaseMapper<PurReturnLine> {
}
