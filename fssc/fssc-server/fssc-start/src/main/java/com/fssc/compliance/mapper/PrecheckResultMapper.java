// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.compliance.entity.PrecheckResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PrecheckResultMapper extends BaseMapper<PrecheckResult> {

    /**
     * 预检批次数（去重）。用派生表分组再计数，而不是 {@code COUNT(DISTINCT ...)}：
     * 同一枚覆盖索引下，后者要建临时表去重，24 万行实测 453ms，前者 75ms。
     */
    @Select("SELECT COUNT(*) FROM ("
            + "SELECT precheck_batch_id FROM precheck_result "
            + "WHERE tenant_id = #{tenantId} AND precheck_batch_id IS NOT NULL "
            + "GROUP BY precheck_batch_id) d")
    Long countDistinctBatch(@Param("tenantId") Long tenantId);
}
