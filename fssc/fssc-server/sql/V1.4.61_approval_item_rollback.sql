-- =============================================================================
-- V1.4.61 流程节点审批项 wf_approval_item 灌 rollback
--
-- 与 V1.4.61_approval_item_seed.sql 成对。本件新增 6 行，回滚即整体删除这 6 行。
-- 不触碰 wf_node_config / wf_task_history —— 本件从未改动这两张表。
-- =============================================================================

DELETE FROM fssc.wf_approval_item
 WHERE tenant_id = 1 AND model_key = 'CLAIM'
   AND node_key IN ('Task_5','Task_11','Task_13')
   AND item_code IN ('APPROVE','REJECT');

-- 回滚后复点（期望 0）
-- SELECT COUNT(*) FROM fssc.wf_approval_item WHERE tenant_id=1 AND del_flag=0;
--
-- 回滚后行为：审批弹窗退回"只有同意/驳回两个固定按钮"，与本件执行前逐字一致。
-- 已用审批项办过的单据不受影响：ActionResult 只是一枚流程变量，
-- 单据已流转到下游节点后其值不再被读取。