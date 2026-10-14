// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.compliance.entity.EmployeeCredit;
import org.apache.ibatis.annotations.Mapper;

/** 员工信用 */
@Mapper
public interface EmployeeCreditMapper extends BaseMapper<EmployeeCredit> {
}
