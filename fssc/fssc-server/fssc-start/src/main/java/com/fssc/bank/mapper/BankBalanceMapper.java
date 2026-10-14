// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bank.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.bank.entity.BankBalance;
import org.apache.ibatis.annotations.Mapper;

/** 银行账户余额快照 Mapper（bank_balance） */
@Mapper
public interface BankBalanceMapper extends BaseMapper<BankBalance> {
}