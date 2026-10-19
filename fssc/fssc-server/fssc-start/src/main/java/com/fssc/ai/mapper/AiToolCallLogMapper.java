// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.ai.entity.AiToolCallLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 工具调用日志。【审计铁律】：只提供 insert 与查询（继承 BaseMapper 的 select 能力即可），
 * 不提供 update/delete —— 与 sys_oper_log/pf_oper_log 同口径，留痕不可删。
 */
@Mapper
public interface AiToolCallLogMapper extends BaseMapper<AiToolCallLog> {
}
