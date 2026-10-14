// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.asset.entity.StockMove;
import org.apache.ibatis.annotations.Mapper;

/** 存货移动单（带 tenant_id） */
@Mapper
public interface StockMoveMapper extends BaseMapper<StockMove> {
}
