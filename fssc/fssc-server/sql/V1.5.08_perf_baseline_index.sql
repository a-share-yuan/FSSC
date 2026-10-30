-- =====================================================================
-- V1.5.08 · 性能基线索引补齐（28 枚）：把 10-28/29 实测跑中临时加的索引
--           并入仓库迁移，使"空库＋sql 脚本"建库路径与现库索引面等价
-- 生成日期：2026-10-30    适用库：fssc（MySQL 8.0）
-- =====================================================================
-- 背景：V1.5.02~V1.5.07 收录的索引已进仓库；但同一轮性能普查里还有 28 枚是
--   直接在开发库上执行 ALTER 加的（脚本散落在工作目录 .tmp/perfrun/*.sql），
--   仓库迁移里没有。走「dump 还原」建库不受影响（索引随 dump 走），
--   走「空库＋sql 脚本」建库会缺这 28 枚 ⇒ 客户侧列表/待办/追溯页回到慢查询。
--
-- 口径（2026-10-30 只读对撞 information_schema.STATISTICS，922 枚索引）：
--   临时脚本共 29 枚 → 现库 29 枚全部在位且列序与脚本完全一致；
--   其中 op_quality.idx_tenant_del (tenant_id, del_flag) 是
--   idx_oq_tenant_del_ct / idx_oq_tenant_del_result / idx_oq_tenant_del_type
--   三枚索引的前缀，V1.5.05/06 已覆盖 ⇒ 本件不收录，按 28 枚出。
--
--   过滤前缀恒为 tenant_id[, del_flag] 固定最前；其后"等值列 → 排序列 → 主键"，
--   让分页 COUNT（覆盖）与 ORDER BY … LIMIT（索引序早停）命中同一枚索引。
--   wf_task.idx_tenant_done_agg 是待办聚合的宽覆盖索引，取数端固定 FORCE INDEX。
--
-- 执行说明：全部在线加索引（INPLACE + LOCK=NONE），不锁表。
--   幂等性：重复执行报 1061 Duplicate key name，属预期（说明已生效）。
--   若实例禁用 INPLACE 报 1846，去掉 ALGORITHM/LOCK 子句重跑。
-- 回滚：V1.5.08_perf_baseline_index_rollback.sql
-- =====================================================================

-- 0) 前置：确认 28 枚索引尚不存在（应为 0 行；有行说明已建过，跳过对应语句即可）
SELECT INDEX_NAME, TABLE_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN (
    'idx_tenant_inst_oper','idx_tenant_oper',
    'idx_tenant_done_agg','idx_tenant_status_node','idx_tenant_status_ct','idx_tenant_status_due',
    'idx_proc_inst_model','idx_tenant_status','idx_tenant_end',
    'idx_tenant_del','idx_tenant_company_del_id','idx_tenant_srcdoc',
    'idx_tenant_agentuser','idx_tenant_owner_pair','idx_tenant_company_period','idx_tenant_company_status',
    'idx_report_cover','idx_tenant_owner','idx_tenant_bill_type','idx_tenant_pool_del'
  )
GROUP BY INDEX_NAME, TABLE_NAME;

-- 1) 待办 / 流程实例 / 追溯
ALTER TABLE wf_task         ADD INDEX idx_tenant_done_agg (tenant_id, status, create_time, assignee_id, due_time, update_time, model_key, node_key), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE wf_task         ADD INDEX idx_tenant_status_node (tenant_id, status, model_key, node_key), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE wf_task         ADD INDEX idx_tenant_status_ct (tenant_id, status, create_time, id), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE wf_task         ADD INDEX idx_tenant_status_due (tenant_id, status, due_time, id), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE wf_task_history ADD INDEX idx_tenant_inst_oper (tenant_id, proc_inst_id, oper_time, id), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE wf_task_history ADD INDEX idx_tenant_oper (tenant_id, oper_time), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE wf_instance     ADD INDEX idx_proc_inst_model (proc_inst_id, model_key), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE wf_instance     ADD INDEX idx_tenant_status (tenant_id, status), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE wf_instance     ADD INDEX idx_tenant_end (tenant_id, end_time), ALGORITHM=INPLACE, LOCK=NONE;

-- 2) 费用报销单 / 行
ALTER TABLE exp_bill        ADD INDEX idx_tenant_agentuser (tenant_id, agent_user_id), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE exp_bill        ADD INDEX idx_tenant_owner_pair (tenant_id, apply_user_id, agent_user_id, del_flag), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE exp_bill        ADD INDEX idx_tenant_company_period (tenant_id, company_id, period_year, period_month), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE exp_bill        ADD INDEX idx_tenant_company_status (tenant_id, company_id, status), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE exp_bill_line   ADD INDEX idx_report_cover (tenant_id, bill_id, del_flag, item_id, item_name, amount), ALGORITHM=INPLACE, LOCK=NONE;

-- 3) 归档 / 凭证
ALTER TABLE op_archive      ADD INDEX idx_tenant_owner (tenant_id, filing_user_id), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE op_voucher      ADD INDEX idx_tenant_del (tenant_id, del_flag), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE op_voucher      ADD INDEX idx_tenant_company_del_id (tenant_id, company_id, del_flag, id), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE op_voucher      ADD INDEX idx_tenant_srcdoc (tenant_id, source_doc_id, source_type), ALGORITHM=INPLACE, LOCK=NONE;

-- 4) 审核任务 / 付款批次 / 计划
ALTER TABLE op_audit_task   ADD INDEX idx_tenant_bill_type (tenant_id, bill_id, task_type), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE op_audit_task   ADD INDEX idx_tenant_pool_del (tenant_id, pool_status, del_flag), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE op_pay_batch    ADD INDEX idx_tenant_owner (tenant_id, create_by), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE pay_plan        ADD INDEX idx_tenant_owner (tenant_id, applicant_id), ALGORITHM=INPLACE, LOCK=NONE;

-- 5) 采购 / 应收 / 发票 / 合同 / 对账（"我经手的单据"按 create_by 过滤）
ALTER TABLE po_order        ADD INDEX idx_tenant_owner (tenant_id, create_by), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE po_receipt      ADD INDEX idx_tenant_owner (tenant_id, create_by), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE rc_receivable   ADD INDEX idx_tenant_owner (tenant_id, create_by), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE inv_invoice     ADD INDEX idx_tenant_owner (tenant_id, create_by), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE cont_contract   ADD INDEX idx_tenant_owner (tenant_id, create_by), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE ar_recon_record ADD INDEX idx_tenant_owner (tenant_id, create_by), ALGORITHM=INPLACE, LOCK=NONE;
