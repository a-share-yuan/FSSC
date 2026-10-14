// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.treasury.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.treasury.entity.TrBillSettle;
import org.apache.ibatis.annotations.Mapper;

/** 票据结算 Mapper */
@Mapper
public interface TrBillSettleMapper extends BaseMapper<TrBillSettle> {
}