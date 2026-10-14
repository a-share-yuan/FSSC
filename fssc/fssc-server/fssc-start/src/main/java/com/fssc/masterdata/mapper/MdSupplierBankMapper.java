// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdSupplierBank;
import org.apache.ibatis.annotations.Mapper;

/** 主数据：供应商银行账户(N)(41号审查 P1-8,租户拦截器自动隔离) */
@Mapper
public interface MdSupplierBankMapper extends BaseMapper<MdSupplierBank> {
}
