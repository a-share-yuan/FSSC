-- =====================================================================
-- V1.5.05 回滚：移除两枚列表页复合索引
-- 只在确认该轮索引反而拖慢写入时才执行；删掉后列表页会退回
-- 「COUNT 回表过滤 + filesort」的旧计划（op_quality 端点 ~420ms，发票列表 ~220ms）。
-- =====================================================================
ALTER TABLE op_quality DROP INDEX idx_oq_tenant_del_ct;
ALTER TABLE inv_invoice DROP INDEX idx_ii_tenant_del_comp_id;

SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_oq_tenant_del_ct', 'idx_ii_tenant_del_comp_id')
GROUP BY TABLE_NAME, INDEX_NAME;
