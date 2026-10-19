-- =============================================================================
-- V1.4.69 配置面/流程域枚举字典补齐（承接 C 类穷举对照_20261015.md §4 的 P1+P2）
--
-- 背景：C 类穷举发现 **4 个配置面枚举 + 1 个流程域枚举无字典覆盖**，
--   而 `wf_node_config` 这类列会直接决定取人/流转/超时行为，
--   枚举无字典 ⇒ 界面只能硬编码下拉、运维只能靠口口相传。
--   本件把这 5 类补齐，让「配置界面下拉」有字典来源可依。
--
-- 【取值来源：全部来自代码，不臆造】
--   assign_type      4 档← WfEngineFacade:1853/2138/2597/2751（"ROLE"/"USER"/"INITIATOR" 分支）+ V1.4.64 实测出现 "POST"
--   handling_mode    3 档  ← WfEngineFacade:1546(POOL)/2005(PICK)/2724(AUTO 缺省)
--   reject_scope     3 档  ← WfEngineFacade:5244(NONE)/5249(ANY_DONE)/5294(PREV_ONE)
--   timeout_action   4 档  ← WfTimeoutJob:156-158（ACT_REMIND/ACT_AUTO_PASS/ACT_ESCALATE）
--                              +:188（ACT_ESCALATE_LADDER 阶梯档）
--                              +:175/185（NOTIFY 与 AUTO_REMIND 归一后同档，但库中 16 个节点仍用 NOTIFY）
--   wf_history_action 9 档 ← wf_task_history.action 实测分布
--
-- 【标签来源：前端 enums.js 已维护带中文标签的映射，逐字取用】
--   enums.js: ASSIGN_TYPE / TIMEOUT_ACTION / MULTI_TYPE 等。
--    TIMEOUT_ACTION 在前端是**别名多对一**（NOTIFY/REMIND/URGE 都标"催办通知"）。
--      本字典的取舍（经落库校验后修正）：
--        · `AUTO_REMIND` 与 `NOTIFY` **都登记** —— WfTimeoutJob:175 明确两者归一后同档，
--          但库中 16 个节点存的是 `NOTIFY`；只登记规范值会让运维查不到这 16 个节点，
--          误判"配置坏了"。REMIND/URGE 等库中零使用的别名不登记，避免下拉出现无意义项。
--        · 其余库中未使用的别名（AUTO_TRANSFER/REDISTRIBUTE/URGE_THEN_ESCALATE 等）一律不登记。
--
-- 【为什么 assign_type 只灌 4 个而库里只用了 2 个】
--   ROLE 178 / POST 1，但代码有 USER / INITIATOR 两个分支（`:2138` USER 指人、
--   `:2597` INITIATOR 取提单人）—— 属**代码已支持、当前无配置使用**。
--   登记它们 = 界面配置时可选项与后端能力对齐，不会配出"后端不认"的组合。
--   （与 V1.4.64 的 POST 是同一个教训：当时软删 J0016 维度就是因为配置能选、后端取不到人。）
--
-- 【零风险边界】
--   · 只 INSERT sys_dict_type / sys_dict_data，**不改任何业务表的枚举列取值**；
--   · 不动 V1.4.67 / V1.4.68 已灌的 23 个容器；
--   · 幂等：先 DELETE 同 (dict_type,dict_value) 再插；
--   · wf_history_action 字典仅供展示；**MIRROR_DONE_ACTIONS（对账判据）保持代码内常量不动**，
--     改字典会静默改变双账对账口径（见 WfLedgerReconcileJob:196-198 注释）。
-- =============================================================================

-- ----------------------------------------------------------------------------
-- §1 前置复点（期望这 5 个 dict_type 均不存在）
-- ----------------------------------------------------------------------------
-- SELECT COUNT(*) FROM fssc.sys_dict_type WHERE dict_type IN
--  ('wf_assign_type','wf_handling_mode','wf_reject_scope','wf_timeout_action','wf_history_action');

-- ----------------------------------------------------------------------------
-- §2 容器
-- ----------------------------------------------------------------------------
INSERT INTO fssc.sys_dict_type (tenant_id,dict_type,dict_name,is_builtin,status,remark,create_by,del_flag) VALUES
 (1,'wf_assign_type',    '节点取人方式（wf_node_config.assign_type）',1,'NORMAL','取值须与 WfEngineFacade 消费分支一致',           NULL,0),
 (1,'wf_handling_mode',  '取人档位（wf_node_config.handling_mode）',1,'NORMAL','AUTO/POOL/PICK 三档，PICK 限定 ROLE',            NULL,0),
 (1,'wf_reject_scope',   '驳回范围（wf_node_config.reject_scope）',1,'NORMAL','G4 自由驳回的节点闸门，NONE=不放开',                 NULL,0),
 (1,'wf_timeout_action', '超时处置（wf_node_config.timeout_action）',1,'NORMAL','档位常量见 WfTimeoutJob:156-158/188',           NULL,0),
 (1,'wf_history_action', '流程留痕动作（wf_task_history.action）',1,'NORMAL','仅供展示；对账判据 MIRROR_DONE_ACTIONS 仍在代码内',   NULL,0)
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), remark=VALUES(remark);

-- ----------------------------------------------------------------------------
-- §3 数据
-- ----------------------------------------------------------------------------
DELETE FROM fssc.sys_dict_data WHERE tenant_id=1 AND dict_type IN
 ('wf_assign_type','wf_handling_mode','wf_reject_scope','wf_timeout_action','wf_history_action');

