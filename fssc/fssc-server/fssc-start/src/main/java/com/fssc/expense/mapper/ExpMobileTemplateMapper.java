// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.expense.entity.ExpMobileTemplate;
import org.apache.ibatis.annotations.Mapper;

/** 移动端单据启用清单（带 tenant_id，租户条件由拦截器注入，本接口不写任何 SQL） */
@Mapper
public interface ExpMobileTemplateMapper extends BaseMapper<ExpMobileTemplate> {
}
