-- ============================================================
-- V1.5.02 单据全链路联查索引（联查抽屉 7s → 0.2s）—— 2026-10-30
--
-- 背景（实测，非估计）：
--   GET /api/tenant/expense/bill-trace-chain/{billId} 在百万级造数库上稳定 5.5~8.3s，
--   其中 7.0s 全部落在四条 SQL 上（performance_schema.events_statements_summary_by_digest 单请求采样）：
--     wf_task_history  3.64s   WHERE biz_id=? AND tenant_id=? ORDER BY id LIMIT 500
--     op_archive       1.41s   WHERE (bill_id=…OR ref_id=…) ⇒ 整表扫
--     wf_instance      0.96s   WHERE biz_id=? AND tenant_id=? ORDER BY id
--     op_voucher       0.87s   WHERE bill_id=? OR source_doc_id=? OR proc_inst_id IN (…) ⇒ 整表扫
--
-- 根因：既有复合索引把 biz_id 放在第三列（idx_tenant_biz＝tenant_id,biz_type,biz_id），
--   查询只给 tenant_id+biz_id 时无法使用；op_archive 的 bill_id 完全没有索引。
--
-- 本脚本（只加索引，不动数据）：
--   1) wf_task_history (tenant_id, biz_id, id)          —— 覆盖过滤 + ORDER BY id
--   2) wf_instance     (tenant_id, biz_id, id)          —— 同上
--   3) op_archive      (tenant_id, bill_id, archive_type) —— 补 bill_id 索引（ref_id 臂已由 idx_tenant_ref 覆盖）
--
-- 配套代码改动（同版本）：
--   BillTraceChainController.expenseVouchersOf / genericVouchersOf 把跨列 OR 拆成各自命中索引的等值查询；
--   ArchiveFacadeImpl.findByBillId 把 (bill_id OR ref_id) 拆成两次查询后并集。
--   ⇒ OR 跨列时优化器不做 index merge，仅加索引不足以让这两条 SQL 走索引。
--
-- 生效方式：DDL 即时生效，无需重启；代码改动需重新出包（本次同一窗口）。
-- 本环境已于 2026-10-30 直接执行本脚本（在线 DDL，INPLACE/LOCK=NONE，11.6s）。
-- ============================================================
USE `fssc`;

-- 0) 执行前置检查（只读）：应返回空集，非空说明索引已存在，跳过对应 ALTER
SELECT table_name, index_name
FROM information_schema.statistics
WHERE table_schema = 'fssc'
  AND ((table_name = 'wf_task_history' AND index_name = 'idx_tenant_bizid')
    OR (table_name = 'wf_instance'     AND index_name = 'idx_tenant_bizid')
    OR (table_name = 'op_archive'      AND index_name = 'idx_tenant_bill'))
GROUP BY table_name, index_name;

-- 1) 审批轨迹：biz_id 是除 tenant_id 外唯一的过滤列
ALTER TABLE wf_task_history ADD INDEX idx_tenant_bizid (tenant_id, biz_id, id), ALGORITHM=INPLACE, LOCK=NONE;

-- 2) 流程实例：同上（多轮次全取，按 id 正序）
ALTER TABLE wf_instance ADD INDEX idx_tenant_bizid (tenant_id, biz_id, id), ALGORITHM=INPLACE, LOCK=NONE;

-- 3) 案卷：溯源按 bill_id 直挂取卷，原表只有 (tenant_id, archive_type, ref_id)
ALTER TABLE op_archive ADD INDEX idx_tenant_bill (tenant_id, bill_id, archive_type), ALGORITHM=INPLACE, LOCK=NONE;
