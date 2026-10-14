// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.returns.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.returns.entity.SaleReturnLine;
import org.apache.ibatis.annotations.Mapper;

/** 销售退货单明细行 Mapper。 */
@Mapper
public interface SaleReturnLineMapper extends BaseMapper<SaleReturnLine> {
}
