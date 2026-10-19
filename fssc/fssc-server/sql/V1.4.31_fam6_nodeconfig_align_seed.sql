-- V1.4.31  批次2 第六族（资金 TR_* + PAY_PLAN_BASE/PAY_PLAN_PLATE）配置侧对齐：wf_node_config <- 已发布 BPMN 唯一真值
-- 前置：13 个模型已 /api/tenant/wf/designer/publish 重发布（PAY_PLAN_BASE / PAY_PLAN_PLATE / TR_PAY / TR_PAY_FAIL / TR_PLAN / TR_PLAN_ADJUST / TR_TRANSFER / TR_UPLOAD / TR_RECEIPT / TR_BILL / TR_BANK_RECON / TR_BANK_MASTER / TR01-02），
--   发布前回读复核 13/13 与 draft 逐字节一致（e2e-ui/batch2/fam6-verify.log）、在途实例双口径均为 0
--   （designer/in-flight runningTotal=0 且 staleCount=0；e2e-ui/batch2/fam6-publish-check.log）。
-- 结构：本族 13 模型 88 个人工节点 -> 48 个（-40，-45%）；
--   作业池 11 个（handling_mode=POOL）、排他网关 10 个（金额 DOA 阶梯，每档显式默认流）、并联会审 1 组
--   （PAY_PLAN_PLATE 项目/基地财务/资金支付/板块财务 4 出 4 汇聚，原 5 出中"采购经理会审"＝提单角色自会签已删）。
-- 相邻同角色：重造前 7 对（TR_PAY_FAIL 资金支付岗 Task_1→2→3 三连＋普通员工 Task_4→5 共 3 对、TR_PLAN/TR_UPLOAD 资金岗各 1 对、
--   TR_BILL 资金支付岗 1 对、PAY_PLAN_PLATE 项目经理 1 对）-> 重造后 0 对，
--   且【同一模型内同一角色只出现一次】（build.js 自检断言）。
-- DOA：TR_PAY 原四档逐字保留（50万基地总经理/100万板块财务经理/200万财务总监/500万总裁）；
--   TR_TRANSFER 原三档（100/200/500万）保留但入口修好——Task_1"发起资金调拨申请"是提单动作占人工节点，改为资金支付岗审核调拨与头寸；
--   PAY_PLAN_PLATE 补 100万→财务总监、500万→总裁（原 VP/板块财务总监 两签是同人常签的中间层）；
--   TR_PLAN_ADJUST 原 8 道纯串行 0 网关＝每单必签基地总经理＋总裁（假 DOA），改为 3 道＋"调整后总额超 500 万→财务总监"一档；
--   不设金额档的三类理由（逐模型取证，见 build.js 各 note）：
--     · 根本没传 amount → 挂档必假：TR_BILL / TR_BANK_RECON / TR_BANK_MASTER 走 WfEngineFacade 四参 start()（amount=null→0＋显式 WARN，R6-02）；
--     · 传了 amount 但本链不新增支出授权：TR_PAY_FAIL（钱已在 TR_PAY 四档批过，此处只把 op_pay_batch 从 FAILED 恢复到可重试）、
--       TR_UPLOAD（同法人体系内下划上存归集，不对外付款）、TR_RECEIPT（收入侧到款确认）；
--     · 按流程性质不设档：TR_PLAN（计划类按组织层级授权，且 start 传 income_amount 而列表展示 outcome_amount，口径不一致 R6-04）、
--       TR_BANK_MASTER/TR01-02 主数据类（§5.3）；TR01-02 另有"无 Java 调用方、结构性不可达"（R6-05）。
-- 已出链旧节点的配置行按批次2口径【保留不删】（DB 增量原则）：引擎按 BPMN 节点反查配置，幽灵行不产生运行时影响，仅在复核 C 盘点计数。
-- 本文件做三件事：1) 48 个已存在节点对齐 node_name/assign_type/assign_value/handling_mode/empty_strategy/超时；
--   2) 0 个新 key 插行；3) 复核 A~F。
-- 改岗清单（BPMN candidateGroups 与旧 assign_value 不同者，共 15 处）：
--   PAY_PLAN_BASE.Task_2 DIRECT_SUPERVISOR->DEPT_MANAGER、PAY_PLAN_PLATE.Task_2_1 PROJECT_MANAGER->BASE_FIN_MANAGER、PAY_PLAN_PLATE.Task_2_2 FUND_STAFF->FUND_PAY_STAFF、PAY_PLAN_PLATE.Task_4 VP->CFO、PAY_PLAN_PLATE.Task_5 FIN_DIRECTOR->PRESIDENT、TR_PAY_FAIL.Task_1 NULL->CASHIER、TR_TRANSFER.Task_1 NULL->FUND_PAY_STAFF、TR_TRANSFER.Task_5 NULL->CASHIER、TR_UPLOAD.Task_1 FUND_STAFF->FUND_PAY_STAFF、TR_UPLOAD.Task_2 FUND_STAFF->CASHIER、TR_RECEIPT.Task_4 FUND_PAY_STAFF->CASHIER、TR_BILL.Task_1 FUND_PAY_STAFF->CASHIER、TR_BILL.Task_4 FUND_PAY_STAFF->FIN_AUDIT、TR_BANK_RECON.Task_1 FUND_PAY_STAFF->CASHIER、TR01-02.Task_1 FUND_STAFF->CASHIER
-- 挂账（配置侧无法自解，须代码/权限侧处置，详见方案 §8.9）：
--   D-FAM6-01 companyTable() 缺 TR_PAY/TR_PAY_FAIL/TR_UPLOAD/TR_RECEIPT/TR_BILL/TR_BANK_RECON/TR_BANK_MASTER/TR01-02/PAY_PLAN_PLATE
--     九个 bizType（同表的 PAY_PLAN_BASE 在、PAY_PLAN_PLATE 漏）→ 上述 COMPANY 范围节点解析公司失败 → 走 ESCALATE 外兜真人（ silent）；
--   D-FAM6-02 tr_bill_settle / tr_credit_bill / bank_reconcile 无 company_id 列 → TR_BILL/TR_RECEIPT/TR_BANK_RECON 三条链【永久只能挂 GROUP 范围角色】，
--     补白名单也解析不出，属数据模型限制而非缺陷绕行；
--   deny_self 在本族全死：这些业务表既无 apply_user_id 也无 applyColumn() 特例（pay_plan=applicant_id、op_pay_batch=submit_user_id）→ 提单人自审无服务端拦截；
--   md:bankaccount:create/update 仅授 CONFIG_ADMIN/IT_ADMIN → TR_BANK_MASTER 的审批人与"能操作的人"两套口径脱钩（并入 RA 系列裁决）。
-- 档位口径（方案 §5.9）：授权层级(BASE_GM/GM/CFO/PRESIDENT/板块分管领导) 48h AUTO_REMIND 且永不 AUTO_PASS；
--   共享审单(FSSC_AUDITOR/FIN_AUDIT/SCAN_STAFF) 8h AUTO_REMIND；业务审批 24h AUTO_REMIND；作业池 48h AUTO_REMIND。
--   本族无 BOARD_OFFICE 节点 → 无 168h NOTIFY；empty_strategy 全族 ESCALATE。
-- allow_transfer / multi_type / auto_pass_self / form_perm 一律不动（沿用批次1 定档）。
-- 不删任何数据；回滚见 V1.4.31_fam6_nodeconfig_align_rollback.sql
-- 执行：mysql -uroot -p123456 -h127.0.0.1 -P3306 -Dfssc --default-character-set=utf8mb4 < 本文件

