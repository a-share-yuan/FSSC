// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdSupplierQualification;
import org.apache.ibatis.annotations.Mapper;

/** 主数据：供应商资质证照(N)(41号审查 ,租户拦截器自动隔离) */
@Mapper
public interface MdSupplierQualificationMapper extends BaseMapper<MdSupplierQualification> {
}
