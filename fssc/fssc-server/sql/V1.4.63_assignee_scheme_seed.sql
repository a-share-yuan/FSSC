-- =============================================================================
-- V1.4.63 参与人方案首套落地 + Task_7 改 assign_type=POST（P0-5）
--
-- 目的：把 `wf_assignee_scheme` / `wf_assignee_dim` 从"有表有代码零数据"变成
--       **真跑起来的一套方案**，同时**不改变任何一个节点的取人结果**。
--
-- 【为什么此前灌了也不生效（2026-10-15 读码实证）】
--   WfEngineFacade.java:1645 的 switch：`applyAssigneeSchemes(...)` 只挂在 **default 分支**；
--   显式分支为 ROLE / USER / INITIATOR / DEPT_LEADER / DEPT_LEADER_UP。
--   而实测 wf_node_config 全表 386 行（含软删）的 assign_type 分布 =
--       ROLE 364 / INITIATOR 21 / USER 1 —— **POST / SHARE_POOL / EXPRESSION 历史上零行**。
--   ⇒ 没有任何节点落进 default，灌方案表纯空转。**"代码在 + 数据空"≠"只缺数据"。**
--
-- 【本件为何敢改 assign_type：AND 交集零差异实证】
--   试点节点选 CLAIM / Task_7「财务共享审核单据【共享会计】」。
--   若配单维度 POST(J0016)，该节点可办人会从 4 人**扩到 16 人**（J0016 主任里混了
--   仓库管理员/档案管理员/内审经理，与"共享会计审核"语义不符）—— 属业务语义变更，需另案裁决。
--
--   本件改用 **combine_mode=AND 的双维度交集**：
--       维度1 = ROLE  (FSSC_AUDITOR)
--       维度2 = POST  (J0016)
--   实测交集 = {3797 共享会计-01, 3820 共享会计-02, 3836 成本费用岗-01, 3898 共享会计-03}
--             **与现状 ROLE 取人逐字相同的 4 人**（A==B 已断言）。
--   ⇒ 本件落库后 Task_7 的取人结果**不变**，但走的是"参与人方案"这条新链路
--     （优先级 + 条件表达式 + 多维度 AND/OR/EXCLUDE），机制得到真实验证。
--
-- 【零影响边界】
--   · 只改 CLAIM / Task_7 一枚节点的 assign_type（ROLE → POST），其余 385 行逐字不动；
--   · assign_value 保留 'FSSC_AUDITOR' 不动（POST 分支不读该值，但保留可一键回滚）；
--   · BPMN 候选组不动 ⇒ EXCLUDE 类基集也不变；
--   · 方案 enabled=1 且条件为空（无条件适用）；若解析异常，resolve() 抛 BizException
--     **不静默** —— 这是设计意图（禁止无人可办），故上线前必须确认交集非空（已断言）。
--
-- 【前置依赖】V1.4.62_post_backfill_seed.sql（sys_user.post_id 补数）。
--   实测 173 人已定岗，**每个岗位的 POST 维度都可解析出人（零在岗岗位 = 0）**，
--   故本件的 POST 维度不会因"岗位无人在岗"而解析失败。
--
-- 【回滚】V1.4.63_assignee_scheme_seed_rollback.sql：
--   ① 把 Task_7 的 assign_type 改回 ROLE（assign_value 本件未动，原样即可）；
--   ② 软删两套方案与其维度。
--   回滚后 Task_7 回到「走 ROLE 分支」，行为与本件执行前逐字一致。
-- =============================================================================

