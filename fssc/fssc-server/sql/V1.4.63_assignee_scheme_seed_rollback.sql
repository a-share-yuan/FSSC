-- =============================================================================
-- V1.4.63 参与人方案首套落地 rollback
--
-- 与 V1.4.63_assignee_scheme_seed.sql 成对。两步回滚，顺序不可颠倒：
--   ① 先把节点切回 ROLE（否则留着指向已软删方案的 POST 分支会走进"无方案"路径）；
--   ② 再软删方案与维度（保留行做痕迹）。
--
-- 回滚后：CLAIM / Task_7 回到「走 ROLE 分支」，取人结果与本件执行前逐字一致；
--         wf_assignee_scheme / wf_assignee_dim 回到「有表零有效行」的原状。
-- =============================================================================

-- ① 节点切回 ROLE
UPDATE fssc.wf_node_config SET assign_type = 'ROLE'
 WHERE tenant_id = 1 AND del_flag = 0
   AND model_key = 'CLAIM' AND node_key = 'Task_7'
   AND assign_type = 'POST';

-- ② 软删维度与方案
UPDATE fssc.wf_assignee_dim SET del_flag = 1
 WHERE tenant_id = 1 AND scheme_id IN (
   SELECT id FROM (SELECT id FROM fssc.wf_assignee_scheme
                    WHERE tenant_id = 1 AND model_key = 'CLAIM'
                      AND node_key = 'Task_7') x);

UPDATE fssc.wf_assignee_scheme SET del_flag = 1, enabled = 0
 WHERE tenant_id = 1 AND model_key = 'CLAIM' AND node_key = 'Task_7';

-- ----------------------------------------------------------------------------
-- 回滚后复点
-- ----------------------------------------------------------------------------
-- ① 节点已切回（期望 ROLE / FSSC_AUDITOR）
-- SELECT node_key, assign_type, assign_value FROM fssc.wf_node_config
--  WHERE tenant_id=1 AND del_flag=0 AND model_key='CLAIM' AND node_key='Task_7';
--
-- ② 全表 assign_type 分布回到 POST=0（期望 ROLE 385、INITIATOR 1）
-- SELECT assign_type, COUNT(*) FROM fssc.wf_node_config GROUP BY assign_type;
--
-- ③ 方案与维度已软删（期望两个 0）
-- SELECT COUNT(*) AS schemes FROM fssc.wf_assignee_scheme WHERE del_flag = 0;
-- SELECT COUNT(*) AS dims    FROM fssc.wf_assignee_dim    WHERE del_flag = 0;
--
-- 注：V1.4.62（sys_user.post_id 补数）**不在本回滚范围内** ——
--     post_id 是主数据补齐，与本件的方案验证相互独立；
--     单独回滚 post_id 见 V1.4.62_post_backfill_rollback.sql。