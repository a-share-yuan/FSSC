// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.expense.entity.ExpBillReceipt;
import org.apache.ibatis.annotations.Mapper;

/** expense 域：ExpBillReceipt（资金流入侧收款事实，带 tenant_id） */
@Mapper
public interface ExpBillReceiptMapper extends BaseMapper<ExpBillReceipt> {
}
