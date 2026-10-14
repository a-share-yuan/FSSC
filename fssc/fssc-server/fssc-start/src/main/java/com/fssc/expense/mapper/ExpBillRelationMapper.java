// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.expense.entity.ExpBillRelation;
import org.apache.ibatis.annotations.Mapper;

/** 单据细分：ExpBillRelation（带 tenant_id） */
@Mapper
public interface ExpBillRelationMapper extends BaseMapper<ExpBillRelation> {
}