-- 0) 备份将被改动的行（幂等：表已存在则跳过，备份表不删）
CREATE TABLE IF NOT EXISTS wf_node_config_bak_V1431_20260922 AS
SELECT * FROM wf_node_config WHERE (model_key, node_key) IN (('PAY_PLAN_BASE','Task_2'),('PAY_PLAN_BASE','Task_3'),('PAY_PLAN_BASE','Task_4'),('PAY_PLAN_BASE','Task_5'),('PAY_PLAN_PLATE','Task_2_0'),('PAY_PLAN_PLATE','Task_2_1'),('PAY_PLAN_PLATE','Task_2_2'),('PAY_PLAN_PLATE','Task_2_3'),('PAY_PLAN_PLATE','Task_3'),('PAY_PLAN_PLATE','Task_4'),('PAY_PLAN_PLATE','Task_5'),('TR_PAY','Task_2'),('TR_PAY','Task_3'),('TR_PAY','Task_4'),('TR_PAY','Task_5'),('TR_PAY','Task_6'),('TR_PAY','Task_7'),('TR_PAY','Task_8'),('TR_PAY','Task_9'),('TR_PAY_FAIL','Task_1'),('TR_PAY_FAIL','Task_6'),('TR_PLAN','Task_2'),('TR_PLAN','Task_3'),('TR_PLAN','Task_5'),('TR_PLAN','Task_6'),('TR_PLAN','Task_7'),('TR_PLAN_ADJUST','Task_2'),('TR_PLAN_ADJUST','Task_3'),('TR_PLAN_ADJUST','Task_6'),('TR_PLAN_ADJUST','Task_7'),('TR_TRANSFER','Task_1'),('TR_TRANSFER','Task_2'),('TR_TRANSFER','Task_3'),('TR_TRANSFER','Task_4'),('TR_TRANSFER','Task_5'),('TR_UPLOAD','Task_1'),('TR_UPLOAD','Task_2'),('TR_RECEIPT','Task_4'),('TR_RECEIPT','Task_2'),('TR_BILL','Task_1'),('TR_BILL','Task_4'),('TR_BANK_RECON','Task_1'),('TR_BANK_RECON','Task_4'),('TR_BANK_MASTER','Task_2'),('TR_BANK_MASTER','Task_4'),('TR_BANK_MASTER','Task_6'),('TR01-02','Task_1'),('TR01-02','Task_2'));

-- 0b) 本族无新 key：48 个节点全部沿用原模型 Task_N，故只有 UPDATE、没有 INSERT。

-- 1) 逐节点对齐（48 行）
-- PAY_PLAN_BASE.Task_2  部门经理审核付款计划与供应商【部门经理】
UPDATE wf_node_config
   SET node_name      = '部门经理审核付款计划与供应商【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'PAY_PLAN_BASE' AND node_key = 'Task_2';

-- PAY_PLAN_BASE.Task_3  项目经理审核付款计划与费用归属【项目经理/费用承担部门负责人】
UPDATE wf_node_config
   SET node_name      = '项目经理审核付款计划与费用归属【项目经理/费用承担部门负责人】',
       assign_type    = 'ROLE',
       assign_value   = 'PROJECT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'PAY_PLAN_BASE' AND node_key = 'Task_3';

