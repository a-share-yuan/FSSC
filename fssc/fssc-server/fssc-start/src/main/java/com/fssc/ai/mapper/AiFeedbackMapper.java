// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.ai.entity.AiFeedback;
import org.apache.ibatis.annotations.Mapper;

/** 答案反馈：只增不改；评测与运营读取。 */
@Mapper
public interface AiFeedbackMapper extends BaseMapper<AiFeedback> {
}
