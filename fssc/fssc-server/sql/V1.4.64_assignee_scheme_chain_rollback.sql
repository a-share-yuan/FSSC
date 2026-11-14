-- =============================================================================
-- V1.4.64 回滚：还原参与人方案链路为"接通前"状态
--
-- 回滚目标 = V1.4.63 之后的原状：
--   ① wf_node_config.CLAIM/Task_7.assign_type : POST → ROLE
--      （assign_value 本件未动，仍为 'FSSC_AUDITOR'，原样即可）
--   ② wf_assignee_dim.id=2（POST 维度）: del_flag 0 → 1
--      还原为软删态。注意**不回改 dim_value**：保留 'J0060'，
--      这样若日后重新启用该维度，值已是校正过的可用值（J0016 在岗 0 人不可用）。
--
-- 执行后校验：
--   SELECT assign_type FROM fssc.wf_node_config
--    WHERE model_key='CLAIM' AND node_key='Task_7' AND del_flag=0;   -- 期望 ROLE
--   SELECT id,dim_type,dim_value,del_flag FROM fssc.wf_assignee_dim
--    WHERE id=2;                -- 期望 POST / J0060 / del_flag=1
-- =============================================================================

UPDATE fssc.wf_node_config
   SET assign_type = 'ROLE',
       update_by = NULL,
       update_time = NOW()
 WHERE model_key = 'CLAIM'
   AND node_key = 'Task_7'
   AND del_flag = 0
   AND assign_type = 'POST';

UPDATE fssc.wf_assignee_dim
   SET del_flag = 1,
       update_by = NULL,
       update_time = NOW()
 WHERE id = 2
   AND scheme_id = 1
   AND dim_type = 'POST'
   AND del_flag = 0;