// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.expense.entity.ExpBillTrace;
import org.apache.ibatis.annotations.Mapper;

/** 单据状态轨迹（带 tenant_id）。【审计表-不可删】不提供 delete。 */
@Mapper
public interface ExpBillTraceMapper extends BaseMapper<ExpBillTrace> {
}
