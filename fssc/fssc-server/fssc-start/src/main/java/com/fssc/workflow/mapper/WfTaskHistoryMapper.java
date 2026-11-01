// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfTaskHistory;
import org.apache.ibatis.annotations.Mapper;

/** 审批历史（带 tenant_id）。【审计表-不可删】不提供 delete 能力。 */
@Mapper
public interface WfTaskHistoryMapper extends BaseMapper<WfTaskHistory> {
}
