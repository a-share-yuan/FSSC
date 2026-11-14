package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.fssc.masterdata.entity.MdGlConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/** 记账配置（分录生成规则） */
@Mapper
public interface MdGlConfigMapper extends BaseMapper<MdGlConfig> {

    /**
     * 按维度候选值宽松查询 md_gl_config：
     * - item_id IN (itemIds)；
     * - dim_type/dim_cc_type/dim_travel/dim_special/dim_status/direction 每个维度允许候选值列表或空（空不参与过滤）。
     * 返回结果按「维度精确匹配数」由调用方在内存里挑选最佳。
     * <p><b>未配置的维度（NULL）必须放行</b>：本方法原先按 {@code dim_x IN (候选)} 严格过滤，
     * 把「该维度留空＝通配」的规则整条排除，而调用侧的 {@code bestMatch}／{@code dimMatches}
     * 明明把留空当通配打分；【记账配置】维护界面又只提供费用类型/方向/科目，
     * 没有一枚维度列 ⇒ 界面新增的规则永远进不了规则池，制证退回非末级兜底科目被拦。
     * 这里与匹配器口径合一：该维度为空或命中候选值都算候选规则。
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
        orWildcard(w, MdGlConfig::getDimType, dimTypes);
        orWildcard(w, MdGlConfig::getDimCcType, ccTypes);
        orWildcard(w, MdGlConfig::getDimTravel, travels);
        orWildcard(w, MdGlConfig::getDimSpecial, specials);
        orWildcard(w, MdGlConfig::getDimStatus, statuses);
        if (directions != null && !directions.isEmpty()) w.in(MdGlConfig::getDirection, directions);
        w.eq(MdGlConfig::getStatus, "NORMAL");
        return selectList(w);
    }

    /** 维度候选值 IN ＋ 留空通配（候选集为空时整条维度不参与过滤） */
    default void orWildcard(LambdaQueryWrapper<MdGlConfig> w,
                            SFunction<MdGlConfig, ?> col,
                            Collection<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return;
        w.and(x -> x.in(col, candidates).or().isNull(col).or().eq(col, ""));
    }
}