-- PAY_PLAN_BASE.Task_4  基地财务经理审核付款计划【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核付款计划【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'PAY_PLAN_BASE' AND node_key = 'Task_4';

-- PAY_PLAN_BASE.Task_5  基地总经理审批付款计划【基地总经理】
UPDATE wf_node_config
   SET node_name      = '基地总经理审批付款计划【基地总经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'PAY_PLAN_BASE' AND node_key = 'Task_5';

-- PAY_PLAN_PLATE.Task_2_0  项目经理会审付款计划与项目预算【项目经理/费用承担部门负责人】
UPDATE wf_node_config
   SET node_name      = '项目经理会审付款计划与项目预算【项目经理/费用承担部门负责人】',
       assign_type    = 'ROLE',
       assign_value   = 'PROJECT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'PAY_PLAN_PLATE' AND node_key = 'Task_2_0';

-- PAY_PLAN_PLATE.Task_2_1  基地财务经理会审资金与预算占用【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理会审资金与预算占用【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'PAY_PLAN_PLATE' AND node_key = 'Task_2_1';

-- PAY_PLAN_PLATE.Task_2_2  资金支付岗会审支付可行性【资金支付岗】
UPDATE wf_node_config
   SET node_name      = '资金支付岗会审支付可行性【资金支付岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FUND_PAY_STAFF',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'PAY_PLAN_PLATE' AND node_key = 'Task_2_2';

-- PAY_PLAN_PLATE.Task_2_3  板块财务经理会审板块资金平衡【板块财务经理】
UPDATE wf_node_config
   SET node_name      = '板块财务经理会审板块资金平衡【板块财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'PAY_PLAN_PLATE' AND node_key = 'Task_2_3';

-- PAY_PLAN_PLATE.Task_3  采购总监汇总审批会审意见【采购总监】
UPDATE wf_node_config
   SET node_name      = '采购总监汇总审批会审意见【采购总监】',
       assign_type    = 'ROLE',
       assign_value   = 'PURCHASE_DIRECTOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'PAY_PLAN_PLATE' AND node_key = 'Task_3';

-- PAY_PLAN_PLATE.Task_4  财务总监审批付款计划（超 100 万）【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批付款计划（超 100 万）【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'PAY_PLAN_PLATE' AND node_key = 'Task_4';

-- PAY_PLAN_PLATE.Task_5  总裁审批付款计划（超 500 万）【总裁】
UPDATE wf_node_config
   SET node_name      = '总裁审批付款计划（超 500 万）【总裁】',
       assign_type    = 'ROLE',
       assign_value   = 'PRESIDENT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'PAY_PLAN_PLATE' AND node_key = 'Task_5';

-- TR_PAY.Task_2  资金主任审核付款批次与账户头寸【资金岗】
UPDATE wf_node_config
   SET node_name      = '资金主任审核付款批次与账户头寸【资金岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FUND_STAFF',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PAY' AND node_key = 'Task_2';

-- TR_PAY.Task_3  基地财务经理审核付款批次【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核付款批次【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PAY' AND node_key = 'Task_3';

-- TR_PAY.Task_4  基地总经理审批付款（超 50 万）【基地总经理】
UPDATE wf_node_config
   SET node_name      = '基地总经理审批付款（超 50 万）【基地总经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PAY' AND node_key = 'Task_4';

-- TR_PAY.Task_5  板块财务经理审批付款（超 100 万）【板块财务经理】
UPDATE wf_node_config
   SET node_name      = '板块财务经理审批付款（超 100 万）【板块财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PAY' AND node_key = 'Task_5';

-- TR_PAY.Task_6  财务总监审批付款（超 200 万）【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批付款（超 200 万）【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PAY' AND node_key = 'Task_6';

-- TR_PAY.Task_7  总裁审批付款（超 500 万）【总裁】
UPDATE wf_node_config
   SET node_name      = '总裁审批付款（超 500 万）【总裁】',
       assign_type    = 'ROLE',
       assign_value   = 'PRESIDENT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PAY' AND node_key = 'Task_7';

-- TR_PAY.Task_8  共享主管复核付款单据齐套性【共享主管】
UPDATE wf_node_config
   SET node_name      = '共享主管复核付款单据齐套性【共享主管】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_SUPERVISOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PAY' AND node_key = 'Task_8';

-- TR_PAY.Task_9  银企直联/网银执行付款并回单影像齐套（作业池）【资金支付岗】
UPDATE wf_node_config
   SET node_name      = '银企直联/网银执行付款并回单影像齐套（作业池）【资金支付岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FUND_PAY_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PAY' AND node_key = 'Task_9';

-- TR_PAY_FAIL.Task_1  核实支付失败原因并通知经办人（作业池）【出纳】
UPDATE wf_node_config
   SET node_name      = '核实支付失败原因并通知经办人（作业池）【出纳】',
       assign_type    = 'ROLE',
       assign_value   = 'CASHIER',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PAY_FAIL' AND node_key = 'Task_1';

-- TR_PAY_FAIL.Task_6  共享会计审核重付方案与收款账户【共享会计】
UPDATE wf_node_config
   SET node_name      = '共享会计审核重付方案与收款账户【共享会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_AUDITOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PAY_FAIL' AND node_key = 'Task_6';

