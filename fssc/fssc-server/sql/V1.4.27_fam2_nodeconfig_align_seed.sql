-- V1.4.27  批次2 第二族（应收 / 发票 / 对账族）配置侧对齐：wf_node_config <- 新 BPMN 唯一真值
-- 前置：11 个模型 BPMN 已经 /api/tenant/wf/designer/publish 重发布
--   AR_RECON v3 / AR_REPORT v4 / BAD_DEBT_CONFIRM v4 / BAD_DEBT_PROV v3 / INVOICE_ISSUE v6 / INVOICE_MATCH v4 /
--   INVOICE_MATCH_MANUAL v3 / RECEIVABLE v4 / RECEIVABLE_COLLECT v4 / RECON_ACTIVE v5 / RECON_PASSIVE v5
-- 为什么必须这一步：发布链路只写 wf_model + Flowable deploy，不回写 wf_node_config；而 WfEngineFacade.applyNodeConfig 的
--   ROLE 分支是把 config.assign_value 作为候选组【追加】到 BPMN candidateGroups 之上、从不摘除。
--   不同改 -> 已出链/已改岗的旧角色仍可办理新节点（参与人两处真值漂移），本族 6 处改岗会全部失效。
-- 只动新 BPMN 里真实存在的 37 个节点（11 个模型；AR_CLAIM 2 节点本轮未改结构，配置行保持原值）。
-- 已出链旧节点的配置行按批次2第一族规则【保留不删】（DB 增量原则）：引擎按 BPMN 节点反查配置，幽灵行不产生运行时影响，仅在盘点里计数。
-- handling_mode / allow_transfer / multi_type 一律不动（沿用批次1 定档），本文件只对齐：名称、参与人、empty_strategy、超时矩阵。
-- 不删任何数据；回滚见 V1.4.27_fam2_nodeconfig_align_rollback.sql
-- 执行：mysql -uroot -p123456 -h127.0.0.1 -P3306 -Dfssc --default-character-set=utf8mb4 < 本文件

-- 0) 备份将被改动的行（去重：表已存在则跳过，备份表不删）
CREATE TABLE IF NOT EXISTS wf_node_config_bak_v1427_20260922 AS
SELECT * FROM wf_node_config WHERE (model_key, node_key) IN (('AR_RECON','Task_4'),('AR_RECON','Task_6'),('AR_RECON','Task_8'),('AR_REPORT','Task_4_0'),('AR_REPORT','Task_4_1'),('AR_REPORT','Task_4_2'),('BAD_DEBT_CONFIRM','Task_2'),('BAD_DEBT_CONFIRM','Task_3'),('BAD_DEBT_CONFIRM','Task_4'),('BAD_DEBT_CONFIRM','Task_5'),('BAD_DEBT_PROV','Task_1'),('BAD_DEBT_PROV','Task_3'),('INVOICE_ISSUE','Task_2'),('INVOICE_ISSUE','Task_3'),('INVOICE_ISSUE','Task_4'),('INVOICE_ISSUE','Task_5'),('INVOICE_ISSUE','Task_6'),('INVOICE_MATCH','Task_4'),('INVOICE_MATCH','Task_2'),('INVOICE_MATCH','Task_3'),('INVOICE_MATCH','Task_8'),('INVOICE_MATCH','Task_11'),('INVOICE_MATCH','Task_9'),('INVOICE_MATCH_MANUAL','Task_2'),('INVOICE_MATCH_MANUAL','Task_5'),('RECEIVABLE','Task_2'),('RECEIVABLE','Task_3'),('RECEIVABLE','Task_5'),('RECEIVABLE_COLLECT','Task_3'),('RECEIVABLE_COLLECT','Task_7'),('RECEIVABLE_COLLECT','Task_8'),('RECON_ACTIVE','Task_5'),('RECON_ACTIVE','Task_6'),('RECON_ACTIVE','Task_7'),('RECON_PASSIVE','Task_3'),('RECON_PASSIVE','Task_7'),('RECON_PASSIVE','Task_8'));

