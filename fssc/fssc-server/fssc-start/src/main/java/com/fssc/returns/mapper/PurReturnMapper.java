// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.returns.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.returns.entity.PurReturn;
import org.apache.ibatis.annotations.Mapper;

/** 采购退货单 Mapper（无 XML，全部走 BaseMapper + Lambda 条件构造器）。 */
@Mapper
public interface PurReturnMapper extends BaseMapper<PurReturn> {
}