-- §3.1 assign_type（标签取自 enums.js ASSIGN_TYPE）
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'wf_assign_type','按角色','ROLE',    'primary',1,1,'NORMAL',NULL,0),
 (1,'wf_assign_type','按岗位','POST',    'primary',2,0,'NORMAL',NULL,0),
 (1,'wf_assign_type','指定人','USER',    'warning',3,0,'NORMAL',NULL,0),
 (1,'wf_assign_type','取提单人','INITIATOR','info',4,0,'NORMAL',NULL,0);

-- §3.2 handling_mode
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'wf_handling_mode','系统自动取人','AUTO','primary',1,1,'NORMAL',NULL,0),
 (1,'wf_handling_mode','任务池（先领取再办理）','POOL','warning',2,0,'NORMAL',NULL,0),
 (1,'wf_handling_mode','上一环点名指定','PICK','info',3,0,'NORMAL',NULL,0);

-- §3.3 reject_scope
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'wf_reject_scope','不放开（驳回即终止流程）','NONE','danger',1,1,'NORMAL',NULL,0),
 (1,'wf_reject_scope','仅退回上一节点','PREV_ONE','warning',2,0,'NORMAL',NULL,0),
 (1,'wf_reject_scope','可退至任意已办结节点','ANY_DONE','info',3,0,'NORMAL',NULL,0);

-- §3.4 timeout_action
--    NOTIFY **必须登记**：它是 AUTO_REMIND 的历史遗留拼写，库中 16 个节点在用
--   （WfTimeoutJob:175 明写「只有 NOTIFY(357) 与 AUTO_REMIND(256) 两种值——
--   两个拼写归一后是同一档」；:185「配了 NOTIFY 的节点仍只催办」）。
--   本字典**两者都登记**（而非只留规范值），否则运维看到这 16 个节点的值在字典里查不到，
--   会误判成"配置坏了"。下拉时把 NOTIFY 排在 AUTO_REMIND 之后并标注"同义"。
--    其余前端别名（REMIND/URGE/TRANSFER/AUTO_ESCALATE/REDISTRIBUTE/
--      URGE_THEN_ESCALATE/REMIND_THEN_ESCALATE）**库中零使用**，不登记，避免下拉出现无意义项。
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag,remark) VALUES
 (1,'wf_timeout_action','催办通知','AUTO_REMIND','info',1,1,'NORMAL',NULL,0,'规范档位，前端别名 NOTIFY/REMIND/URGE'),
 (1,'wf_timeout_action','催办通知（旧拼写）','NOTIFY','info',2,0,'NORMAL',NULL,0,'与 AUTO_REMIND 同义，库中 16 个节点在用，勿删'),
 (1,'wf_timeout_action','超时自动通过','AUTO_PASS','success',3,0,'NORMAL',NULL,0,'须同时在 sys_config.wf.timeout.autopass.nodes 白名单内'),
 (1,'wf_timeout_action','超时升级转办','ESCALATE','warning',4,0,'NORMAL',NULL,0,'SLA 一到立即沿汇报线升，前端别名 AUTO_ESCALATE/TRANSFER'),
 (1,'wf_timeout_action','催办N 次后升级','ESCALATE_AFTER_URGES','warning',5,0,'NORMAL',NULL,0,'阶梯档：累计满 sys_config.wf.timeout.escalate.after_urges（缺省3）次才升');

-- §3.5 wf_history_action（9 档，中文按动作语义命名）
--   注意 APPROVE(任务级) 与 APPROVED(实例级) 并存且语义不同，故都登记。
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'wf_history_action','提交发起','SUBMIT',       'primary',1,0,'NORMAL',NULL,0),
 (1,'wf_history_action','审批通过','APPROVE',      'success',2,0,'NORMAL',NULL,0),
 (1,'wf_history_action','审批通过(实例级)','APPROVED','success',3,0,'NORMAL',NULL,0),
 (1,'wf_history_action','作废','VOID',            'danger',4,0,'NORMAL',NULL,0),
 (1,'wf_history_action','撤销','CANCEL',          'info',5,0,'NORMAL',NULL,0),
 (1,'wf_history_action','发起加签','ADDSIGN',      'warning',6,0,'NORMAL',NULL,0),
 (1,'wf_history_action','加签办结','ADDSIGN_DONE', 'warning',7,0,'NORMAL',NULL,0),
 (1,'wf_history_action','催办升级','ESCALATE',     'warning',8,0,'NORMAL',NULL,0),
 (1,'wf_history_action','改派','ASSIGN',           'info',9,0,'NORMAL',NULL,0);

-- ----------------------------------------------------------------------------
-- §4 执行后复点（期望：28 个容器 / 112 行）
-- ----------------------------------------------------------------------------
-- SELECT COUNT(*) FROM fssc.sys_dict_type WHERE del_flag=0;
-- SELECT COUNT(*) FROM fssc.sys_dict_data WHERE del_flag=0;
-- SELECT dict_type, COUNT(*) FROM fssc.sys_dict_data WHERE del_flag=0 GROUP BY dict_type ORDER BY dict_type;

-- 校验①：与 wf_node_config 实际取值比对（本件新增 4 类应无遗漏）
-- SELECT DISTINCT assign_type FROM fssc.wf_node_config WHERE del_flag=0
--   AND assign_type NOT IN (SELECT dict_value FROM fssc.sys_dict_data
--                          WHERE dict_type='wf_assign_type' AND del_flag=0);
-- 同理验handling_mode / reject_scope / timeout_action 四列。

-- 校验②：与 wf_task_history 实际 action 比对（期望 0 行）
-- SELECT DISTINCT action FROM fssc.wf_task_history
--  WHERE action NOT IN (SELECT dict_value FROM fssc.sys_dict_data
--                       WHERE dict_type='wf_history_action' AND del_flag=0);