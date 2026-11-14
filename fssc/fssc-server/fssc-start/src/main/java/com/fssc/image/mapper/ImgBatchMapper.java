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
     * 该批次号是否已被占用（**含软删行**）。
     * <p>与唯一键 uk_tenant_batch_no 同口径：唯一键不区分 del_flag，删掉的批次仍占着号。
     * 取号重试必须用这条原生 SQL——用 BaseMapper 的 selectCount 会被逻辑删插件补上
     * {@code del_flag=0}，把软删行当"没占用"，取回来的号照样插不进去。
     */
    @Select("SELECT COUNT(*) FROM img_batch WHERE tenant_id = #{tenantId} AND batch_no = #{batchNo}")
    Long countByBatchNoIncludingDeleted(@Param("tenantId") Long tenantId,
                                        @Param("batchNo") String batchNo);

    /**
     * 按封单状态计数，强制走 {@code idx_ib_tenant_del_seal}。
     *
     * <p>2026-11-14 实测：优化器对 {@code del_flag=0 AND seal_status=?} 默认选
     * {@code idx_ib_tenant_del_biz}（305ms，扫 24.7 万行），只有 FORCE INDEX 才用上
     * 专用索引（126ms）。故必须提供本方法供 {@code ImageService#countTask} 在
     * 「仅封单状态筛选」时调用 —— 索引与 SQL 必须成对上线。</p>
     */
    @Select("SELECT COUNT(*) FROM img_batch FORCE INDEX (idx_ib_tenant_del_seal) "
            + "WHERE tenant_id = #{tenantId} AND del_flag = 0 AND seal_status = #{sealStatus}")
    Long countBySealStatus(@Param("tenantId") Long tenantId, @Param("sealStatus") String sealStatus);

    /**
     * 按业务类型汇总批次：同一枚覆盖索引承载全部五列，免回表。
     * <p>FORCE INDEX 原因同 {@link #countByTenant(Long)}。
     *
     * <p><b>2026-11-14 修枚举口径</b>：原判定写的是 {@code review_status='PASSED'} /
     * {@code seal_status='DONE'}，而代码实际写入的白名单是
     * {@code PENDING/OK/REJECTED} 与 {@code UNSEALED/SEALED}
     * （见 {@code ImageService#setReviewStatus}、{@code ImageService#seal}）。
     * 两个 SUM(CASE WHEN) 因此恒为 0 —— 压测库实有 16.7 万 OK、25 万 SEALED，
     * 接口却五个 bizType 全报 0，属静默错数，不是性能问题。</p>
     */
    @Select("SELECT biz_type AS bizType, COUNT(*) AS batchCnt, SUM(doc_count) AS totalDocs, "
            + "SUM(CASE WHEN review_status = 'OK' THEN 1 ELSE 0 END) AS reviewedCnt, "
            + "SUM(CASE WHEN seal_status = 'SEALED' THEN 1 ELSE 0 END) AS sealedCnt "
            + "FROM img_batch FORCE INDEX (idx_ib_tenant_del_biz) "
            + "WHERE tenant_id = #{tenantId} AND del_flag = 0 "
            + "GROUP BY biz_type ORDER BY batchCnt DESC")
    List<Map<String, Object>> statByBizType(@Param("tenantId") Long tenantId);
}
