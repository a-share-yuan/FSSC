-- =============================================================================
-- V1.4.61 流程节点审批项 wf_approval_item 灌 seed（P0-2，修订版）
--
-- 【本件为何不是"灌 wf_assignee_scheme"】
--   复核阶段原计划灌 wf_assignee_scheme/wf_assignee_dim，但逐条读码后**证伪**：
--   · WfEngineFacade.java:1645 的 switch 中，`applyAssigneeSchemes(...)` 只挂在 **default 分支**；
--     显式分支为 ROLE / USER / INITIATOR / DEPT_LEADER / DEPT_LEADER_UP。
--   · 实测（2026-10-15 现查）wf_node_config 全表 386 行（含软删）的 assign_type 分布为：
--       ROLE 364 / INITIATOR 21 / USER 1 —— **POST / SHARE_POOL / EXPRESSION 历史上从未出现过一行**。
--   ⇒ 没有任何节点会落进 default 分支，灌 wf_assignee_scheme **一行也不会生效**（空转）。
--   ⇒ 本件改为灌 wf_approval_item：它由审批弹窗 `/approval-items` 直接读、
--     与 `/approve` 的 approvalItemCode 同源同解析器，**不经过 assign_type switch**，且
--     实体 javadoc 明写「零影响边界：库里没有任何行时 complete() 收不到 approvalItemCode，
--     全部行为与改造前逐字一致」——是当前唯一"灌了立刻见效且可一键回退"的目标。
--   （wf_assignee_scheme 的真解法是把某节点的 assign_type 改成 POST 并配方案，
--     属"改现有取人语义"，风险与决策成本都更高，另案裁决，不在本件。）
--
-- 消费链（已逐段读码确认闭合）：
--   GET  /api/workflow/task/approval-items?taskId=…
--        → WfEngineFacade.approvalItemsOf(#2395)
--        → approvalItemService.listEnabled(modelKey, task.getTaskDefinitionKey())
--   POST /api/workflow/task/approve  {approvalItemCode}
--        → WfEngineFacade.complete(..., approvalItemCode) 落流程变量 ActionResult/ActionResultName
--   两端同源同一解析器，不存在"看得见点不动"。
--
-- modelKey 对位（已核）：approvalItemsOf 取 procDefId.split(":")[0]；
--   ACT_RE_PROCDEF 实测 CLAIM 段 ID_ 形如 'CLAIM:232:c6c2…' ⇒ split(":")[0] == 'CLAIM'
--   与 wf_approval_item.model_key、wf_node_config.model_key 逐字同源 
--
-- 试点范围（收窄口径）：
--   只给 CLAIM 的 3 个节点配审批项，且只配 2 类结果（POSITIVE/REJECT）——
--   **TERMINATE 本件一律不配**：终止是不可逆动作，需业务先签字确认语义，
--   不与"把按钮点亮"混在同一件里。
--
-- 零影响边界复核：
--   · item_code 落流程变量 ActionResult，wf_task_history.action 保持枚举
--     （实体 javadoc：action 是 varchar(20) NOT NULL 且 WfQueryService:676 用
--      "REJECT".equals(action) 反推实例状态，写文案会误判 —— 本件严格遵守，不碰 action）；
--   · REJECT 项的 target_node 留空 ⇒ 落回原「终止本流程」路径，与改造前逐字一致
--     （target_node 只在 wf.reject.free.enabled 总闸放行时才搬执行，现该键=0，闸门本就是关的）。
-- =============================================================================

-- ----------------------------------------------------------------------------
-- §1 前置复点（只读；期望 items=0，且三个目标 node_key 均存在于 wf_node_config）
-- ----------------------------------------------------------------------------
-- SELECT COUNT(*) AS items FROM fssc.wf_approval_item WHERE del_flag = 0;
--   期望 0
-- SELECT node_key, node_name FROM fssc.wf_node_config
--  WHERE del_flag = 0 AND model_key = 'CLAIM'
--    AND node_key IN ('Task_5','Task_11','Task_13');
--   期望 3 行：Task_5=部门经理审核、Task_11=财务总监审核、Task_13=总裁审批
--
-- 旁证：确认这三枚是 CLAIM 上的人工审批节点
-- SELECT node_key, node_name, handling_mode FROM fssc.wf_node_config
--  WHERE del_flag = 0 AND model_key='CLAIM' AND node_key IN ('Task_5','Task_11','Task_13');
--   实测 2026-10-15：Task_5=PICK(部门经理挑选)、Task_11=AUTO(财务总监)、Task_13=AUTO(总裁)
--   —— 三者均非 POOL 取数节点，故均可挂审批项。

-- ----------------------------------------------------------------------------
-- §2 执行段（幂等：先按 (tenant, model, node, item_code) 删，再插）
-- ----------------------------------------------------------------------------
DELETE FROM fssc.wf_approval_item
 WHERE tenant_id = 1 AND model_key = 'CLAIM'
   AND node_key IN ('Task_5','Task_11','Task_13')
   AND item_code IN ('APPROVE','REJECT');

INSERT INTO fssc.wf_approval_item
  (tenant_id, model_key, node_key, item_code, item_name, category,
   linked_oper, target_node, sort, enabled, remark, create_by, del_flag)
VALUES
  -- 节点一：部门经理审核（Task_5，DEPT_MANAGER）
  (1, 'CLAIM', 'Task_5', 'APPROVE', '同意', 'POSITIVE', 'AUDIT', NULL, 10, 1,
   '2026-10-15 P0-2：正向审批项。落流程变量 ActionResult=APPROVE / ActionResultName=同意；'
   '不写 wf_task_history.action（该列须保持枚举，见实体 javadoc）。', NULL, 0),
  (1, 'CLAIM', 'Task_5', 'REJECT', '驳回', 'REJECT', 'UNAUDIT', NULL, 20, 1,
   '2026-10-15 P0-2：驳回审批项。target_node 留空 ⇒ 落回原「终止本流程」路径（逐字不变）；'
   '要真退到上游节点需先开 sys_config.wf.reject.free.enabled（现值 0，闸门关）。', NULL, 0),

  -- 节点二：财务总监审核（Task_11，CFO）
  (1, 'CLAIM', 'Task_11', 'APPROVE', '同意', 'POSITIVE', 'AUDIT', NULL, 10, 1,
   '2026-10-15 P0-2：正向审批项，同 Task_5 口径。', NULL, 0),
  (1, 'CLAIM', 'Task_11', 'REJECT', '驳回', 'REJECT', 'UNAUDIT', NULL, 20, 1,
   '2026-10-15 P0-2：驳回审批项，target_node 留空。', NULL, 0),

  -- 节点三：总裁审批（Task_13，PRESIDENT）
  (1, 'CLAIM', 'Task_13', 'APPROVE', '同意', 'POSITIVE', 'AUDIT', NULL, 10, 1,
   '2026-10-15 P0-2：正向审批项，同 Task_5 口径。', NULL, 0),
  (1, 'CLAIM', 'Task_13', 'REJECT', '驳回', 'REJECT', 'UNAUDIT', NULL, 20, 1,
   '2026-10-15 P0-2：驳回审批项，target_node 留空。', NULL, 0);

-- ----------------------------------------------------------------------------
-- §3 后置复点
-- ----------------------------------------------------------------------------
-- SELECT model_key, node_key, item_code, item_name, category, target_node, enabled
--   FROM fssc.wf_approval_item WHERE tenant_id=1 AND del_flag=0
--  ORDER BY node_key, sort;
--   期望 6 行：Task_5/Task_11/Task_13 各 2 条（APPROVE=POSITIVE、REJECT=REJECT），target_node 全 NULL
--
-- 孤儿检查（期望 0 行：node_key 必须能在 wf_node_config 里对上，否则引擎取不到）
-- SELECT DISTINCT a.node_key FROM fssc.wf_approval_item a
--  LEFT JOIN fssc.wf_node_config n
--    ON n.tenant_id = a.tenant_id AND n.model_key = a.model_key
--   AND n.node_key = a.node_key AND n.del_flag = 0
--  WHERE a.del_flag = 0 AND n.id IS NULL;
--
-- 按钮可见性复点（需真跑，因无在办任务时接口返空数组）：
--   取任一 CLAIM 在办任务 → GET /api/workflow/task/approval-items?taskId=<id>
--   期望返回 2 项（同意/驳回），前端审批弹窗据此渲染自定义按钮。
--   注：当前库 CLAIM/LOAN 无 PENDING 在办任务（wf_task 中 status='PENDING' 且 model_key IN
--   ('CLAIM','LOAN') 命中 0 行），故本轮只能做配置侧验证，界面腿待有在办单据时补。

-- =============================================================================
-- 回滚
-- =============================================================================
-- DELETE FROM fssc.wf_approval_item
--  WHERE tenant_id = 1 AND model_key = 'CLAIM'
--    AND node_key IN ('Task_5','Task_11','Task_13')
--    AND item_code IN ('APPROVE','REJECT');
--
-- 回滚后复点（期望 0）
-- SELECT COUNT(*) FROM fssc.wf_approval_item WHERE tenant_id=1 AND del_flag=0;
--
-- 回滚后行为：/approval-items 返空数组 ⇒ 审批弹窗不渲染自定义按钮，
--             与 2026-10-15 之前完全一致（实体 javadoc 的零影响边界）。