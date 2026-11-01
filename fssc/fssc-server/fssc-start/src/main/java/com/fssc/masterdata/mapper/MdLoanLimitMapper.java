// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdLoanLimit;
import org.apache.ibatis.annotations.Mapper;

/** 借款额度（需求 2.28：定义员工借款额度上限） */
@Mapper
public interface MdLoanLimitMapper extends BaseMapper<MdLoanLimit> {
}
