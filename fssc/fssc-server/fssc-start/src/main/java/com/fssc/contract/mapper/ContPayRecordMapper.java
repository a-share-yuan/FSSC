// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.contract.entity.ContPayRecord;
import org.apache.ibatis.annotations.Mapper;

/** 合同支付记录 */
@Mapper
public interface ContPayRecordMapper extends BaseMapper<ContPayRecord> {
}
