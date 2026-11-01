// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfEventActionConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程事件收尾动作配置 Mapper。
 */
@Mapper
public interface WfEventActionConfigMapper extends BaseMapper<WfEventActionConfig> {
}
