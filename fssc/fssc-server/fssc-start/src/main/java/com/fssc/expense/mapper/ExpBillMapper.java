// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.expense.entity.ExpBill;
import org.apache.ibatis.annotations.Mapper;

/** 报销单据主表（带 tenant_id） */
@Mapper
public interface ExpBillMapper extends BaseMapper<ExpBill> {
}
