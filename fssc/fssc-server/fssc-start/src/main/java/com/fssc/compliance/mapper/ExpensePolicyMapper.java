// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.compliance.entity.ExpensePolicy;
import org.apache.ibatis.annotations.Mapper;

/** 费用政策 Mapper */
@Mapper
public interface ExpensePolicyMapper extends BaseMapper<ExpensePolicy> {
}
