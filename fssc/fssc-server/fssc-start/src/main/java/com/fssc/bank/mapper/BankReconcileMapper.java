// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bank.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.bank.entity.BankReconcile;
import org.apache.ibatis.annotations.Mapper;

/** 银企勾对流水 Mapper（只读 + 追加，不提供物理删除） */
@Mapper
public interface BankReconcileMapper extends BaseMapper<BankReconcile> {
}