-- ----------------------------------------------------------------------------
-- §1 前置复点（只读；期望：post_notnull=173、Task_7 当前 assign_type=ROLE、方案表 0 行）
-- ----------------------------------------------------------------------------
-- SELECT COUNT(*) AS post_notnull FROM fssc.sys_user WHERE post_id IS NOT NULL;   -- 期望 173
-- SELECT node_key, node_name, assign_type, assign_value FROM fssc.wf_node_config
--  WHERE tenant_id=1 AND del_flag=0 AND model_key='CLAIM' AND node_key='Task_7';    -- 期望 ROLE / FSSC_AUDITOR
-- SELECT COUNT(*) FROM fssc.wf_assignee_scheme;                                   -- 期望 0
-- SELECT COUNT(*) FROM fssc.wf_assignee_dim;                                      -- 期望 0

-- ----------------------------------------------------------------------------
-- §2 执行段 A：建方案（幂等：先软删同键再插）
-- ----------------------------------------------------------------------------
DELETE FROM fssc.wf_assignee_scheme
 WHERE tenant_id = 1 AND model_key = 'CLAIM' AND node_key = 'Task_7';
DELETE FROM fssc.wf_assignee_dim
 WHERE tenant_id = 1 AND scheme_id IN (
   SELECT id FROM (SELECT id FROM fssc.wf_assignee_scheme
                    WHERE tenant_id=1 AND model_key='CLAIM' AND node_key='Task_7') x);

INSERT INTO fssc.wf_assignee_scheme
  (tenant_id, model_key, node_key, priority, condition_expr, combine_mode,
   enabled, sort, del_flag)
VALUES
  -- 注：wf_assignee_scheme 无 remark 列（见 DDL），说明性文字只留在本文件注释与 CHANGELOG。
  --     2026-10-15 P0-5：首套真跑方案。AND 双维度交集 = ROLE(FSSC_AUDITOR) ∩ POST(J0016)；
  --     实测交集精确等于现状 ROLE 取人的 4 人（3797/3820/3836/3898），A==B 已断言 ⇒ 取人结果不变。
  --     condition_expr 空 = 无条件适用；priority=1 = 只有这一套，无降级分支。
  (1, 'CLAIM', 'Task_7', 1, NULL, 'AND', 1, 10, 0);

-- 维度：必须拿到刚插入的 scheme.id，故用子查询锁定
INSERT INTO fssc.wf_assignee_dim
  (tenant_id, scheme_id, dim_type, dim_value, relation, up_level,
   include_children, sort, del_flag)
SELECT 1, s.id, 'ROLE', 'FSSC_AUDITOR', NULL, NULL, 0, 10, 0
  FROM fssc.wf_assignee_scheme s
 WHERE s.tenant_id=1 AND s.model_key='CLAIM' AND s.node_key='Task_7'
   AND s.del_flag=0 AND s.combine_mode='AND';

INSERT INTO fssc.wf_assignee_dim
  (tenant_id, scheme_id, dim_type, dim_value, relation, up_level,
   include_children, sort, del_flag)
SELECT 1, s.id, 'POST', 'J0016', NULL, NULL, 0, 20, 0
  FROM fssc.wf_assignee_scheme s
 WHERE s.tenant_id=1 AND s.model_key='CLAIM' AND s.node_key='Task_7'
   AND s.del_flag=0 AND s.combine_mode='AND';

-- ----------------------------------------------------------------------------
-- §2 执行段 B：改 Task_7 的 assign_type（ROLE → POST）
--     只改 assign_type 一列；assign_value 原样保留（FSSC_AUDITOR），便于回滚与留痕。
-- ----------------------------------------------------------------------------
UPDATE fssc.wf_node_config
   SET assign_type = 'POST'
 WHERE tenant_id = 1 AND del_flag = 0
   AND model_key = 'CLAIM' AND node_key = 'Task_7'
   AND assign_type = 'ROLE';   -- 幂等护栏：只在当前确为 ROLE 时才改，重复执行不会误伤

