-- =============================================================================
-- V1.4.65 字段级表单权限 form_perm 首批落地（5-A）
--
-- 目的：让 `WfFormPermGuard` 的服务端硬校验**首次真正生效**。
--       此前 `wf_node_config.form_perm` 全库 390 行**非空 0** ⇒ 守卫恒 fail-open，
--       `assertFieldsWritable` 形同虚设，绕过前端直调 `/approve-deduct/{id}` 可改核定金额与审批意见。
--
-- 【载体纠正 —— 不是独立表】
--   `form_perm` **不是一张表**（库中无此表），而是 `wf_node_config` 的**一列**
--   （`WfNodeConfig.java:65 private String formPerm`），JSON 扁平结构 {"字段":"档位"}。
--   解析器 `WfNodeConfigResolver.resolveFormFieldPerm:121`，规则三条：
--     · 取严不取宽（同字段多档取H>R>W）；
--     · **fail-open**（未配置/配置损坏 → 空 Map 不裁剪）
--       ⇒ **限制性规则必须把字段写全**，否则漏写的字段等于放行；
--     · 档位别名归一（h/hidden→H、r/ro/readonly→R、w/write/editable→W），
--       单字段非法只跳过该字段、不整表失败。
--
-- 【生效范围（重要，避免误以为全字段都被保护）】
--   `WfFormPermGuard` 当前**仅一个调用点**：`ExpenseBillService.java:1325`
--   （端点 `POST /api/.../exp/bill/approve-deduct/{id}`，权限 `exp:bill:audit`），
--   提交字段恰好 4 个（该端点的全部表头写入字段，2026-10-14 已补齐 auditRemark）：
--       approvedAmount / approvedPayAmount / approvedRefundAmount / auditRemark
--   ⇒ 本件只对这 4 个字段有效；其余字段（如 total_amount/payable_amount）配了也不会被校验，
--      **扩域需先在对应业务 Service 补 guard 调用点**。
--
-- 【本件配了两个节点，构成一组有业务含义的对照】
--   ① CLAIM/Task_7「财务共享审核单据【共享会计】」
--      核定三层是财务共享的职责 ⇒ 4 字段全 **W**（可编辑）。
--      作用：作为"放行侧"基线，证明配了 W 不改变任何现有行为（该节点当前审批人正是核定人）。
--   ② CLAIM/Task_13「总裁审批【总裁】」
--      总裁是决策岗，**不应核减金额** ⇒ 3 个金额字段 **R**（只读，禁止变更），
--      但 `auditRemark` 保留 **W**（决策意见必须能写）。
--      作用：这是**真实的越权面** —— 绕过界面直调即可在总裁节点擅自改动核定金额。
--
-- 【为什么首批只配这两个节点】
--   · 扩到全库 179 个存活节点会给每个节点都加一层拦截，一旦档位配错就是"该填的填不了"，
--     属于 fail-closed 方向的业务事故；首批只配**语义确定**的两个节点；
--   · Task_13 的配置是"窄而准"：只锁金额、不锁意见，符合最高决策岗的实际职责。
--
-- 【零影响边界】
--   · 只写 `form_perm` 一列，不动assign_type / assign_value / multi_type 等任何其他列；
--   · 未配 form_perm 的节点（含 CLAIM 其余节点与全部其他模型）行为逐字不变；
--   · 前端 `GET /wf/task-form-perm` 会自动拿到这两份配置并对 Task_7/Task_13 做
--     隐藏/只读渲染 ⇒ 前端裁剪与服务端校验**同源同解析器**，不会出现"界面能填但后端拒绝"的错位。
--   · 回滚＝把两行 form_perm 置 NULL（见 V1.4.65_..._rollback.sql），无其他连带。
-- =============================================================================

-- ----------------------------------------------------------------------------
-- §1 前置复点（只读；期望：nonempty=0，即全库尚无 form_perm 配置）
-- ----------------------------------------------------------------------------
-- SELECT COUNT(*) AS nonempty FROM fssc.wf_node_config
--  WHERE form_perm IS NOT NULL AND TRIM(form_perm) <> '';
--
-- 确认两个目标节点存活（期望各 1 行）：
-- SELECT node_key, node_name, assign_value FROM fssc.wf_node_config
--  WHERE model_key='CLAIM' AND node_key IN ('Task_7','Task_13') AND del_flag=0;

-- ----------------------------------------------------------------------------
-- §2 执行段（去重）
-- ----------------------------------------------------------------------------

-- §2.1 Task_7 共享会计：核定三层 + 审批意见均可写（放行侧基线）
UPDATE fssc.wf_node_config
   SET form_perm = '{"approvedAmount":"W","approvedPayAmount":"W","approvedRefundAmount":"W","auditRemark":"W"}',
       update_by = NULL,
       update_time = NOW()
 WHERE model_key = 'CLAIM'
   AND node_key = 'Task_7'
   AND del_flag = 0;

-- §2.2 Task_13 总裁审批：金额三项只读、意见可写（办结真实越权面）
UPDATE fssc.wf_node_config
   SET form_perm = '{"approvedAmount":"R","approvedPayAmount":"R","approvedRefundAmount":"R","auditRemark":"W"}',
       update_by = NULL,
       update_time = NOW()
 WHERE model_key = 'CLAIM'
   AND node_key = 'Task_13'
   AND del_flag = 0;

-- ----------------------------------------------------------------------------
-- §3 执行后复点（期望：nonempty=2；且两行内容与上面逐字一致）
-- ----------------------------------------------------------------------------
-- SELECT node_key, form_perm FROM fssc.wf_node_config
--  WHERE model_key='CLAIM' AND node_key IN ('Task_7','Task_13') AND del_flag=0;
--
-- JSON 合法性自检（两条均应返回 1）：
-- SELECT JSON_VALID(form_perm) AS ok FROM fssc.wf_node_config
--  WHERE model_key='CLAIM' AND node_key IN ('Task_7','Task_13') AND del_flag=0;