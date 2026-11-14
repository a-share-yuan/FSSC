-- =============================================================================
-- V1.4.59 加签校验两枚 sys_config 键 seed（P0-1）
--
-- 目的：把「allow_addsign 节点级开关」从"界面不显示、运行时零读取"接进运行时。
--       代码读点已就位（WfEngineFacade.isAddSignGateEnabled:1964 / addSignGateModels:1971 /
--       isConfigGatedOn:3815），缺的是校验键本身 —— sys_config 里查无此二键，
--       故 isConfigGatedOn 恒 false，26 枚 allow_addsign=1 与 150 枚 =0 行为完全一致。
--
-- 灰度依据（实测，2026-10-15 现查 fssc@127.0.0.1:3306）：
--   · wf_node_config 全表 386 行：allow_addsign=0 → 352，=1 → 34
--   · 存活行（del_flag=0）176 行：=0 → 150，=1 → 26
--   · allow_addsign=1 的节点只分布在 4 个模型：CLAIM 12 / LOAN 12 / TR_PAY 1 / SETTLE 1
--   ⇒ 灰度只放 CLAIM、LOAN 两个模型（覆盖 24 枚 =1 节点所在的模型），
--     其余 42 个模型保持缺省=不拦，现行为逐字不变。
--
-- 校验键语义（见 WfEngineFacade:3814-3822 注释）：
--   enabled=1 且 modelKey ∈ models 才拦；enabled=1 且 models 为空 ⇒ Set.of() ⇒ 任何模型都不拦；
--   两键缺任一 ⇒ 不拦。本件即取"窄灰度"：只让 CLAIM/LOAN 真拦。
--
-- 影响面（灰度内）：CLAIM/LOAN 流程中若某节点 allow_addsign=0，
--   用户点加签将被 403 拒绝并给出可读原因（提示联系流程管理员）。
--   这是把"静默可加签"改成"按配置拒绝" —— 是修正，不是新增限制。
--
-- 前置校验（已实测）：allow_addsign=0 的节点在 CLAIM/LOAN 内确实存在，
--   即本件一落即产生可观测行为差异，非空转。
-- =============================================================================

-- ----------------------------------------------------------------------------
-- §1 前置复点（只读，执行前人工确认；期望：keys=0，CLAIM/LOAN 的 0 值节点数 > 0）
-- ----------------------------------------------------------------------------
-- SELECT COUNT(*) AS gate_keys_present
--   FROM fssc.sys_config
--  WHERE config_key IN ('approve.addsign_gate.enabled','approve.addsign_gate.models');
--   期望 0 —— 两键当前确实缺失
--
-- SELECT model_key, COUNT(*) AS zero_nodes
--   FROM fssc.wf_node_config
--  WHERE del_flag = 0 AND allow_addsign = 0 AND model_key IN ('CLAIM','LOAN')
--  GROUP BY model_key;
--   期望 CLAIM/LOAN 各 > 0 —— 灰度内确有"该禁却被放行"的节点
--
-- SELECT model_key, COUNT(*) AS one_nodes
--   FROM fssc.wf_node_config
--  WHERE del_flag = 0 AND allow_addsign = 1 AND model_key IN ('CLAIM','LOAN')
--  GROUP BY model_key;
--   期望 CLAIM 12 / LOAN 12

-- ----------------------------------------------------------------------------
-- §2 执行段（去重：先删同键再插，避免重复键与历史值漂移）
-- ----------------------------------------------------------------------------
DELETE FROM fssc.sys_config
 WHERE config_key IN ('approve.addsign_gate.enabled','approve.addsign_gate.models');

INSERT INTO fssc.sys_config
  (tenant_id, config_key, config_name, config_value, value_type,
   group_code, is_builtin, editable, remark, create_by, del_flag)
VALUES
  (1, 'approve.addsign_gate.enabled', '加签闸口总开关',
   '1', 'BOOLEAN',
   'WORKFLOW', 0, 1,
   '2026-10-15 P0-1：1=按节点 allow_addsign 真拦加签；0 或缺键=不拦（保持现行为）。'
   '读取方 WfEngineFacade.isAddSignGateEnabled(#1964)。窄灰度：配合 .models 仅放 CLAIM/LOAN。',
   NULL, 0),

  (1, 'approve.addsign_gate.models', '加签闸口灰度模型清单',
   'CLAIM,LOAN', 'STRING',
   'WORKFLOW', 0, 1,
   '2026-10-15 P0-1：逗号分隔的 model_key 清单，只有清单内模型才受闸口约束；'
   '空值/缺键=任何模型都不拦。读取方 WfEngineFacade.addSignGateModels(#1971)。'
   '当前灰度 CLAIM,LOAN（allow_addsign=1 的 26 枚节点全在这 4 个模型：CLAIM12/LOAN12/TR_PAY1/SETTLE1）。',
   NULL, 0);

-- ----------------------------------------------------------------------------
-- §3 后置复点（期望：2 行，值 1 / CLAIM,LOAN）
-- ----------------------------------------------------------------------------
-- SELECT config_key, config_value, editable
--   FROM fssc.sys_config
--  WHERE config_key IN ('approve.addsign_gate.enabled','approve.addsign_gate.models');
--
-- 行为对位复点（期望：校验生效后，CLAIM/LOAN 内 allow_addsign=0 的节点加签将被拒）
-- SELECT model_key,
--        SUM(allow_addsign = 0) AS blocked_now,
--        SUM(allow_addsign = 1) AS still_allowed
--   FROM fssc.wf_node_config
--  WHERE del_flag = 0 AND model_key IN ('CLAIM','LOAN')
--  GROUP BY model_key;

-- =============================================================================
-- 回滚（若灰度出问题，立刻执行；注意 sys_config 有 tenant_id 维度）
-- =============================================================================
-- DELETE FROM fssc.sys_config
--  WHERE config_key IN ('approve.addsign_gate.enabled','approve.addsign_gate.models');
--
-- 回滚后复点（期望 0）
-- SELECT COUNT(*) FROM fssc.sys_config
--  WHERE config_key IN ('approve.addsign_gate.enabled','approve.addsign_gate.models');
--
-- 回滚规则说明：回滚即"回到本件写入数据库前的状态"——两键本不存在，
-- 故回滚只有 DELETE 一条路径，不需还原旧值（这是"缺键补键"型 seed 的固有好处）。