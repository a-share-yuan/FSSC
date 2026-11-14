-- ============================================================================
-- V1.4.25b 修正：V1.4.25 第 2d 步按 node_key 定位"预制凭证复核"，压错了 CLAIM_TRAVEL 的总裁档
-- 实测（写后复核表）：CLAIM_TRAVEL.Task_14 = 总裁审批【PRESIDENT】被误设 24h（应为 48h 授权层级），
--   而真正的预制凭证复核在 CLAIM_TRAVEL.Task_15（FIN_GL），仍是 48h/NOTIFY 未落位。
--   根因：三链 node_key 序号不同构，跨模型批量配置**不能按 node_key 选节点**。已同步修正主脚本 2d/2e。
-- 回滚：无需单独脚本——V1.4.25_..._rollback.sql 按备份表还原全部 45 行（含本修正）。
-- ============================================================================
UPDATE wf_node_config SET timeout_hours = 48, timeout_action = 'AUTO_REMIND'
 WHERE del_flag = 0 AND model_key = 'CLAIM_TRAVEL' AND node_key = 'Task_14' AND assign_value = 'PRESIDENT';
UPDATE wf_node_config SET timeout_hours = 24, timeout_action = 'AUTO_REMIND'
 WHERE del_flag = 0 AND model_key IN ('CLAIM','CLAIM_TRAVEL','SETTLE') AND node_name LIKE '审核预制凭证%';
SELECT model_key,node_key,node_name,assign_value,timeout_hours,timeout_action
  FROM wf_node_config
 WHERE del_flag=0 AND model_key IN ('CLAIM','CLAIM_TRAVEL','SETTLE')
   AND (timeout_action <> 'AUTO_REMIND' OR timeout_action IS NULL)
 ORDER BY model_key, CAST(SUBSTRING(node_key,6) AS UNSIGNED);