-- 1) 逐节点对齐 node_name / assign_type / assign_value / empty_strategy / timeout_hours / timeout_action
-- AR_RECON.Task_4  客户核对并出具余额调节表【应收会计】
UPDATE wf_node_config
   SET node_name      = '客户核对并出具余额调节表【应收会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AR',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'AR_RECON' AND node_key = 'Task_4';

-- AR_RECON.Task_6  调查和确认差异【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '调查和确认差异【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'AR_RECON' AND node_key = 'Task_6';

-- AR_RECON.Task_8  审核确认差异【财务经理】
UPDATE wf_node_config
   SET node_name      = '审核确认差异【财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_MANAGER',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'AR_RECON' AND node_key = 'Task_8';

-- AR_REPORT.Task_4_0  板块财务经理审阅【板块财务经理】
UPDATE wf_node_config
   SET node_name      = '板块财务经理审阅【板块财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_FIN_MANAGER',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'AR_REPORT' AND node_key = 'Task_4_0';

-- AR_REPORT.Task_4_1  财务总监审阅【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审阅【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'AR_REPORT' AND node_key = 'Task_4_1';

-- AR_REPORT.Task_4_2  分管领导审阅【分管领导】
UPDATE wf_node_config
   SET node_name      = '分管领导审阅【分管领导】',
       assign_type    = 'ROLE',
       assign_value   = 'DIV_LEADER',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'AR_REPORT' AND node_key = 'Task_4_2';

-- BAD_DEBT_CONFIRM.Task_2  业务部门负责人审核【部门经理】
UPDATE wf_node_config
   SET node_name      = '业务部门负责人审核【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'BAD_DEBT_CONFIRM' AND node_key = 'Task_2';

-- BAD_DEBT_CONFIRM.Task_3  往来结算岗审核【应收会计】
UPDATE wf_node_config
   SET node_name      = '往来结算岗审核【应收会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AR',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'BAD_DEBT_CONFIRM' AND node_key = 'Task_3';

-- BAD_DEBT_CONFIRM.Task_4  董办/董会上会决议【董办/总裁办公会】
UPDATE wf_node_config
   SET node_name      = '董办/董会上会决议【董办/总裁办公会】',
       assign_type    = 'ROLE',
       assign_value   = 'BOARD_OFFICE',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'BAD_DEBT_CONFIRM' AND node_key = 'Task_4';

-- BAD_DEBT_CONFIRM.Task_5  基地财务共享支持并确认核销【共享支持岗】
UPDATE wf_node_config
   SET node_name      = '基地财务共享支持并确认核销【共享支持岗】',
       assign_type    = 'ROLE',
       assign_value   = 'SHARING_SUPPORT',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'BAD_DEBT_CONFIRM' AND node_key = 'Task_5';

-- BAD_DEBT_PROV.Task_1  按账龄分析与计提比例确认计提金额【应收会计】
UPDATE wf_node_config
   SET node_name      = '按账龄分析与计提比例确认计提金额【应收会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AR',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'BAD_DEBT_PROV' AND node_key = 'Task_1';

-- BAD_DEBT_PROV.Task_3  复核会计凭证【总账会计】
UPDATE wf_node_config
   SET node_name      = '复核会计凭证【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'BAD_DEBT_PROV' AND node_key = 'Task_3';

-- INVOICE_ISSUE.Task_2  直线上级审核业务真实性【直线上级】
UPDATE wf_node_config
   SET node_name      = '直线上级审核业务真实性【直线上级】',
       assign_type    = 'ROLE',
       assign_value   = 'DIRECT_SUPERVISOR',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_ISSUE' AND node_key = 'Task_2';

-- INVOICE_ISSUE.Task_3  基地财务经理审核【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_ISSUE' AND node_key = 'Task_3';

