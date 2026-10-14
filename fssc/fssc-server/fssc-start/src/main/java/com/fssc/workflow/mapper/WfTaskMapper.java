// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfTask;
import org.apache.ibatis.annotations.Mapper;

/** 待办任务业务视图（带 tenant_id） */
@Mapper
public interface WfTaskMapper extends BaseMapper<WfTask> {
}
