-- =====================================================================
-- V1.5.06 回滚：移除质检表两枚筛选索引
-- 删掉后 result / check_type 下拉筛选退回全量扫描（实测 282~307ms）。
-- =====================================================================
ALTER TABLE op_quality DROP INDEX idx_oq_tenant_del_result;
ALTER TABLE op_quality DROP INDEX idx_oq_tenant_del_type;

SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_oq_tenant_del_result', 'idx_oq_tenant_del_type')
GROUP BY TABLE_NAME, INDEX_NAME;