-- INVOICE_ISSUE.Task_4  项目管理中心总监合规审核【项目管理中心总监】
UPDATE wf_node_config
   SET node_name      = '项目管理中心总监合规审核【项目管理中心总监】',
       assign_type    = 'ROLE',
       assign_value   = 'PM_CENTER_DIRECTOR',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_ISSUE' AND node_key = 'Task_4';

-- INVOICE_ISSUE.Task_5  财务总监审批【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_ISSUE' AND node_key = 'Task_5';

-- INVOICE_ISSUE.Task_6  共享审核报账单【共享会计】
UPDATE wf_node_config
   SET node_name      = '共享审核报账单【共享会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_AUDITOR',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_ISSUE' AND node_key = 'Task_6';

-- INVOICE_MATCH.Task_4  业务经办确认差异原因及处理意见【普通员工】
UPDATE wf_node_config
   SET node_name      = '业务经办确认差异原因及处理意见【普通员工】',
       assign_type    = 'ROLE',
       assign_value   = 'COMMON_EMP',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_MATCH' AND node_key = 'Task_4';

-- INVOICE_MATCH.Task_2  财务共享往来会计审核【财务专员】
UPDATE wf_node_config
   SET node_name      = '财务共享往来会计审核【财务专员】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_STAFF',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_MATCH' AND node_key = 'Task_2';

-- INVOICE_MATCH.Task_3  基地财务经理审核【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_MATCH' AND node_key = 'Task_3';

-- INVOICE_MATCH.Task_8  FSSC成本费用岗审核【成本费用岗】
UPDATE wf_node_config
   SET node_name      = 'FSSC成本费用岗审核【成本费用岗】',
       assign_type    = 'ROLE',
       assign_value   = 'COST_STAFF',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_MATCH' AND node_key = 'Task_8';

-- INVOICE_MATCH.Task_11  FSSC资产核算岗审核【共享会计】
UPDATE wf_node_config
   SET node_name      = 'FSSC资产核算岗审核【共享会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_AUDITOR',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_MATCH' AND node_key = 'Task_11';

-- INVOICE_MATCH.Task_9  FSSC稽核核算岗终审【财务审核岗】
UPDATE wf_node_config
   SET node_name      = 'FSSC稽核核算岗终审【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_MATCH' AND node_key = 'Task_9';

-- INVOICE_MATCH_MANUAL.Task_2  发票合规审核【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '发票合规审核【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_MATCH_MANUAL' AND node_key = 'Task_2';

-- INVOICE_MATCH_MANUAL.Task_5  手工三单匹配确认【应付会计】
UPDATE wf_node_config
   SET node_name      = 'IFS 手工三单匹配确认【应付会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AP',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'INVOICE_MATCH_MANUAL' AND node_key = 'Task_5';

-- RECEIVABLE.Task_2  查询收款信息并提交收款认领单【资金支付岗】
UPDATE wf_node_config
   SET node_name      = '查询收款信息并提交收款认领单【资金支付岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FUND_PAY_STAFF',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'RECEIVABLE' AND node_key = 'Task_2';

-- RECEIVABLE.Task_3  勾选应收清单并核销应收账款【应收会计】
UPDATE wf_node_config
   SET node_name      = '勾选应收清单并核销应收账款【应收会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AR',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'RECEIVABLE' AND node_key = 'Task_3';

-- RECEIVABLE.Task_5  邮件通知项目经理、项目会计和相关领导【项目经理/费用承担部门负责人】
UPDATE wf_node_config
   SET node_name      = '邮件通知项目经理、项目会计和相关领导【项目经理/费用承担部门负责人】',
       assign_type    = 'ROLE',
       assign_value   = 'PROJECT_MANAGER',
       empty_strategy = 'SKIP',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'RECEIVABLE' AND node_key = 'Task_5';

