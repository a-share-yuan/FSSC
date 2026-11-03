// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdContractType;
import org.apache.ibatis.annotations.Mapper;

/** 合同类型 */
@Mapper
public interface MdContractTypeMapper extends BaseMapper<MdContractType> {
}
