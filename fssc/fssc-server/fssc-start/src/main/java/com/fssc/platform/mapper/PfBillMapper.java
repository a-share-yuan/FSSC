// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.platform.entity.PfBill;
import org.apache.ibatis.annotations.Mapper;

/** 租户账单（平台表，无 tenant_id） */
@Mapper
public interface PfBillMapper extends BaseMapper<PfBill> {
}