-- RECEIVABLE_COLLECT.Task_3  逾期催收执行并记录（到期提醒／7天／15天／每工作日两次催款函）【财务专员】
UPDATE wf_node_config
   SET node_name      = '逾期催收执行并留痕（到期提醒／7天／15天／每工作日两次催款函）【财务专员】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_STAFF',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 168,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'RECEIVABLE_COLLECT' AND node_key = 'Task_3';

-- RECEIVABLE_COLLECT.Task_7  逾期满60天资料整理归档并移交板块法律部【档案管理员】
UPDATE wf_node_config
   SET node_name      = '逾期满60天资料整理归档并移交板块法律部【档案管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'FM_ARCHIVIST',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 168,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'RECEIVABLE_COLLECT' AND node_key = 'Task_7';

-- RECEIVABLE_COLLECT.Task_8  采用法律手段强力催收【应收会计】
UPDATE wf_node_config
   SET node_name      = '采用法律手段强力催收【应收会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AR',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'RECEIVABLE_COLLECT' AND node_key = 'Task_8';

-- RECON_ACTIVE.Task_5  调查和确认差异【对账专员】
UPDATE wf_node_config
   SET node_name      = '调查和确认差异【对账专员】',
       assign_type    = 'ROLE',
       assign_value   = 'RECON_CLERK',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'RECON_ACTIVE' AND node_key = 'Task_5';

-- RECON_ACTIVE.Task_6  审核确认差异【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '审核确认差异【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'RECON_ACTIVE' AND node_key = 'Task_6';

-- RECON_ACTIVE.Task_7  编制并复核调整凭证【总账会计】
UPDATE wf_node_config
   SET node_name      = '编制并复核调整凭证【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'RECON_ACTIVE' AND node_key = 'Task_7';

-- RECON_PASSIVE.Task_3  核对供应商反馈对账结果并调查确认差异【对账专员】
UPDATE wf_node_config
   SET node_name      = '核对供应商反馈对账结果并调查确认差异【对账专员】',
       assign_type    = 'ROLE',
       assign_value   = 'RECON_CLERK',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'RECON_PASSIVE' AND node_key = 'Task_3';

-- RECON_PASSIVE.Task_7  审核确认差异【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '审核确认差异【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'RECON_PASSIVE' AND node_key = 'Task_7';

-- RECON_PASSIVE.Task_8  编制并复核调整凭证【总账会计】
UPDATE wf_node_config
   SET node_name      = '编制并复核调整凭证【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'RECON_PASSIVE' AND node_key = 'Task_8';

