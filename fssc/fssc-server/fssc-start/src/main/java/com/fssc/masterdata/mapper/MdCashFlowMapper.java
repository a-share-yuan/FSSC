// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdCashFlow;
import org.apache.ibatis.annotations.Mapper;

/** 现金流量码（需求 2.32：管理现金流量项目编码） */
@Mapper
public interface MdCashFlowMapper extends BaseMapper<MdCashFlow> {
}
