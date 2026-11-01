// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysNumberSeq;
import org.apache.ibatis.annotations.Mapper;

/** 单据编号流水 */
@Mapper
public interface SysNumberSeqMapper extends BaseMapper<SysNumberSeq> {
}
