-- =============================================================================
-- V1.4.73  八类差距收尾①：SLA 维护权限码 + wf_approval_item 首批审批项种子
-- 日期：2026-10-17
-- 背景：FSSC未实现项归类复核_8类框架_20261014.md
--   §4-① 灌 wf_approval_item（10-15 报告称 6 行，实际库中 0 行）
--   §4-⑨ op_sla_config 补维护界面（后端此前只有 list/check，无写端点）
--
-- 本脚本只做「配置面」两件事，不改任何业务代码语义：
--   1) 补 gl:sla:create / update / delete 三个权限码（后端端点本次已补齐）
--   2) 灌 CLAIM 主线 3 个节点的审批项，其中 POSITIVE 立即启用、
--      REJECT / TERMINATE 建档但 enabled=0（见文末「为什么驳回/终止不启用」）
-- 回滚：见同名 _rollback.sql
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. SLA 维护权限码（挂 parent_id=105「共享运营」，与既有 gl:sla:list/check 同父）
--    列取值对齐既有行 3433：feature_code='FSSC_SHARED'（端点带 @RequiresFeature）、
--    terminal='PC'、visible=1、status=1、del_flag=0。
--    id 取 3775 起（执行时 sys_menu MAX(id)=3774，3775~3780 已确认空闲）。
-- ---------------------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, feature_code, terminal, sort, visible, status, del_flag)
VALUES
  (3775, 105, 'SLA配置新增', 'F', 'gl:sla:create',  'FSSC_SHARED', 'PC', 3451, 1, 1, 0),
  (3776, 105, 'SLA配置修改', 'F', 'gl:sla:update',  'FSSC_SHARED', 'PC', 3452, 1, 1, 0),
  (3777, 105, 'SLA配置删除', 'F', 'gl:sla:delete',  'FSSC_SHARED', 'PC', 3453, 1, 1, 0);

-- 授权给既有的 gl:sla:list 持有角色（执行时 role 37 SHARING_SUPPORT / 61 FSSC_SUPERVISOR，
-- tenant_id 均为 1），保持「能查就能维护」的一致性。
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_time)
SELECT 1, rm.role_id, m.id, NOW()
FROM sys_role_menu rm
JOIN sys_menu src ON src.id = rm.menu_id AND src.perms = 'gl:sla:list'
JOIN sys_menu m   ON m.id IN (3775, 3776, 3777)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_menu d
  WHERE d.role_id = rm.role_id AND d.menu_id = m.id
);

-- ---------------------------------------------------------------------------
-- 2. wf_approval_item 首批种子（CLAIM 报销主线，tenant_id=1）
--
--    node_key 取自「引擎里实际部署的 CLAIM v237」BPMN 的 userTask id
--    （从 act_ge_bytearray 读 CLAIM.bpmn20.xml 实得 21 个 userTask，与 wf_node_config
--      的 CLAIM 节点逐一对齐，无孤儿行）——不是按设计位置编号猜的。
--
--    enabled 策略：
--      POSITIVE（正向，继续流转）→ enabled=1，立即可用，零行为风险
--        依据 WfEngineFacade.complete:3527：POSITIVE 分支只多写
--        ActionResult / ActionResultName / ActionOper 三个流程变量 + 意见前缀。
--      REJECT / TERMINATE → enabled=0，建档待用
--        这两类会改写流程走向（3531-3539 分支），属行为变更，需业务确认后再启用。
--        审批项在弹窗里是「附加的可选标注」而非替代同意/驳回主按钮，
--        故停用不影响原审批路径。
-- ---------------------------------------------------------------------------
INSERT INTO wf_approval_item
  (tenant_id, model_key, node_key, item_code, item_name, category, linked_oper, target_node, sort, enabled, remark, del_flag)
VALUES
  -- Task_2 扫描单据【财务扫描岗】：扫描岗判定单据是否受理
  (1, 'CLAIM', 'Task_2',  'SCAN_PASS',    '扫描无误·提交审核', 'POSITIVE', 'SUBMIT',   NULL, 1, 1, '扫描岗确认单据要件齐全', 0),
  (1, 'CLAIM', 'Task_2',  'SCAN_RETURN',  '退回重扫',         'REJECT',   'SAVE',     'Task_2', 2, 0, '要件不全退回扫描岗（驳回闸门放行时生效）', 0),
  (1, 'CLAIM', 'Task_2',  'SCAN_VOID',    '单据无效·终止',    'TERMINATE','UNAUDIT',  NULL, 3, 0, '重复/无效单据终止流程', 0),
  -- Task_7 财务共享审核单据【共享会计】：与已写入数据库的 form_perm 核减权限配套
  (1, 'CLAIM', 'Task_7',  'FS_PASS',      '审核通过',         'POSITIVE', 'AUDIT',    NULL, 1, 1, '共享会计审核通过', 0),
  (1, 'CLAIM', 'Task_7',  'FS_ADJUST',    '核减后通过',       'POSITIVE', 'AUDIT',    NULL, 2, 1, '核定金额有核减（配合 Task_7 form_perm 四字段可写）', 0),
  (1, 'CLAIM', 'Task_7',  'FS_REJECT',    '驳回至扫描',       'REJECT',   'SAVE',     'Task_2', 3, 0, '退回扫描岗重报', 0),
  -- Task_13 总裁审批【总裁】：决策岗
  (1, 'CLAIM', 'Task_13', 'GM_PASS',      '同意',             'POSITIVE', 'AUDIT',    NULL, 1, 1, '总裁同意', 0),
  (1, 'CLAIM', 'Task_13', 'GM_REJECT',    '驳回',             'REJECT',   'SAVE',     'Task_2', 2, 0, '驳回（target_node 待业务确认后启用）', 0),
  (1, 'CLAIM', 'Task_13', 'GM_TERMINATE', '终止流程',         'TERMINATE','UNAUDIT',  NULL, 3, 0, '大额异常终止', 0);

-- ---------------------------------------------------------------------------
-- 为什么驳回/终止默认不启用
--   REJECT 需「自由驳回」校验（wf.reject.free.enabled，当前默认 0）放行才搬执行，
--   否则按 WfEngineFacade 代码落回「终止本流程」——比原「驳回」更重，属行为变更。
--   不该由灌数据脚本替业务做主，故建档（可在管理页改 enabled），默认停用。
-- ---------------------------------------------------------------------------
