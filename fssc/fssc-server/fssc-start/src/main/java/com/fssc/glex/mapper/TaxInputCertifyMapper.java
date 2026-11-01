// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.glex.entity.TaxInputCertify;
import org.apache.ibatis.annotations.Mapper;

/** 进项认证台账 */
@Mapper
public interface TaxInputCertifyMapper extends BaseMapper<TaxInputCertify> {
}
