// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.image.entity.ImgBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 影像域：ImgBatch（带 tenant_id） */
@Mapper
public interface ImgBatchMapper extends BaseMapper<ImgBatch> {

    /**
     * 租户内批次数：走 idx_ib_tenant_del_biz 覆盖索引。
     * <p>必须 FORCE INDEX——del_flag 全表同值，优化器按默认 10% 过滤率误判
     * uk_tenant_batch_no 只需回表 1/10 行，实测却要多花 3 倍时间（420ms vs 70ms）。
     */
    @Select("SELECT COUNT(*) FROM img_batch FORCE INDEX (idx_ib_tenant_del_biz) "
            + "WHERE tenant_id = #{tenantId} AND del_flag = 0")
    Long countByTenant(@Param("tenantId") Long tenantId);

    /**
     * 按业务类型汇总批次：同一枚覆盖索引承载全部五列，免回表。
     * <p>FORCE INDEX 原因同 {@link #countByTenant(Long)}。
     */
    @Select("SELECT biz_type AS bizType, COUNT(*) AS batchCnt, SUM(doc_count) AS totalDocs, "
            + "SUM(CASE WHEN review_status = 'PASSED' THEN 1 ELSE 0 END) AS reviewedCnt, "
            + "SUM(CASE WHEN seal_status = 'DONE' THEN 1 ELSE 0 END) AS sealedCnt "
            + "FROM img_batch FORCE INDEX (idx_ib_tenant_del_biz) "
            + "WHERE tenant_id = #{tenantId} AND del_flag = 0 "
            + "GROUP BY biz_type ORDER BY batchCnt DESC")
    List<Map<String, Object>> statByBizType(@Param("tenantId") Long tenantId);
}
