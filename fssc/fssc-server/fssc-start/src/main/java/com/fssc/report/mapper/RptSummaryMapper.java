// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.report.entity.RptSummary;
import org.apache.ibatis.annotations.Mapper;

/** 报表预聚合 Mapper */
@Mapper
public interface RptSummaryMapper extends BaseMapper<RptSummary> {
}
