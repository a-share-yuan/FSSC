// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.purchase.entity.PoOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PoOrderMapper extends BaseMapper<PoOrder> {
}
