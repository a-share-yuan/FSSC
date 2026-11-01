// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.ai.entity.AiActionConfirm;
import org.apache.ibatis.annotations.Mapper;

/** 确认票据：状态机推进用 update（票据非审计日志），但绝不物理删除。 */
@Mapper
public interface AiActionConfirmMapper extends BaseMapper<AiActionConfirm> {
}
