// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.OpVoucher;
import org.apache.ibatis.annotations.Mapper;

/** 记账凭证（带 tenant_id） */
@Mapper
public interface OpVoucherMapper extends BaseMapper<OpVoucher> {
}