-- TR_PLAN.Task_2  基地财务汇总编制资金计划并齐套依据（作业池）【资金岗】
UPDATE wf_node_config
   SET node_name      = '基地财务汇总编制资金计划并齐套依据（作业池）【资金岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FUND_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PLAN' AND node_key = 'Task_2';

-- TR_PLAN.Task_3  基地财务经理审核资金计划【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核资金计划【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PLAN' AND node_key = 'Task_3';

-- TR_PLAN.Task_5  板块财务经理审批资金计划【板块财务经理】
UPDATE wf_node_config
   SET node_name      = '板块财务经理审批资金计划【板块财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PLAN' AND node_key = 'Task_5';

-- TR_PLAN.Task_6  财务总监审批资金计划【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批资金计划【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PLAN' AND node_key = 'Task_6';

-- TR_PLAN.Task_7  总裁审批资金计划【总裁】
UPDATE wf_node_config
   SET node_name      = '总裁审批资金计划【总裁】',
       assign_type    = 'ROLE',
       assign_value   = 'PRESIDENT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PLAN' AND node_key = 'Task_7';

-- TR_PLAN_ADJUST.Task_2  录入调整资金计划与调整依据（作业池）【资金岗】
UPDATE wf_node_config
   SET node_name      = '录入调整资金计划与调整依据（作业池）【资金岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FUND_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PLAN_ADJUST' AND node_key = 'Task_2';

-- TR_PLAN_ADJUST.Task_3  基地财务经理审核计划调整【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核计划调整【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PLAN_ADJUST' AND node_key = 'Task_3';

-- TR_PLAN_ADJUST.Task_6  板块财务经理审批计划调整【板块财务经理】
UPDATE wf_node_config
   SET node_name      = '板块财务经理审批计划调整【板块财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PLAN_ADJUST' AND node_key = 'Task_6';

-- TR_PLAN_ADJUST.Task_7  财务总监审批计划调整（总额超 500 万）【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批计划调整（总额超 500 万）【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_PLAN_ADJUST' AND node_key = 'Task_7';

-- TR_TRANSFER.Task_1  资金支付岗审核调拨申请与账户头寸【资金支付岗】
UPDATE wf_node_config
   SET node_name      = '资金支付岗审核调拨申请与账户头寸【资金支付岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FUND_PAY_STAFF',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_TRANSFER' AND node_key = 'Task_1';

-- TR_TRANSFER.Task_2  板块财务经理审批调拨（超 100 万）【板块财务经理】
UPDATE wf_node_config
   SET node_name      = '板块财务经理审批调拨（超 100 万）【板块财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_TRANSFER' AND node_key = 'Task_2';

-- TR_TRANSFER.Task_3  财务总监审批调拨（超 200 万）【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批调拨（超 200 万）【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_TRANSFER' AND node_key = 'Task_3';

-- TR_TRANSFER.Task_4  总裁审批调拨（超 500 万）【总裁】
UPDATE wf_node_config
   SET node_name      = '总裁审批调拨（超 500 万）【总裁】',
       assign_type    = 'ROLE',
       assign_value   = 'PRESIDENT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_TRANSFER' AND node_key = 'Task_4';

-- TR_TRANSFER.Task_5  银企转账执行与回单影像齐套（作业池）【出纳】
UPDATE wf_node_config
   SET node_name      = '银企转账执行与回单影像齐套（作业池）【出纳】',
       assign_type    = 'ROLE',
       assign_value   = 'CASHIER',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_TRANSFER' AND node_key = 'Task_5';

-- TR_UPLOAD.Task_1  资金支付岗审核上存申请与账户头寸【资金支付岗】
UPDATE wf_node_config
   SET node_name      = '资金支付岗审核上存申请与账户头寸【资金支付岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FUND_PAY_STAFF',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_UPLOAD' AND node_key = 'Task_1';

-- TR_UPLOAD.Task_2  账户资金归集执行与回单影像齐套（作业池）【出纳】
UPDATE wf_node_config
   SET node_name      = '账户资金归集执行与回单影像齐套（作业池）【出纳】',
       assign_type    = 'ROLE',
       assign_value   = 'CASHIER',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_UPLOAD' AND node_key = 'Task_2';

-- TR_RECEIPT.Task_4  录入票据与收款信息并核对到款（作业池）【出纳】
UPDATE wf_node_config
   SET node_name      = '录入票据与收款信息并核对到款（作业池）【出纳】',
       assign_type    = 'ROLE',
       assign_value   = 'CASHIER',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_RECEIPT' AND node_key = 'Task_4';

-- TR_RECEIPT.Task_2  财务审核岗确认到款并开具收据【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '财务审核岗确认到款并开具收据【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_RECEIPT' AND node_key = 'Task_2';

-- TR_BILL.Task_1  票据开具背书签章、银行办理与台账登记（作业池）【出纳】
UPDATE wf_node_config
   SET node_name      = '票据开具背书签章、银行办理与台账登记（作业池）【出纳】',
       assign_type    = 'ROLE',
       assign_value   = 'CASHIER',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_BILL' AND node_key = 'Task_1';

-- TR_BILL.Task_4  财务审核岗复核票据与信用证开具背书业务【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '财务审核岗复核票据与信用证开具背书业务【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_BILL' AND node_key = 'Task_4';

