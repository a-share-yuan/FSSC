// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.glex.entity.TaxDeclaration;
import org.apache.ibatis.annotations.Mapper;

/** 全税种申报表 */
@Mapper
public interface TaxDeclarationMapper extends BaseMapper<TaxDeclaration> {
}
