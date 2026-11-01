// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.glex.entity.TaxRefund;
import org.apache.ibatis.annotations.Mapper;

/** 税务退税三场景映射器（带 tenant_id，租户拦截器自动隔离） */
@Mapper
public interface TaxRefundMapper extends BaseMapper<TaxRefund> {
}