-- 2) 复核 A：逐节点期望值对账（期望 0 行；输出任一行即为两处真值漂移）
SELECT e.model_key, e.node_key, e.exp_role AS bpmn_candidate_groups, c.assign_value AS config_assign_value,
       e.exp_name AS bpmn_name, c.node_name AS config_name, e.exp_es AS exp_empty, c.empty_strategy AS got_empty
  FROM (
    SELECT 'AR_RECON' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'FIN_AR' exp_role, '客户核对并出具余额调节表【应收会计】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'AR_RECON' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '调查和确认差异【财务审核岗】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'AR_RECON' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'FIN_MANAGER' exp_role, '审核确认差异【财务经理】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'AR_REPORT' model_key, 'Task_4_0' node_key, 'ROLE' exp_type, 'PLATE_FIN_MANAGER' exp_role, '板块财务经理审阅【板块财务经理】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'AR_REPORT' model_key, 'Task_4_1' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审阅【财务总监】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'AR_REPORT' model_key, 'Task_4_2' node_key, 'ROLE' exp_type, 'DIV_LEADER' exp_role, '分管领导审阅【分管领导】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'BAD_DEBT_CONFIRM' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '业务部门负责人审核【部门经理】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'BAD_DEBT_CONFIRM' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_AR' exp_role, '往来结算岗审核【应收会计】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'BAD_DEBT_CONFIRM' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'BOARD_OFFICE' exp_role, '董办/董会上会决议【董办/总裁办公会】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'BAD_DEBT_CONFIRM' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'SHARING_SUPPORT' exp_role, '基地财务共享支持并确认核销【共享支持岗】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'BAD_DEBT_PROV' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'FIN_AR' exp_role, '按账龄分析与计提比例确认计提金额【应收会计】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'BAD_DEBT_PROV' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '复核会计凭证【总账会计】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_ISSUE' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'DIRECT_SUPERVISOR' exp_role, '直线上级审核业务真实性【直线上级】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_ISSUE' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核【基地财务经理】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_ISSUE' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'PM_CENTER_DIRECTOR' exp_role, '项目管理中心总监合规审核【项目管理中心总监】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_ISSUE' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批【财务总监】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_ISSUE' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'FSSC_AUDITOR' exp_role, '共享审核报账单【共享会计】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_MATCH' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'COMMON_EMP' exp_role, '业务经办确认差异原因及处理意见【普通员工】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_MATCH' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_STAFF' exp_role, '财务共享往来会计审核【财务专员】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_MATCH' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核【基地财务经理】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_MATCH' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'COST_STAFF' exp_role, 'FSSC成本费用岗审核【成本费用岗】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_MATCH' model_key, 'Task_11' node_key, 'ROLE' exp_type, 'FSSC_AUDITOR' exp_role, 'FSSC资产核算岗审核【共享会计】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_MATCH' model_key, 'Task_9' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, 'FSSC稽核核算岗终审【财务审核岗】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_MATCH_MANUAL' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '发票合规审核【财务审核岗】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'INVOICE_MATCH_MANUAL' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'FIN_AP' exp_role, 'IFS 手工三单匹配确认【应付会计】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'RECEIVABLE' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FUND_PAY_STAFF' exp_role, '查询收款信息并提交收款认领单【资金支付岗】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'RECEIVABLE' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_AR' exp_role, '勾选应收清单并核销应收账款【应收会计】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'RECEIVABLE' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'PROJECT_MANAGER' exp_role, '邮件通知项目经理、项目会计和相关领导【项目经理/费用承担部门负责人】' exp_name, 'SKIP' exp_es
    UNION ALL
    SELECT 'RECEIVABLE_COLLECT' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_STAFF' exp_role, '逾期催收执行并留痕（到期提醒／7天／15天／每工作日两次催款函）【财务专员】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'RECEIVABLE_COLLECT' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FM_ARCHIVIST' exp_role, '逾期满60天资料整理归档并移交板块法律部【档案管理员】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'RECEIVABLE_COLLECT' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'FIN_AR' exp_role, '采用法律手段强力催收【应收会计】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'RECON_ACTIVE' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'RECON_CLERK' exp_role, '调查和确认差异【对账专员】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'RECON_ACTIVE' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '审核确认差异【基地财务经理】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'RECON_ACTIVE' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '编制并复核调整凭证【总账会计】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'RECON_PASSIVE' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'RECON_CLERK' exp_role, '核对供应商反馈对账结果并调查确认差异【对账专员】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'RECON_PASSIVE' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '审核确认差异【基地财务经理】' exp_name, 'ESCALATE' exp_es
    UNION ALL
    SELECT 'RECON_PASSIVE' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '编制并复核调整凭证【总账会计】' exp_name, 'ESCALATE' exp_es
  ) e
  LEFT JOIN wf_node_config c ON c.model_key = e.model_key AND c.node_key = e.node_key AND c.del_flag = 0
 WHERE c.node_key IS NULL
    OR IFNULL(c.assign_type, '') <> e.exp_type
    OR IFNULL(c.assign_value, '') <> e.exp_role
    OR IFNULL(c.node_name, '') <> e.exp_name
    OR IFNULL(c.empty_strategy, '') <> e.exp_es;

