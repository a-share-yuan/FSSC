-- =====================================================================
-- V1.5.04 回滚：撤掉九表十枚复合索引（V1.5.04_list_page_index.sql 的逆操作）
-- 生成日期：2026-10-30    适用库：fssc（MySQL 8.0）
-- 说明：回滚只影响查询计划，不改数据。执行后 2026-10-30 复测通过的
--   wf_process_log / op_dispatch_log / op_transfer_log / wf_task_history /
--   archive_access_log / op_archive / bank_reconcile / sys_oper_log 列表页，
--   以及「我的流程历史」（wf_task 按实例取任务）会退回到
--   "每行回表判过滤"的旧计划（端点耗时回到秒级，审批历史页重新挂死）。
--   幂等性：索引不存在时报 1091，属预期。
-- =====================================================================

ALTER TABLE op_archive         DROP INDEX idx_t_del_comp_status_ct, ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE archive_access_log DROP INDEX idx_tal_tenant_status_ct,   ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE wf_process_log     DROP INDEX idx_wpl_tenant_del_id,      ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE op_dispatch_log    DROP INDEX idx_odl_tenant_del_id,      ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE op_transfer_log    DROP INDEX idx_otl_tenant_del_id,      ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE wf_task_history    DROP INDEX idx_wth_tenant_action_oper, ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE wf_task_history    DROP INDEX idx_wth_tenant_target,      ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE bank_reconcile     DROP INDEX idx_br_tenant_id,           ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE wf_task            DROP INDEX idx_t_inst_assignee,        ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE sys_oper_log       DROP INDEX idx_t_title_id,             ALGORITHM = INPLACE, LOCK = NONE;

SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_t_del_comp_status_ct', 'idx_tal_tenant_status_ct', 'idx_wpl_tenant_del_id',
                     'idx_odl_tenant_del_id', 'idx_otl_tenant_del_id',
                     'idx_wth_tenant_action_oper', 'idx_wth_tenant_target', 'idx_br_tenant_id',
                     'idx_t_inst_assignee', 'idx_t_title_id')
GROUP BY TABLE_NAME, INDEX_NAME;
