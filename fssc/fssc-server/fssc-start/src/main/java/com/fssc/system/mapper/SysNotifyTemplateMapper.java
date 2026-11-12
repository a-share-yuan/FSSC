// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysNotifyTemplate;
import org.apache.ibatis.annotations.Mapper;

/** 消息模板（触发点 → 标题/正文/深链） */
@Mapper
public interface SysNotifyTemplateMapper extends BaseMapper<SysNotifyTemplate> {
}
