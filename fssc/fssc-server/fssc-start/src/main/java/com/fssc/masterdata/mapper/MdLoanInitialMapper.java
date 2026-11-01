// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdLoanInitial;
import org.apache.ibatis.annotations.Mapper;

/** 初始借款余额（需求 2.29：导入上线前员工借款余额） */
@Mapper
public interface MdLoanInitialMapper extends BaseMapper<MdLoanInitial> {
}