-- 3) 复核 B：本族 22 个角色的存在性与持岗人数（本库 sys_role/sys_user.status 取值为 'NORMAL'，不是 '0'）
SELECT r.role_code, ANY_VALUE(r.role_name) role_name, ANY_VALUE(r.status) role_status,
       COUNT(DISTINCT CASE WHEN u.status = 'NORMAL' AND u.del_flag = 0 THEN ur.user_id END) holders_active
  FROM sys_role r
  LEFT JOIN sys_user_role ur ON ur.role_id = r.id
  LEFT JOIN sys_user u ON u.id = ur.user_id AND u.status = 'NORMAL' AND u.del_flag = 0
 WHERE r.role_code IN ('FIN_AR','FIN_AUDIT','FIN_MANAGER','PLATE_FIN_MANAGER','CFO','DIV_LEADER','DEPT_MANAGER','BOARD_OFFICE','SHARING_SUPPORT','FIN_GL','DIRECT_SUPERVISOR','BASE_FIN_MANAGER','PM_CENTER_DIRECTOR','FSSC_AUDITOR','COMMON_EMP','FIN_STAFF','COST_STAFF','FIN_AP','FUND_PAY_STAFF','PROJECT_MANAGER','FM_ARCHIVIST','RECON_CLERK') AND r.del_flag = 0
 GROUP BY r.role_code ORDER BY holders_active, r.role_code;

-- 4) 复核 C：配置行盘点 = 新 BPMN 有效节点 + 出链后的幽灵行（仅盘点，不改）
SELECT model_key, COUNT(*) rows_total,
       SUM((model_key, node_key) IN (('AR_RECON','Task_4'),('AR_RECON','Task_6'),('AR_RECON','Task_8'),('AR_REPORT','Task_4_0'),('AR_REPORT','Task_4_1'),('AR_REPORT','Task_4_2'),('BAD_DEBT_CONFIRM','Task_2'),('BAD_DEBT_CONFIRM','Task_3'),('BAD_DEBT_CONFIRM','Task_4'),('BAD_DEBT_CONFIRM','Task_5'),('BAD_DEBT_PROV','Task_1'),('BAD_DEBT_PROV','Task_3'),('INVOICE_ISSUE','Task_2'),('INVOICE_ISSUE','Task_3'),('INVOICE_ISSUE','Task_4'),('INVOICE_ISSUE','Task_5'),('INVOICE_ISSUE','Task_6'),('INVOICE_MATCH','Task_4'),('INVOICE_MATCH','Task_2'),('INVOICE_MATCH','Task_3'),('INVOICE_MATCH','Task_8'),('INVOICE_MATCH','Task_11'),('INVOICE_MATCH','Task_9'),('INVOICE_MATCH_MANUAL','Task_2'),('INVOICE_MATCH_MANUAL','Task_5'),('RECEIVABLE','Task_2'),('RECEIVABLE','Task_3'),('RECEIVABLE','Task_5'),('RECEIVABLE_COLLECT','Task_3'),('RECEIVABLE_COLLECT','Task_7'),('RECEIVABLE_COLLECT','Task_8'),('RECON_ACTIVE','Task_5'),('RECON_ACTIVE','Task_6'),('RECON_ACTIVE','Task_7'),('RECON_PASSIVE','Task_3'),('RECON_PASSIVE','Task_7'),('RECON_PASSIVE','Task_8'))) rows_in_new_bpmn
  FROM wf_node_config
 WHERE model_key IN ('AR_RECON','AR_REPORT','BAD_DEBT_CONFIRM','BAD_DEBT_PROV','INVOICE_ISSUE','INVOICE_MATCH',
                     'INVOICE_MATCH_MANUAL','RECEIVABLE','RECEIVABLE_COLLECT','RECON_ACTIVE','RECON_PASSIVE')
   AND del_flag = 0
 GROUP BY model_key ORDER BY model_key;
