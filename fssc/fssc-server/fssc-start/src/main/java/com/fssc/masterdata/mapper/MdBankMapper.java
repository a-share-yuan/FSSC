// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdBank;
import org.apache.ibatis.annotations.Mapper;

/** 金融机构（银行主数据）（需求 2.10） */
@Mapper
public interface MdBankMapper extends BaseMapper<MdBank> {
}
