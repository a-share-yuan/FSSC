// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.compliance.entity.SatisfactionSurvey;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SatisfactionSurveyMapper extends BaseMapper<SatisfactionSurvey> {
}