-- TR_BANK_RECON.Task_1  获取银行明细、导出日记账并编制余额调节表（作业池）【出纳】
UPDATE wf_node_config
   SET node_name      = '获取银行明细、导出日记账并编制余额调节表（作业池）【出纳】',
       assign_type    = 'ROLE',
       assign_value   = 'CASHIER',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_BANK_RECON' AND node_key = 'Task_1';

-- TR_BANK_RECON.Task_4  财务审核岗复核银行余额调节表与未达账【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '财务审核岗复核银行余额调节表与未达账【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_BANK_RECON' AND node_key = 'Task_4';

-- TR_BANK_MASTER.Task_2  基地财务经理审核开户资料与用途【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核开户资料与用途【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_BANK_MASTER' AND node_key = 'Task_2';

-- TR_BANK_MASTER.Task_4  财务总监审批银行开户主数据【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批银行开户主数据【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_BANK_MASTER' AND node_key = 'Task_4';

-- TR_BANK_MASTER.Task_6  维护账户主数据与网银参数并影像齐套（作业池）【共享主管】
UPDATE wf_node_config
   SET node_name      = '维护账户主数据与网银参数并影像齐套（作业池）【共享主管】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_SUPERVISOR',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR_BANK_MASTER' AND node_key = 'Task_6';

-- TR01-02.Task_1  印鉴变更授权书与新印鉴卡影像齐套（作业池）【出纳】
UPDATE wf_node_config
   SET node_name      = '印鉴变更授权书与新印鉴卡影像齐套（作业池）【出纳】',
       assign_type    = 'ROLE',
       assign_value   = 'CASHIER',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR01-02' AND node_key = 'Task_1';

-- TR01-02.Task_2  财务总监审批银行印鉴变更【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批银行印鉴变更【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'TR01-02' AND node_key = 'Task_2';

-- 2) 新 key 插行（0 行，NOT EXISTS 守卫保证幂等）

-- 3) 复核 A：逐节点期望值对账（期望 0 行；输出任一行即为 BPMN 与 config 两处真值漂移）
SELECT e.model_key, e.node_key, e.exp_role AS bpmn_groups, c.assign_value AS config_role,
       e.exp_name AS bpmn_name, c.node_name AS config_name, e.exp_es AS exp_empty, IFNULL(c.empty_strategy,'') got_empty,
       e.exp_th AS exp_th, IFNULL(c.timeout_hours,-1) got_th, e.exp_ta exp_ta, IFNULL(c.timeout_action,'') got_ta,
       e.exp_hm exp_hm, IFNULL(c.handling_mode,'') got_hm
  FROM (
    SELECT 'PAY_PLAN_BASE' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '部门经理审核付款计划与供应商【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'PAY_PLAN_BASE' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'PROJECT_MANAGER' exp_role, '项目经理审核付款计划与费用归属【项目经理/费用承担部门负责人】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'PAY_PLAN_BASE' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核付款计划【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'PAY_PLAN_BASE' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'BASE_GM' exp_role, '基地总经理审批付款计划【基地总经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'PAY_PLAN_PLATE' model_key, 'Task_2_0' node_key, 'ROLE' exp_type, 'PROJECT_MANAGER' exp_role, '项目经理会审付款计划与项目预算【项目经理/费用承担部门负责人】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'PAY_PLAN_PLATE' model_key, 'Task_2_1' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理会审资金与预算占用【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'PAY_PLAN_PLATE' model_key, 'Task_2_2' node_key, 'ROLE' exp_type, 'FUND_PAY_STAFF' exp_role, '资金支付岗会审支付可行性【资金支付岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'PAY_PLAN_PLATE' model_key, 'Task_2_3' node_key, 'ROLE' exp_type, 'PLATE_FIN_MANAGER' exp_role, '板块财务经理会审板块资金平衡【板块财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'PAY_PLAN_PLATE' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'PURCHASE_DIRECTOR' exp_role, '采购总监汇总审批会审意见【采购总监】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'PAY_PLAN_PLATE' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批付款计划（超 100 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'PAY_PLAN_PLATE' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'PRESIDENT' exp_role, '总裁审批付款计划（超 500 万）【总裁】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PAY' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FUND_STAFF' exp_role, '资金主任审核付款批次与账户头寸【资金岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PAY' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核付款批次【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PAY' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'BASE_GM' exp_role, '基地总经理审批付款（超 50 万）【基地总经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PAY' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'PLATE_FIN_MANAGER' exp_role, '板块财务经理审批付款（超 100 万）【板块财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PAY' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批付款（超 200 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PAY' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'PRESIDENT' exp_role, '总裁审批付款（超 500 万）【总裁】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PAY' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'FSSC_SUPERVISOR' exp_role, '共享主管复核付款单据齐套性【共享主管】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PAY' model_key, 'Task_9' node_key, 'ROLE' exp_type, 'FUND_PAY_STAFF' exp_role, '银企直联/网银执行付款并回单影像齐套（作业池）【资金支付岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'TR_PAY_FAIL' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'CASHIER' exp_role, '核实支付失败原因并通知经办人（作业池）【出纳】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'TR_PAY_FAIL' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'FSSC_AUDITOR' exp_role, '共享会计审核重付方案与收款账户【共享会计】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PLAN' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FUND_STAFF' exp_role, '基地财务汇总编制资金计划并齐套依据（作业池）【资金岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'TR_PLAN' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核资金计划【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PLAN' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'PLATE_FIN_MANAGER' exp_role, '板块财务经理审批资金计划【板块财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PLAN' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批资金计划【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PLAN' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'PRESIDENT' exp_role, '总裁审批资金计划【总裁】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PLAN_ADJUST' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FUND_STAFF' exp_role, '录入调整资金计划与调整依据（作业池）【资金岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'TR_PLAN_ADJUST' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核计划调整【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PLAN_ADJUST' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'PLATE_FIN_MANAGER' exp_role, '板块财务经理审批计划调整【板块财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_PLAN_ADJUST' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批计划调整（总额超 500 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_TRANSFER' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'FUND_PAY_STAFF' exp_role, '资金支付岗审核调拨申请与账户头寸【资金支付岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_TRANSFER' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'PLATE_FIN_MANAGER' exp_role, '板块财务经理审批调拨（超 100 万）【板块财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_TRANSFER' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批调拨（超 200 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_TRANSFER' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'PRESIDENT' exp_role, '总裁审批调拨（超 500 万）【总裁】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_TRANSFER' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'CASHIER' exp_role, '银企转账执行与回单影像齐套（作业池）【出纳】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'TR_UPLOAD' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'FUND_PAY_STAFF' exp_role, '资金支付岗审核上存申请与账户头寸【资金支付岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_UPLOAD' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'CASHIER' exp_role, '账户资金归集执行与回单影像齐套（作业池）【出纳】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'TR_RECEIPT' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'CASHIER' exp_role, '录入票据与收款信息并核对到款（作业池）【出纳】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'TR_RECEIPT' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗确认到款并开具收据【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_BILL' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'CASHIER' exp_role, '票据开具背书签章、银行办理与台账登记（作业池）【出纳】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'TR_BILL' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗复核票据与信用证开具背书业务【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_BANK_RECON' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'CASHIER' exp_role, '获取银行明细、导出日记账并编制余额调节表（作业池）【出纳】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'TR_BANK_RECON' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗复核银行余额调节表与未达账【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_BANK_MASTER' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核开户资料与用途【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_BANK_MASTER' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批银行开户主数据【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'TR_BANK_MASTER' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'FSSC_SUPERVISOR' exp_role, '维护账户主数据与网银参数并影像齐套（作业池）【共享主管】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'TR01-02' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'CASHIER' exp_role, '印鉴变更授权书与新印鉴卡影像齐套（作业池）【出纳】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'TR01-02' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批银行印鉴变更【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
  ) e
  LEFT JOIN wf_node_config c ON c.model_key = e.model_key AND c.node_key = e.node_key AND c.del_flag = 0
 WHERE c.node_key IS NULL
    OR IFNULL(c.assign_type, '') <> e.exp_type
    OR IFNULL(c.assign_value, '') <> e.exp_role
    OR IFNULL(c.node_name, '') <> e.exp_name
    OR IFNULL(c.empty_strategy, '') <> e.exp_es
    OR IFNULL(c.timeout_hours, -1) <> e.exp_th
    OR IFNULL(c.timeout_action, '') <> e.exp_ta
    OR IFNULL(c.handling_mode, '') <> e.exp_hm;

