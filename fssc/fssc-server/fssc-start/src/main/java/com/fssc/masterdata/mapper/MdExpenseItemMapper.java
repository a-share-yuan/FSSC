// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdExpenseItem;
import org.apache.ibatis.annotations.Mapper;

/** 主数据：MdExpenseItem（带 tenant_id，租户拦截器自动隔离） */
@Mapper
public interface MdExpenseItemMapper extends BaseMapper<MdExpenseItem> {
}
