-- =====================================================================
-- V1.5.07 回滚：移除统计类七枚索引
-- 删掉后计数与分桶回到 img_batch 420~540ms、op_archive 280ms、
-- 合规三表 131~453ms（整表扫）、ops/overview 800ms（del_flag 使索引失覆盖→整表扫）。
-- 注意：ImgBatchMapper 两处 SQL 固定 FORCE INDEX (idx_ib_tenant_del_biz)，
--   回滚本迁移后这两个接口会直接报 1176 Key doesn't exist，必须同时回退应用版本。
-- =====================================================================
ALTER TABLE fssc_image.img_batch DROP INDEX idx_ib_tenant_del_biz;
ALTER TABLE fssc.op_archive DROP INDEX idx_t_del_status;
ALTER TABLE fssc.precheck_result DROP INDEX idx_pr_tenant_batch_result;
ALTER TABLE fssc.precheck_result DROP INDEX idx_pr_tenant_result;
ALTER TABLE fssc.fraud_detection_log DROP INDEX idx_fdl_tenant_result_id;
ALTER TABLE fssc.op_review_log DROP INDEX idx_orl_tenant_action;
ALTER TABLE fssc.op_audit_task DROP INDEX idx_oat_tenant_del_type_ft;

SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA IN ('fssc', 'fssc_image')
  AND INDEX_NAME IN ('idx_ib_tenant_del_biz', 'idx_t_del_status', 'idx_pr_tenant_batch_result',
                     'idx_pr_tenant_result', 'idx_fdl_tenant_result_id', 'idx_orl_tenant_action', 'idx_oat_tenant_del_type_ft')
GROUP BY TABLE_NAME, INDEX_NAME;
