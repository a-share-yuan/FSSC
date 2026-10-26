// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdGlConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/** 记账配置（分录生成规则）（需求 2.20） */
@Mapper
public interface MdGlConfigMapper extends BaseMapper<MdGlConfig> {

    /**
     * 按维度候选值宽松查询 md_gl_config：
     * - item_id IN (itemIds)；
     * - dim_type/dim_cc_type/dim_travel/dim_special/dim_status/direction 每个维度允许候选值列表或空（空不参与过滤）。
     * 返回结果按「维度精确匹配数」由调用方在内存里挑选最佳。
     */
    default List<MdGlConfig> queryByDims(Long tenantId,
                                         Collection<Long> itemIds,
                                         Collection<String> dimTypes,
                                         Collection<String> ccTypes,
                                         Collection<String> travels,
                                         Collection<String> specials,
                                         Collection<String> statuses,
                                         Collection<String> directions) {
        LambdaQueryWrapper<MdGlConfig> w = new LambdaQueryWrapper<>();
        w.eq(MdGlConfig::getTenantId, tenantId);
        if (itemIds != null && !itemIds.isEmpty()) w.in(MdGlConfig::getItemId, itemIds);
        if (dimTypes != null && !dimTypes.isEmpty()) w.in(MdGlConfig::getDimType, dimTypes);
        if (ccTypes != null && !ccTypes.isEmpty()) w.in(MdGlConfig::getDimCcType, ccTypes);
        if (travels != null && !travels.isEmpty()) w.in(MdGlConfig::getDimTravel, travels);
        if (specials != null && !specials.isEmpty()) w.in(MdGlConfig::getDimSpecial, specials);
        if (statuses != null && !statuses.isEmpty()) w.in(MdGlConfig::getDimStatus, statuses);
        if (directions != null && !directions.isEmpty()) w.in(MdGlConfig::getDirection, directions);
        w.eq(MdGlConfig::getStatus, "NORMAL");
        return selectList(w);
    }
}
