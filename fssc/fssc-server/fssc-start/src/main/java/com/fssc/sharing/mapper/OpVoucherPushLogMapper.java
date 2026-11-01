// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.OpVoucherPushLog;
import org.apache.ibatis.annotations.Mapper;

/** 凭证推送外部系统日志（带 tenant_id） */
@Mapper
public interface OpVoucherPushLogMapper extends BaseMapper<OpVoucherPushLog> {
}
