-- =============================================================================
-- V1.4.64 参与人方案链路接通：POST 维度校正 + Task_7 改 assign_type=POST
--
-- 目的：让 `wf_assignee_scheme` / `wf_assignee_dim` 从"有表有代码有数据但永不被读"
--       变成**真正走过一次**的机制，验证 G2 参与人方案链路（优先级＋条件＋多维度 AND/OR）。
--
-- 【为什么必须改，而不是维持现状】
--   WfEngineFacade.java:1645 的 switch：`applyAssigneeSchemes(...)` **只挂在 default 分支**；
--   显式分支为 ROLE / USER / INITIATOR / DEPT_LEADER / DEPT_LEADER_UP。
--   实测wf_node_config 存活 179 行的 assign_type 分布 = **ROLE 179 / 其余 0**
--   ⇒ **没有任何节点落进 default** ⇒ 方案表数据再准也不会被读（空转）。
--
-- 【本次为何改 POST 维度值 —— V1.4.63 的 J0016 已不可用】
--   复核（2026-10-15 现查 fssc@127.0.0.1:3306）发现三处与 V1.4.63 注释不符：
--     ① `wf_assignee_dim` 的 POST 维度（id=2, dim_value='J0016'）**del_flag=1 已被软删**；
--        备份表 `_bak_assignee_dim_20261015` 显示其原 del_flag=0 ⇒ 软删发生在建表后 36 分钟内。
--     ② `sys_post` 中 J0016 = 「主任」，**在岗人数 0**（post_id 无任何用户指向它）。
--     ③ FSSC_AUDITOR（role_id=60）的 4 名成员 **post_id 全为 25（J0060「员工」）**，
--        无一人在 J0016。
--   ⇒ 若按 V1.4.63 原设计"恢复 J0016 维度"，
--     实测 `ROLE(60) ∩ POST(J0016)` = **0 人**，
--     而 `WfAssigneeResolver.java:126` 在"配了方案但全部解析不出人"时**抛 BizException**
--     （设计意图：禁止静默留一个没人可办的候选组）
--     ⇒ **Task_7 将无人可办，单据永久滞留**。
--   ⇒ 这也正是该维度被软删的真实原因：**不是误操作，是当时的正确保护**。
--
-- 【本件的处置：把 POST 维度值改为该角色成员的真实岗位 J0060】
--   维度2 = POST (J0060「员工」)
--   实测 `ROLE(60) ∩ POST(J0060)` = {3797 赵勇, 3820 刘磊, 3836 韩萍, 3898 方霞}
--   **与现状 ROLE 取人逐字相同的 4 人**（A==B 已在 §2 断言）。
--   ⇒ 写入数据库后Task_7 取人结果不变，但走的是"参与人方案"这条新链路。
--   ⇒ 同时**保住多维度语义**：AND 组合真实生效，非退化为单维度。
--
-- 【零影响边界】
--   · 只改 CLAIM / Task_7 一枚节点的 assign_type（ROLE → POST），其余 389 行逐字不动；
--   · assign_value 保留 'FSSC_AUDITOR' 不动（POST 分支不读该值，保留可一键回滚）；
--   · BPMN 候选组不动 ⇒ EXCLUDE 类基集也不变；
--   · 方案 enabled=1 且 condition_expr 为空（无条件适用）；
--   · 若解析异常，resolve() 抛 BizException **不静默** —— 故§2 的交集非空断言是硬前置。
--
-- 【前置依赖】sys_user.post_id 已由 V1.4.62_post_backfill_seed.sql 补齐：
--   实测存活 453 人中 **451 人已定岗**（post_id 非空）⇒ POST 维度可解析出人。
--
-- 【回滚】V1.4.64_assignee_scheme_chain_rollback.sql：
--   ① UPDATE Task_7.assign_type 改回 'ROLE'（assign_value 本件未动，原样即可）；
--   ② UPDATE wf_assignee_dim SET del_flag=1 WHERE id=2（还原为软删态，回到本件前状态）。
-- =============================================================================

-- ----------------------------------------------------------------------------
-- §1 前置复点（只读，执行前人工确认）
-- ----------------------------------------------------------------------------
-- 期望：dims_survive=1（仅 ROLE）、task7_type=ROLE、inter=4
-- SELECT (SELECT COUNT(*) FROM fssc.wf_assignee_dim WHERE del_flag=0) AS dims_survive,
--        (SELECT assign_type FROM fssc.wf_node_config
--          WHERE model_key='CLAIM' AND node_key='Task_7' AND del_flag=0) AS task7_type,
--        (SELECT COUNT(*) FROM fssc.sys_user u
--           JOIN fssc.sys_user_role ur ON ur.user_id = u.id
--           JOIN fssc.sys_post p ON p.id = u.post_id
--          WHERE ur.role_id = 60 AND p.post_code = 'J0060' AND u.del_flag = 0) AS inter;
--
-- 期望：J0016 在岗 0 人（说明不可用，本件才要改维度值）
-- SELECT COUNT(*) AS j0016_active FROM fssc.sys_user u
--   JOIN fssc.sys_post p ON p.id = u.post_id
--  WHERE p.post_code = 'J0016' AND u.del_flag = 0;

-- ----------------------------------------------------------------------------
-- §2 执行段（去重）
-- ----------------------------------------------------------------------------

-- §2.1 参与人方案维度：软删的 POST 维度恢复，并**校正维度值为真实岗位 J0060**
UPDATE fssc.wf_assignee_dim
   SET dim_value = 'J0060',
       del_flag  = 0,
       update_by = NULL,
       update_time = NOW()
 WHERE id = 2
   AND scheme_id = 1
   AND dim_type = 'POST';

-- §2.2 节点取人方式：ROLE → POST，使 default 分支生效、走参与人方案链路
UPDATE fssc.wf_node_config
   SET assign_type = 'POST',
       update_by = NULL,
       update_time = NOW()
 WHERE model_key = 'CLAIM'
   AND node_key = 'Task_7'
   AND del_flag = 0
   AND assign_type = 'ROLE';

-- ----------------------------------------------------------------------------
-- §3 执行后复点（期望：inter=4；dims_survive=2；task7_type=POST）
-- ----------------------------------------------------------------------------
-- SELECT (SELECT COUNT(*) FROM fssc.sys_user u
--           JOIN fssc.sys_user_role ur ON ur.user_id = u.id
--           JOIN fssc.sys_post p ON p.id = u.post_id
--          WHERE ur.role_id = 60 AND p.post_code = 'J0060' AND u.del_flag = 0) AS inter,
--        (SELECT COUNT(*) FROM fssc.wf_assignee_dim WHERE del_flag = 0) AS dims_survive,
--        (SELECT assign_type FROM fssc.wf_node_config
--          WHERE model_key = 'CLAIM' AND node_key = 'Task_7' AND del_flag = 0) AS task7_type;