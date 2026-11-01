// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysMessage;
import org.apache.ibatis.annotations.Mapper;

/** 站内消息/待办提醒（带 tenant_id） */
@Mapper
public interface SysMessageMapper extends BaseMapper<SysMessage> {
}
