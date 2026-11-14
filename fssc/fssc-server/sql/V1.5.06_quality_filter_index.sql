-- =====================================================================
-- V1.5.06 · 列表页筛选（下拉过滤）慢查询治理：质检表两枚窄覆盖索引
-- 生成日期：2026-10-30    适用库：fssc（MySQL 8.0）
-- =====================================================================
-- 背景：V1.5.05 让 op_quality 的「无筛选」分页从 377~427ms 降到 40ms，
--   但页面上两个下拉筛选仍走不到索引——过滤列（result / check_type）不在
--   (tenant_id, del_flag, check_time, id) 里，COUNT 必须扫全量索引项再逐行回表判：
--     实测 result=PASS 285ms、check_type=RANDOM 288ms（20 万行表）。
--
-- 为什么是「只有三列」的窄索引：一版曾按 (tenant_id, del_flag, result, check_time, id)
--   建宽覆盖索引，优化器一次都没选（它把 del_flag=0 估成 1% 过滤率，认为
--   idx_tenant 回表 993 行最便宜，实测 285ms）。把索引收窄到过滤三列后，
--   优化器按 ref(const,const,const) 命中，COUNT 变纯索引计数：
--     实测 result=PASS 46~50ms、check_type=RANDOM 34ms，均为原耗时的 1/6。
--   分页（ORDER BY check_time DESC）另有 idx_oq_tenant_del_ct 提供倒序早停，
--   实测 7ms，不需要这两枚索引再带 check_time。
--
-- 执行说明：在线加索引（INPLACE + LOCK=NONE），20 万行表单枚预计 < 5 秒。
-- 幂等性：重复执行报 1061 Duplicate key name，属预期。
-- 回滚：同名 _rollback.sql。
-- =====================================================================

-- 0) 前置：确认两枚索引尚不存在（应为 0 行）
SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_oq_tenant_del_result', 'idx_oq_tenant_del_type')
GROUP BY TABLE_NAME, INDEX_NAME;

-- 1) 质检结果下拉（PASS/BACK）
ALTER TABLE op_quality
    ADD INDEX idx_oq_tenant_del_result (tenant_id, del_flag, result),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 2) 抽检方式下拉（RANDOM/FULL/KEY）
ALTER TABLE op_quality
    ADD INDEX idx_oq_tenant_del_type (tenant_id, del_flag, check_type),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 3) 自检：两枚索引应全部在册（应为 2 行）
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_oq_tenant_del_result', 'idx_oq_tenant_del_type')
GROUP BY TABLE_NAME, INDEX_NAME;
