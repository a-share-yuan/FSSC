// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.audit.entity.AuditTrail;
import org.apache.ibatis.annotations.Mapper;

/** 审计流水 Mapper：只读 + 追加，不提供物理删除（审计表铁律）。 */
@Mapper
public interface AuditTrailMapper extends BaseMapper<AuditTrail> {
}
