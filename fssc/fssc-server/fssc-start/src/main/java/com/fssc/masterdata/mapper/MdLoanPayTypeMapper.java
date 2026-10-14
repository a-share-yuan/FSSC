// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdLoanPayType;
import org.apache.ibatis.annotations.Mapper;

/** 借款付款类型（需求 2.26） */
@Mapper
public interface MdLoanPayTypeMapper extends BaseMapper<MdLoanPayType> {
}