-- ----------------------------------------------------------------------------
-- §3 后置复点
-- ----------------------------------------------------------------------------
-- ① 节点已切 POST
-- SELECT node_key, assign_type, assign_value FROM fssc.wf_node_config
--  WHERE tenant_id=1 AND del_flag=0 AND model_key='CLAIM' AND node_key='Task_7';
--   期望 POST / FSSC_AUDITOR
--
-- ② 其余节点未动
--    执行后实测全表分布：ROLE 363 / INITIATOR 21 / POST 1 / USER 1 = 386 行
--    （落库前为 ROLE 364 / INITIATOR 21 / USER 1 ⇒ 恰好 1 行由 ROLE 变 POST）
-- SELECT assign_type, COUNT(*) FROM fssc.wf_node_config GROUP BY assign_type;
--
-- ③ 方案与维度就位
-- SELECT s.id, s.priority, s.combine_mode, s.enabled, d.dim_type, d.dim_value, d.sort
--   FROM fssc.wf_assignee_scheme s
--   LEFT JOIN fssc.wf_assignee_dim d ON d.scheme_id = s.id AND d.del_flag = 0
--  WHERE s.tenant_id=1 AND s.model_key='CLAIM' AND s.node_key='Task_7' AND s.del_flag=0
--  ORDER BY d.sort;
--   期望 1 套方案（priority=1 / AND / enabled=1）+ 2 个维度（ROLE 先 / POST 后）
--
-- ④ ★★ 决定性断言：AND 交集是否等于原 ROLE 取人（必须完全相同，否则回滚）
-- SELECT u.id, u.nick_name
--   FROM fssc.sys_user u
--   JOIN fssc.sys_user_role ur ON ur.user_id = u.id
--   JOIN fssc.sys_role r ON r.id = ur.role_id
--   JOIN fssc.sys_post p ON p.id = u.post_id
--  WHERE r.role_code = 'FSSC_AUDITOR' AND p.post_code = 'J0016'
--    AND u.status='NORMAL' AND u.is_admin=0 AND u.user_name <> 'admin'
--  ORDER BY u.id;
--   期望恰好 4 行：3797 共享会计-01 / 3820 共享会计-02 / 3836 成本费用岗-01 / 3898 共享会计-03
--
-- ⑤ 对照：现状 ROLE 分支口径（应与 ④ 完全一致）
-- SELECT u.id, u.nick_name
--   FROM fssc.sys_user u
--   JOIN fssc.sys_user_role ur ON ur.user_id = u.id
--   JOIN fssc.sys_role r ON r.id = ur.role_id
--  WHERE r.role_code = 'FSSC_AUDITOR' ORDER BY u.id;
--   期望同样 4 行且 id 完全相同
--
-- ⑥ 启动期 fast-fail 自检（可选，防"配了方案却解析不出人"）：
--   起一条 CLAIM 单据走到 Task_7，确认待办出现且 assignee 落在 ④ 的 4 人内；
--   若 resolve 抛「参与人解析失败」，说明前置 ④ 未成立 → 立即执行 rollback。

-- =============================================================================
-- 回滚（见 V1.4.63_assignee_scheme_seed_rollback.sql）
-- =============================================================================
-- ① assign_type 改回 ROLE（assign_value 本件未动，原样即可）
-- UPDATE fssc.wf_node_config SET assign_type = 'ROLE'
--  WHERE tenant_id=1 AND del_flag=0 AND model_key='CLAIM' AND node_key='Task_7'
--    AND assign_type = 'POST';
--
-- ② 软删方案与维度（保留行做痕迹，不物理删）
-- UPDATE fssc.wf_assignee_dim SET del_flag = 1
--  WHERE tenant_id=1 AND scheme_id IN (
--    SELECT id FROM (SELECT id FROM fssc.wf_assignee_scheme
--                     WHERE tenant_id=1 AND model_key='CLAIM' AND node_key='Task_7') x);
-- UPDATE fssc.wf_assignee_scheme SET del_flag = 1, enabled = 0
--  WHERE tenant_id=1 AND model_key='CLAIM' AND node_key='Task_7';