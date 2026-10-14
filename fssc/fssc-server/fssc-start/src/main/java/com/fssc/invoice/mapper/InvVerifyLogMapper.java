// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.invoice.entity.InvVerifyLog;
import org.apache.ibatis.annotations.Mapper;

/** 发票验真日志（带 tenant_id）。【审计表-不可删】不提供 delete。 */
@Mapper
public interface InvVerifyLogMapper extends BaseMapper<InvVerifyLog> {
}