-- 4) 复核 B：本族 14 个角色的存在性与 co1 在编持岗人数（口径 status='NORMAL' 且 is_admin=0；
--    COMPANY 范围角色必须问"该公司有没有在编持有人"。active_co1=0 即为 §5.4 补岗清单候选，不是脚本缺陷。
--     本族三个单点角色：FUND_STAFF 全租户 1 人挂 3 口、BASE_GM 1 人挂 2 口、PRESIDENT 1 人挂 4 口（DOA 顶档），
--      断流只能靠 ESCALATE 沿汇报线外兜；DIRECT_SUPERVISOR co1=0 人已从 PAY_PLAN_BASE 换下（改 DEPT_MANAGER）。
--     复核 C 里 rows_total>rows_in_new_bpmn 的差额＝已出链幽灵行（40 行），不改不删。
--     D-FAM6-01：下表 org_scope=COMPANY 且 model_key 不在 companyTable 白名单者，co1 计数再高也解析不出公司。）
SELECT r.role_code, ANY_VALUE(r.role_name) role_name, ANY_VALUE(r.org_scope) org_scope, ANY_VALUE(r.status) status,
       COUNT(DISTINCT ur.user_id) holders_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 THEN 1 ELSE 0 END) active_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 AND u.company_id = 1 THEN 1 ELSE 0 END) active_co1
  FROM sys_role r
  LEFT JOIN sys_user_role ur ON ur.role_id = r.id
  LEFT JOIN sys_user u ON u.id = ur.user_id AND u.del_flag = 0
 WHERE r.role_code IN ('BASE_FIN_MANAGER','BASE_GM','CASHIER','CFO','DEPT_MANAGER','FIN_AUDIT','FSSC_AUDITOR','FSSC_SUPERVISOR','FUND_PAY_STAFF','FUND_STAFF','PLATE_FIN_MANAGER','PRESIDENT','PROJECT_MANAGER','PURCHASE_DIRECTOR') AND r.del_flag = 0
 GROUP BY r.role_code ORDER BY active_co1, r.role_code;

