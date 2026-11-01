// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfAssigneeScheme;
import org.apache.ibatis.annotations.Mapper;

/** 节点参与人方案（wf_assignee_scheme，带 tenant_id）：tenant_id 由 MyBatis-Plus 租户拦截器注入 */
@Mapper
public interface WfAssigneeSchemeMapper extends BaseMapper<WfAssigneeScheme> {
}
