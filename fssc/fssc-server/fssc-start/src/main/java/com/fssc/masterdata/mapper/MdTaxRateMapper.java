// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdTaxRate;
import org.apache.ibatis.annotations.Mapper;

/** 主数据：MdTaxRate（带 tenant_id，租户拦截器自动隔离） */
@Mapper
public interface MdTaxRateMapper extends BaseMapper<MdTaxRate> {
}