-- 5) 复核 C：配置行盘点 = 新 BPMN 有效节点(48) + 出链后的幽灵行（仅盘点，不改；期望 rows_in_new_bpmn 合计 48）
SELECT model_key, COUNT(*) rows_total,
       SUM((model_key, node_key) IN (('PAY_PLAN_BASE','Task_2'),('PAY_PLAN_BASE','Task_3'),('PAY_PLAN_BASE','Task_4'),('PAY_PLAN_BASE','Task_5'),('PAY_PLAN_PLATE','Task_2_0'),('PAY_PLAN_PLATE','Task_2_1'),('PAY_PLAN_PLATE','Task_2_2'),('PAY_PLAN_PLATE','Task_2_3'),('PAY_PLAN_PLATE','Task_3'),('PAY_PLAN_PLATE','Task_4'),('PAY_PLAN_PLATE','Task_5'),('TR_PAY','Task_2'),('TR_PAY','Task_3'),('TR_PAY','Task_4'),('TR_PAY','Task_5'),('TR_PAY','Task_6'),('TR_PAY','Task_7'),('TR_PAY','Task_8'),('TR_PAY','Task_9'),('TR_PAY_FAIL','Task_1'),('TR_PAY_FAIL','Task_6'),('TR_PLAN','Task_2'),('TR_PLAN','Task_3'),('TR_PLAN','Task_5'),('TR_PLAN','Task_6'),('TR_PLAN','Task_7'),('TR_PLAN_ADJUST','Task_2'),('TR_PLAN_ADJUST','Task_3'),('TR_PLAN_ADJUST','Task_6'),('TR_PLAN_ADJUST','Task_7'),('TR_TRANSFER','Task_1'),('TR_TRANSFER','Task_2'),('TR_TRANSFER','Task_3'),('TR_TRANSFER','Task_4'),('TR_TRANSFER','Task_5'),('TR_UPLOAD','Task_1'),('TR_UPLOAD','Task_2'),('TR_RECEIPT','Task_4'),('TR_RECEIPT','Task_2'),('TR_BILL','Task_1'),('TR_BILL','Task_4'),('TR_BANK_RECON','Task_1'),('TR_BANK_RECON','Task_4'),('TR_BANK_MASTER','Task_2'),('TR_BANK_MASTER','Task_4'),('TR_BANK_MASTER','Task_6'),('TR01-02','Task_1'),('TR01-02','Task_2'))) rows_in_new_bpmn
  FROM wf_node_config
 WHERE model_key IN ('PAY_PLAN_BASE','PAY_PLAN_PLATE','TR_PAY','TR_PAY_FAIL','TR_PLAN','TR_PLAN_ADJUST','TR_TRANSFER','TR_UPLOAD','TR_RECEIPT','TR_BILL','TR_BANK_RECON','TR_BANK_MASTER','TR01-02') AND del_flag = 0
 GROUP BY model_key ORDER BY model_key;

-- 6) 复核 D：作业池档位落地（期望 11 行 POOL）
SELECT model_key, node_key, handling_mode, assign_value, node_name FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('PAY_PLAN_BASE','Task_2'),('PAY_PLAN_BASE','Task_3'),('PAY_PLAN_BASE','Task_4'),('PAY_PLAN_BASE','Task_5'),('PAY_PLAN_PLATE','Task_2_0'),('PAY_PLAN_PLATE','Task_2_1'),('PAY_PLAN_PLATE','Task_2_2'),('PAY_PLAN_PLATE','Task_2_3'),('PAY_PLAN_PLATE','Task_3'),('PAY_PLAN_PLATE','Task_4'),('PAY_PLAN_PLATE','Task_5'),('TR_PAY','Task_2'),('TR_PAY','Task_3'),('TR_PAY','Task_4'),('TR_PAY','Task_5'),('TR_PAY','Task_6'),('TR_PAY','Task_7'),('TR_PAY','Task_8'),('TR_PAY','Task_9'),('TR_PAY_FAIL','Task_1'),('TR_PAY_FAIL','Task_6'),('TR_PLAN','Task_2'),('TR_PLAN','Task_3'),('TR_PLAN','Task_5'),('TR_PLAN','Task_6'),('TR_PLAN','Task_7'),('TR_PLAN_ADJUST','Task_2'),('TR_PLAN_ADJUST','Task_3'),('TR_PLAN_ADJUST','Task_6'),('TR_PLAN_ADJUST','Task_7'),('TR_TRANSFER','Task_1'),('TR_TRANSFER','Task_2'),('TR_TRANSFER','Task_3'),('TR_TRANSFER','Task_4'),('TR_TRANSFER','Task_5'),('TR_UPLOAD','Task_1'),('TR_UPLOAD','Task_2'),('TR_RECEIPT','Task_4'),('TR_RECEIPT','Task_2'),('TR_BILL','Task_1'),('TR_BILL','Task_4'),('TR_BANK_RECON','Task_1'),('TR_BANK_RECON','Task_4'),('TR_BANK_MASTER','Task_2'),('TR_BANK_MASTER','Task_4'),('TR_BANK_MASTER','Task_6'),('TR01-02','Task_1'),('TR01-02','Task_2')) AND handling_mode = 'POOL'
 ORDER BY model_key, node_key;

