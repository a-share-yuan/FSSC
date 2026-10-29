-- ============================================================
-- V1.5.02 回滚：删除单据全链路联查索引 —— 2026-10-30
-- 对应 seed：V1.5.02_trace_chain_index.sql
-- 回滚后数据不变，只是联查回到整表扫（百万级库上 5~8s/次）。
--  仅删索引不足以恢复旧行为：配套代码已把 OR 拆成多条等值查询，
--    拆开后的查询在无索引时反而更慢（每条都整表扫）。回滚代码请一并回滚。
-- ============================================================
USE `fssc`;

ALTER TABLE wf_task_history DROP INDEX idx_tenant_bizid;
ALTER TABLE wf_instance     DROP INDEX idx_tenant_bizid;
ALTER TABLE op_archive      DROP INDEX idx_tenant_bill;