-- 7) 复核 E：零 admin 硬门槛 —— 本族任一节点都不得由 admin 参与；点名残留（assign_value 是纯数字 userId）也算违规
SELECT c.model_key, c.node_key, c.assign_type, c.assign_value
  FROM wf_node_config c
 WHERE c.del_flag = 0 AND (c.model_key, c.node_key) IN (('PAY_PLAN_BASE','Task_2'),('PAY_PLAN_BASE','Task_3'),('PAY_PLAN_BASE','Task_4'),('PAY_PLAN_BASE','Task_5'),('PAY_PLAN_PLATE','Task_2_0'),('PAY_PLAN_PLATE','Task_2_1'),('PAY_PLAN_PLATE','Task_2_2'),('PAY_PLAN_PLATE','Task_2_3'),('PAY_PLAN_PLATE','Task_3'),('PAY_PLAN_PLATE','Task_4'),('PAY_PLAN_PLATE','Task_5'),('TR_PAY','Task_2'),('TR_PAY','Task_3'),('TR_PAY','Task_4'),('TR_PAY','Task_5'),('TR_PAY','Task_6'),('TR_PAY','Task_7'),('TR_PAY','Task_8'),('TR_PAY','Task_9'),('TR_PAY_FAIL','Task_1'),('TR_PAY_FAIL','Task_6'),('TR_PLAN','Task_2'),('TR_PLAN','Task_3'),('TR_PLAN','Task_5'),('TR_PLAN','Task_6'),('TR_PLAN','Task_7'),('TR_PLAN_ADJUST','Task_2'),('TR_PLAN_ADJUST','Task_3'),('TR_PLAN_ADJUST','Task_6'),('TR_PLAN_ADJUST','Task_7'),('TR_TRANSFER','Task_1'),('TR_TRANSFER','Task_2'),('TR_TRANSFER','Task_3'),('TR_TRANSFER','Task_4'),('TR_TRANSFER','Task_5'),('TR_UPLOAD','Task_1'),('TR_UPLOAD','Task_2'),('TR_RECEIPT','Task_4'),('TR_RECEIPT','Task_2'),('TR_BILL','Task_1'),('TR_BILL','Task_4'),('TR_BANK_RECON','Task_1'),('TR_BANK_RECON','Task_4'),('TR_BANK_MASTER','Task_2'),('TR_BANK_MASTER','Task_4'),('TR_BANK_MASTER','Task_6'),('TR01-02','Task_1'),('TR01-02','Task_2'))
   AND ( c.assign_type <> 'ROLE'
      OR c.assign_value REGEXP '^[0-9]+$'
      OR EXISTS (SELECT 1 FROM sys_user u
                   JOIN sys_user_role ur ON ur.user_id = u.id
                   JOIN sys_role r ON r.id = ur.role_id
                  WHERE u.is_admin = 1 AND u.del_flag = 0 AND r.role_code = c.assign_value) );

-- 8) 复核 F：出链后仍在的【点名 assignee】残留普查（本族重造把 4 个 ${approver} 节点请出链：TR_BILL×2 / TR_RECEIPT / TR_UPLOAD，
--    此处确认已发布链上再无 flowable:assignee 点名与 USER 型配置；期望 0 行）
SELECT model_key, node_key, assign_type, assign_value FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('PAY_PLAN_BASE','Task_2'),('PAY_PLAN_BASE','Task_3'),('PAY_PLAN_BASE','Task_4'),('PAY_PLAN_BASE','Task_5'),('PAY_PLAN_PLATE','Task_2_0'),('PAY_PLAN_PLATE','Task_2_1'),('PAY_PLAN_PLATE','Task_2_2'),('PAY_PLAN_PLATE','Task_2_3'),('PAY_PLAN_PLATE','Task_3'),('PAY_PLAN_PLATE','Task_4'),('PAY_PLAN_PLATE','Task_5'),('TR_PAY','Task_2'),('TR_PAY','Task_3'),('TR_PAY','Task_4'),('TR_PAY','Task_5'),('TR_PAY','Task_6'),('TR_PAY','Task_7'),('TR_PAY','Task_8'),('TR_PAY','Task_9'),('TR_PAY_FAIL','Task_1'),('TR_PAY_FAIL','Task_6'),('TR_PLAN','Task_2'),('TR_PLAN','Task_3'),('TR_PLAN','Task_5'),('TR_PLAN','Task_6'),('TR_PLAN','Task_7'),('TR_PLAN_ADJUST','Task_2'),('TR_PLAN_ADJUST','Task_3'),('TR_PLAN_ADJUST','Task_6'),('TR_PLAN_ADJUST','Task_7'),('TR_TRANSFER','Task_1'),('TR_TRANSFER','Task_2'),('TR_TRANSFER','Task_3'),('TR_TRANSFER','Task_4'),('TR_TRANSFER','Task_5'),('TR_UPLOAD','Task_1'),('TR_UPLOAD','Task_2'),('TR_RECEIPT','Task_4'),('TR_RECEIPT','Task_2'),('TR_BILL','Task_1'),('TR_BILL','Task_4'),('TR_BANK_RECON','Task_1'),('TR_BANK_RECON','Task_4'),('TR_BANK_MASTER','Task_2'),('TR_BANK_MASTER','Task_4'),('TR_BANK_MASTER','Task_6'),('TR01-02','Task_1'),('TR01-02','Task_2')) AND assign_type = 'USER';
