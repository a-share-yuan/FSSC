-- V1.4.29  批次2 第四族（资产 FA_*）配置侧对齐：wf_node_config <- 已发布 BPMN 唯一真值
-- 前置：11 个模型已 /api/tenant/wf/designer/publish 重发布（FA_ADD / FA_ALIENATE / FA_DISPOSE / FA_DEPRECIATION / FA_IMPAIRMENT / FA_TRANSFER / FA_TRANSFER_CROSS / FA_CER / FA_CHECK_DIFF / FA_VALUE_ADJUST / FA_CIP_TO_ASSET），
--   发布前回读复核 11/11 与 draft 逐字节一致、在途实例 0（wf_instance 与 ACT_RU_EXECUTION 双口径均为 0）。
-- 结构：本族 11 模型 92 个人工节点 -> 60 个（-32，-35%）；
--   作业池 14 个（handling_mode=POOL）、排他网关 16 个（金额 DOA）、并联会审 2 组（FA_ADD 三道验收、FA_CHECK_DIFF 资产+稽核）。
-- 已出链旧节点的配置行按批次2口径【保留不删】（DB 增量原则）：引擎按 BPMN 节点反查配置，幽灵行不产生运行时影响，仅在复核 C 盘点计数。
-- 本文件做三件事：1) 60 个已存在节点对齐 node_name/assign_type/assign_value/handling_mode/empty_strategy/超时；
--   2) 0 个新 key 插行；3) 复核 A~E。
-- 改岗清单（BPMN candidateGroups 与旧 assign_value 不同者）：
--   FA_ALIENATE.Task_2 DIRECT_SUPERVISOR->DEPT_MANAGER、FA_DEPRECIATION.Task_3 FA_ASSET_ACCOUNT->FIN_GL、FA_IMPAIRMENT.Task_4 FIN_GL->CFO、FA_TRANSFER.Task_3 DIRECT_SUPERVISOR->DEPT_MANAGER、FA_CER.Task_1 3906->FA_ASSET_ADMIN、FA_VALUE_ADJUST.Task_7 FA_ASSET_ADMIN->FA_ASSET_ACCOUNT、FA_CIP_TO_ASSET.Task_6 NULL->FA_ASSET_ACCOUNT
-- 档位口径（方案 §5.9）：授权层级(GM/BASE_GM/CFO/PRESIDENT/板块分管领导) 48h AUTO_REMIND 且永不 AUTO_PASS；
--   集体决策 BOARD_OFFICE 168h NOTIFY；共享审单(FSSC_AUDITOR/FIN_AUDIT/SCAN_STAFF) 8h AUTO_REMIND；
--   业务审批 24h AUTO_REMIND；作业池 48h AUTO_REMIND。empty_strategy 全族 ESCALATE
--   （ERROR 会在零持有人公司直接抛错卡死；作业池不用 §5.9 的 SKIP —— 建档/清卡/制卡是"必须有人做的事"，SKIP 会事无人做且不留待办）。
-- allow_transfer / multi_type / auto_pass_self / form_perm 一律不动（沿用批次1 定档）。
-- 不删任何数据；回滚见 V1.4.29_fam4_nodeconfig_align_rollback.sql
-- 执行：mysql -uroot -p123456 -h127.0.0.1 -P3306 -Dfssc --default-character-set=utf8mb4 < 本文件

-- 0) 备份将被改动的行（幂等：表已存在则跳过，备份表不删）
CREATE TABLE IF NOT EXISTS wf_node_config_bak_V1429_20260922 AS
SELECT * FROM wf_node_config WHERE (model_key, node_key) IN (('FA_ADD','Task_1'),('FA_ADD','Task_2'),('FA_ADD','Task_3'),('FA_ADD','Task_4'),('FA_ADD','Task_5'),('FA_ADD','Task_6'),('FA_ADD','Task_7'),('FA_ALIENATE','Task_1'),('FA_ALIENATE','Task_2'),('FA_ALIENATE','Task_4'),('FA_ALIENATE','Task_3'),('FA_ALIENATE','Task_6'),('FA_ALIENATE','Task_7'),('FA_DISPOSE','Task_3'),('FA_DISPOSE','Task_4'),('FA_DISPOSE','Task_5'),('FA_DISPOSE','Task_6'),('FA_DISPOSE','Task_9'),('FA_DISPOSE','Task_7'),('FA_DISPOSE','Task_10'),('FA_DEPRECIATION','Task_1'),('FA_DEPRECIATION','Task_3'),('FA_IMPAIRMENT','Task_1'),('FA_IMPAIRMENT','Task_3'),('FA_IMPAIRMENT','Task_4'),('FA_TRANSFER','Task_2'),('FA_TRANSFER','Task_3'),('FA_TRANSFER','Task_4'),('FA_TRANSFER','Task_5'),('FA_TRANSFER','Task_7'),('FA_TRANSFER_CROSS','Task_2'),('FA_TRANSFER_CROSS','Task_3'),('FA_TRANSFER_CROSS','Task_4'),('FA_TRANSFER_CROSS','Task_7'),('FA_TRANSFER_CROSS','Task_8'),('FA_TRANSFER_CROSS','Task_10'),('FA_TRANSFER_CROSS','Task_12'),('FA_CER','Task_1'),('FA_CER','Task_3'),('FA_CER','Task_7'),('FA_CER','Task_8'),('FA_CER','Task_9'),('FA_CER','Task_5'),('FA_CER','Task_4'),('FA_CHECK_DIFF','Task_2'),('FA_CHECK_DIFF','Task_3'),('FA_CHECK_DIFF','Task_5'),('FA_CHECK_DIFF','Task_6'),('FA_CHECK_DIFF','Task_7'),('FA_CHECK_DIFF','Task_8'),('FA_VALUE_ADJUST','Task_2'),('FA_VALUE_ADJUST','Task_4'),('FA_VALUE_ADJUST','Task_3'),('FA_VALUE_ADJUST','Task_5'),('FA_VALUE_ADJUST','Task_6'),('FA_VALUE_ADJUST','Task_7'),('FA_CIP_TO_ASSET','Task_2'),('FA_CIP_TO_ASSET','Task_3'),('FA_CIP_TO_ASSET','Task_6'),('FA_CIP_TO_ASSET','Task_8'));

-- 0b) 本族无新 key：60 个 BPMN 节点的 node_key 全部沿用原模型 Task_N（结构重造只做删除/改岗/改档位），
--     故本文件为纯 UPDATE、零 INSERT，插行体检跳过。

-- 1) 逐节点对齐（60 行）
-- FA_ADD.Task_1  联合验收、打印资产编码并建档（作业池）【资产管理员】
UPDATE wf_node_config
   SET node_name      = '联合验收、打印资产编码并建档（作业池）【资产管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ADMIN',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_ADD' AND node_key = 'Task_1';

-- FA_ADD.Task_2  使用部门确认验收【部门经理】
UPDATE wf_node_config
   SET node_name      = '使用部门确认验收【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_ADD' AND node_key = 'Task_2';

-- FA_ADD.Task_3  资产管理岗审核【资产管理员】
UPDATE wf_node_config
   SET node_name      = '资产管理岗审核【资产管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ADMIN',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_ADD' AND node_key = 'Task_3';

-- FA_ADD.Task_4  财务经理/稽核复核【财务经理】
UPDATE wf_node_config
   SET node_name      = '财务经理/稽核复核【财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_ADD' AND node_key = 'Task_4';

-- FA_ADD.Task_5  总经理审批（超 10 万）【总经理】
UPDATE wf_node_config
   SET node_name      = '总经理审批（超 10 万）【总经理】',
       assign_type    = 'ROLE',
       assign_value   = 'GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_ADD' AND node_key = 'Task_5';

-- FA_ADD.Task_6  板块分管领导审批（超 50 万）【板块分管领导】
UPDATE wf_node_config
   SET node_name      = '板块分管领导审批（超 50 万）【板块分管领导】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_DIV_LEADER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_ADD' AND node_key = 'Task_6';

-- FA_ADD.Task_7  总裁审批（超 500 万）【总裁】
UPDATE wf_node_config
   SET node_name      = '总裁审批（超 500 万）【总裁】',
       assign_type    = 'ROLE',
       assign_value   = 'PRESIDENT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_ADD' AND node_key = 'Task_7';

-- FA_ALIENATE.Task_1  采购招标三方比价与处置销售协议签订（作业池）【采购经理】
UPDATE wf_node_config
   SET node_name      = '采购招标三方比价与处置销售协议签订（作业池）【采购经理】',
       assign_type    = 'ROLE',
       assign_value   = 'PURCHASE_MANAGER',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_ALIENATE' AND node_key = 'Task_1';

-- FA_ALIENATE.Task_2  业务部门审核处置【部门经理】
UPDATE wf_node_config
   SET node_name      = '业务部门审核处置【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_ALIENATE' AND node_key = 'Task_2';

-- FA_ALIENATE.Task_4  财务经理审批【财务经理】
UPDATE wf_node_config
   SET node_name      = '财务经理审批【财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_ALIENATE' AND node_key = 'Task_4';

-- FA_ALIENATE.Task_3  基地总经理审批（超 20 万）【基地总经理】
UPDATE wf_node_config
   SET node_name      = '基地总经理审批（超 20 万）【基地总经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_ALIENATE' AND node_key = 'Task_3';

-- FA_ALIENATE.Task_6  财务总监审批（超 50 万）【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批（超 50 万）【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_ALIENATE' AND node_key = 'Task_6';

-- FA_ALIENATE.Task_7  董办/董会上会决议（超 200 万）【董办/总裁办公会】
UPDATE wf_node_config
   SET node_name      = '董办/董会上会决议（超 200 万）【董办/总裁办公会】',
       assign_type    = 'ROLE',
       assign_value   = 'BOARD_OFFICE',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 168,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'FA_ALIENATE' AND node_key = 'Task_7';

-- FA_DISPOSE.Task_3  归口部门经理审核报废【部门经理】
UPDATE wf_node_config
   SET node_name      = '归口部门经理审核报废【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_DISPOSE' AND node_key = 'Task_3';

-- FA_DISPOSE.Task_4  基地财务经理审核【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_DISPOSE' AND node_key = 'Task_4';

-- FA_DISPOSE.Task_5  资产管理岗复核【资产管理员】
UPDATE wf_node_config
   SET node_name      = '资产管理岗复核【资产管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ADMIN',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_DISPOSE' AND node_key = 'Task_5';

-- FA_DISPOSE.Task_6  总经理审批（超 20 万）【总经理】
UPDATE wf_node_config
   SET node_name      = '总经理审批（超 20 万）【总经理】',
       assign_type    = 'ROLE',
       assign_value   = 'GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_DISPOSE' AND node_key = 'Task_6';

-- FA_DISPOSE.Task_9  财务总监审批（超 50 万）【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批（超 50 万）【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_DISPOSE' AND node_key = 'Task_9';

-- FA_DISPOSE.Task_7  板块分管领导审批（超 200 万）【板块分管领导】
UPDATE wf_node_config
   SET node_name      = '板块分管领导审批（超 200 万）【板块分管领导】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_DIV_LEADER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_DISPOSE' AND node_key = 'Task_7';

-- FA_DISPOSE.Task_10  董办/董会上会决议（超 500 万）【董办/总裁办公会】
UPDATE wf_node_config
   SET node_name      = '董办/董会上会决议（超 500 万）【董办/总裁办公会】',
       assign_type    = 'ROLE',
       assign_value   = 'BOARD_OFFICE',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 168,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'FA_DISPOSE' AND node_key = 'Task_10';

-- FA_DEPRECIATION.Task_1  运行折旧、核对折旧明细并修改资产卡片（作业池）【资产核算岗】
UPDATE wf_node_config
   SET node_name      = '运行折旧、核对折旧明细并修改资产卡片（作业池）【资产核算岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ACCOUNT',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_DEPRECIATION' AND node_key = 'Task_1';

-- FA_DEPRECIATION.Task_3  审核折旧记账单【总账会计】
UPDATE wf_node_config
   SET node_name      = '审核折旧记账单【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_DEPRECIATION' AND node_key = 'Task_3';

-- FA_IMPAIRMENT.Task_1  减值测试底稿编制与影像齐套（作业池）【资产管理员】
UPDATE wf_node_config
   SET node_name      = '减值测试底稿编制与影像齐套（作业池）【资产管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ADMIN',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_IMPAIRMENT' AND node_key = 'Task_1';

-- FA_IMPAIRMENT.Task_3  审核减值计提与预制凭证【总账会计】
UPDATE wf_node_config
   SET node_name      = '审核减值计提与预制凭证【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_IMPAIRMENT' AND node_key = 'Task_3';

-- FA_IMPAIRMENT.Task_4  财务总监审批减值准备（超 50 万）【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批减值准备（超 50 万）【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_IMPAIRMENT' AND node_key = 'Task_4';

-- FA_TRANSFER.Task_2  调拨影像扫描与单据齐套（作业池）【财务扫描岗】
UPDATE wf_node_config
   SET node_name      = '调拨影像扫描与单据齐套（作业池）【财务扫描岗】',
       assign_type    = 'ROLE',
       assign_value   = 'SCAN_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_TRANSFER' AND node_key = 'Task_2';

-- FA_TRANSFER.Task_3  调出部门审核【部门经理】
UPDATE wf_node_config
   SET node_name      = '调出部门审核【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_TRANSFER' AND node_key = 'Task_3';

-- FA_TRANSFER.Task_4  共享中心资产核算岗审核调拨【资产核算岗】
UPDATE wf_node_config
   SET node_name      = '共享中心资产核算岗审核调拨【资产核算岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ACCOUNT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_TRANSFER' AND node_key = 'Task_4';

-- FA_TRANSFER.Task_5  基地财务经理审核【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_TRANSFER' AND node_key = 'Task_5';

-- FA_TRANSFER.Task_7  调拨后维护资产卡片归属部门（作业池）【资产核算岗】
UPDATE wf_node_config
   SET node_name      = '调拨后维护资产卡片归属部门（作业池）【资产核算岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ACCOUNT',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_TRANSFER' AND node_key = 'Task_7';

-- FA_TRANSFER_CROSS.Task_2  跨法人调拨影像扫描与单据齐套（作业池）【财务扫描岗】
UPDATE wf_node_config
   SET node_name      = '跨法人调拨影像扫描与单据齐套（作业池）【财务扫描岗】',
       assign_type    = 'ROLE',
       assign_value   = 'SCAN_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_TRANSFER_CROSS' AND node_key = 'Task_2';

-- FA_TRANSFER_CROSS.Task_3  共享中心资产核算岗审核调拨申请【资产核算岗】
UPDATE wf_node_config
   SET node_name      = '共享中心资产核算岗审核调拨申请【资产核算岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ACCOUNT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_TRANSFER_CROSS' AND node_key = 'Task_3';

-- FA_TRANSFER_CROSS.Task_4  双方基地财务经理审核【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '双方基地财务经理审核【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_TRANSFER_CROSS' AND node_key = 'Task_4';

-- FA_TRANSFER_CROSS.Task_7  财务审核岗审核视同销售与计税【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '财务审核岗审核视同销售与计税【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_TRANSFER_CROSS' AND node_key = 'Task_7';

-- FA_TRANSFER_CROSS.Task_8  卖方清卡、确认已开票并制作凭证（作业池）【资产核算岗】
UPDATE wf_node_config
   SET node_name      = '卖方清卡、确认已开票并制作凭证（作业池）【资产核算岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ACCOUNT',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_TRANSFER_CROSS' AND node_key = 'Task_8';

-- FA_TRANSFER_CROSS.Task_10  买方接收发票与采购校验（作业池）【应付会计】
UPDATE wf_node_config
   SET node_name      = '买方接收发票与采购校验（作业池）【应付会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AP',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_TRANSFER_CROSS' AND node_key = 'Task_10';

-- FA_TRANSFER_CROSS.Task_12  买方新增资产卡片与入账（作业池）【资产核算岗】
UPDATE wf_node_config
   SET node_name      = '买方新增资产卡片与入账（作业池）【资产核算岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ACCOUNT',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_TRANSFER_CROSS' AND node_key = 'Task_12';

-- FA_CER.Task_1  资产归口部门经办审核【资产管理员】
UPDATE wf_node_config
   SET node_name      = '资产归口部门经办审核【资产管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ADMIN',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CER' AND node_key = 'Task_1';

-- FA_CER.Task_3  板块归口部门经理审核【部门经理】
UPDATE wf_node_config
   SET node_name      = '板块归口部门经理审核【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CER' AND node_key = 'Task_3';

-- FA_CER.Task_7  基地财务经理审核【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CER' AND node_key = 'Task_7';

-- FA_CER.Task_8  共享中心资产核算岗审核【共享会计】
UPDATE wf_node_config
   SET node_name      = '共享中心资产核算岗审核【共享会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_AUDITOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CER' AND node_key = 'Task_8';

-- FA_CER.Task_9  共享中心稽核岗复核【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '共享中心稽核岗复核【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CER' AND node_key = 'Task_9';

-- FA_CER.Task_5  基地总经理审批（超 50 万）【基地总经理】
UPDATE wf_node_config
   SET node_name      = '基地总经理审批（超 50 万）【基地总经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CER' AND node_key = 'Task_5';

-- FA_CER.Task_4  板块分管领导审批（超 500 万）【板块分管领导】
UPDATE wf_node_config
   SET node_name      = '板块分管领导审批（超 500 万）【板块分管领导】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_DIV_LEADER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CER' AND node_key = 'Task_4';

-- FA_CHECK_DIFF.Task_2  业务部门经理审批差异【部门经理】
UPDATE wf_node_config
   SET node_name      = '业务部门经理审批差异【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CHECK_DIFF' AND node_key = 'Task_2';

-- FA_CHECK_DIFF.Task_3  基地财务经理审批差异【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审批差异【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CHECK_DIFF' AND node_key = 'Task_3';

-- FA_CHECK_DIFF.Task_5  财务总监审批（差异超 50 万）【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批（差异超 50 万）【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CHECK_DIFF' AND node_key = 'Task_5';

-- FA_CHECK_DIFF.Task_6  资产管理岗复核卡片差异【资产管理员】
UPDATE wf_node_config
   SET node_name      = '资产管理岗复核卡片差异【资产管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ADMIN',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CHECK_DIFF' AND node_key = 'Task_6';

-- FA_CHECK_DIFF.Task_7  稽核岗复核差异处理【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '稽核岗复核差异处理【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CHECK_DIFF' AND node_key = 'Task_7';

-- FA_CHECK_DIFF.Task_8  差异处理后维护资产卡片并调账（作业池）【资产核算岗】
UPDATE wf_node_config
   SET node_name      = '差异处理后维护资产卡片并调账（作业池）【资产核算岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ACCOUNT',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CHECK_DIFF' AND node_key = 'Task_8';

-- FA_VALUE_ADJUST.Task_2  业务部门经理审批原值调整【部门经理】
UPDATE wf_node_config
   SET node_name      = '业务部门经理审批原值调整【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_VALUE_ADJUST' AND node_key = 'Task_2';

-- FA_VALUE_ADJUST.Task_4  基地财务经理审核【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_VALUE_ADJUST' AND node_key = 'Task_4';

-- FA_VALUE_ADJUST.Task_3  基地总经理审批（调整超 5 万）【基地总经理】
UPDATE wf_node_config
   SET node_name      = '基地总经理审批（调整超 5 万）【基地总经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_VALUE_ADJUST' AND node_key = 'Task_3';

-- FA_VALUE_ADJUST.Task_5  财务总监审批（调整超 50 万）【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批（调整超 50 万）【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_VALUE_ADJUST' AND node_key = 'Task_5';

-- FA_VALUE_ADJUST.Task_6  稽核岗复核原值调整【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '稽核岗复核原值调整【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_VALUE_ADJUST' AND node_key = 'Task_6';

-- FA_VALUE_ADJUST.Task_7  调整生效后维护资产卡片并制作调整凭证（作业池）【资产核算岗】
UPDATE wf_node_config
   SET node_name      = '调整生效后维护资产卡片并制作调整凭证（作业池）【资产核算岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ACCOUNT',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_VALUE_ADJUST' AND node_key = 'Task_7';

-- FA_CIP_TO_ASSET.Task_2  转资资料扫描与现场验收齐套（作业池）【财务扫描岗】
UPDATE wf_node_config
   SET node_name      = '转资资料扫描与现场验收齐套（作业池）【财务扫描岗】',
       assign_type    = 'ROLE',
       assign_value   = 'SCAN_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CIP_TO_ASSET' AND node_key = 'Task_2';

-- FA_CIP_TO_ASSET.Task_3  审核转资资料与合规性【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '审核转资资料与合规性【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CIP_TO_ASSET' AND node_key = 'Task_3';

-- FA_CIP_TO_ASSET.Task_6  投资已登记确认与暂估冲回（作业池）【资产核算岗】
UPDATE wf_node_config
   SET node_name      = '投资已登记确认与暂估冲回（作业池）【资产核算岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ACCOUNT',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CIP_TO_ASSET' AND node_key = 'Task_6';

-- FA_CIP_TO_ASSET.Task_8  转固价值与记账单复核【总账会计】
UPDATE wf_node_config
   SET node_name      = '转固价值与记账单复核【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA_CIP_TO_ASSET' AND node_key = 'Task_8';

-- 3) 复核 A：逐节点期望值对账（期望 0 行；输出任一行即为 BPMN 与 config 两处真值漂移）
SELECT e.model_key, e.node_key, e.exp_role AS bpmn_groups, c.assign_value AS config_role,
       e.exp_name AS bpmn_name, c.node_name AS config_name, e.exp_es AS exp_empty, IFNULL(c.empty_strategy,'') got_empty,
       e.exp_th AS exp_th, IFNULL(c.timeout_hours,-1) got_th, e.exp_ta exp_ta, IFNULL(c.timeout_action,'') got_ta,
       e.exp_hm exp_hm, IFNULL(c.handling_mode,'') got_hm
  FROM (
    SELECT 'FA_ADD' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'FA_ASSET_ADMIN' exp_role, '联合验收、打印资产编码并建档（作业池）【资产管理员】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_ADD' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '使用部门确认验收【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_ADD' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FA_ASSET_ADMIN' exp_role, '资产管理岗审核【资产管理员】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_ADD' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'FIN_MANAGER' exp_role, '财务经理/稽核复核【财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_ADD' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'GM' exp_role, '总经理审批（超 10 万）【总经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_ADD' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'PLATE_DIV_LEADER' exp_role, '板块分管领导审批（超 50 万）【板块分管领导】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_ADD' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'PRESIDENT' exp_role, '总裁审批（超 500 万）【总裁】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_ALIENATE' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'PURCHASE_MANAGER' exp_role, '采购招标三方比价与处置销售协议签订（作业池）【采购经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_ALIENATE' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '业务部门审核处置【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_ALIENATE' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'FIN_MANAGER' exp_role, '财务经理审批【财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_ALIENATE' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'BASE_GM' exp_role, '基地总经理审批（超 20 万）【基地总经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_ALIENATE' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批（超 50 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_ALIENATE' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'BOARD_OFFICE' exp_role, '董办/董会上会决议（超 200 万）【董办/总裁办公会】' exp_name, 'ESCALATE' exp_es, 168 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_DISPOSE' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '归口部门经理审核报废【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_DISPOSE' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_DISPOSE' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'FA_ASSET_ADMIN' exp_role, '资产管理岗复核【资产管理员】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_DISPOSE' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'GM' exp_role, '总经理审批（超 20 万）【总经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_DISPOSE' model_key, 'Task_9' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批（超 50 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_DISPOSE' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'PLATE_DIV_LEADER' exp_role, '板块分管领导审批（超 200 万）【板块分管领导】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_DISPOSE' model_key, 'Task_10' node_key, 'ROLE' exp_type, 'BOARD_OFFICE' exp_role, '董办/董会上会决议（超 500 万）【董办/总裁办公会】' exp_name, 'ESCALATE' exp_es, 168 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_DEPRECIATION' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'FA_ASSET_ACCOUNT' exp_role, '运行折旧、核对折旧明细并修改资产卡片（作业池）【资产核算岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_DEPRECIATION' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '审核折旧记账单【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_IMPAIRMENT' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'FA_ASSET_ADMIN' exp_role, '减值测试底稿编制与影像齐套（作业池）【资产管理员】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_IMPAIRMENT' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '审核减值计提与预制凭证【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_IMPAIRMENT' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批减值准备（超 50 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_TRANSFER' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'SCAN_STAFF' exp_role, '调拨影像扫描与单据齐套（作业池）【财务扫描岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_TRANSFER' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '调出部门审核【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_TRANSFER' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'FA_ASSET_ACCOUNT' exp_role, '共享中心资产核算岗审核调拨【资产核算岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_TRANSFER' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_TRANSFER' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FA_ASSET_ACCOUNT' exp_role, '调拨后维护资产卡片归属部门（作业池）【资产核算岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_TRANSFER_CROSS' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'SCAN_STAFF' exp_role, '跨法人调拨影像扫描与单据齐套（作业池）【财务扫描岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_TRANSFER_CROSS' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FA_ASSET_ACCOUNT' exp_role, '共享中心资产核算岗审核调拨申请【资产核算岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_TRANSFER_CROSS' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '双方基地财务经理审核【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_TRANSFER_CROSS' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗审核视同销售与计税【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_TRANSFER_CROSS' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'FA_ASSET_ACCOUNT' exp_role, '卖方清卡、确认已开票并制作凭证（作业池）【资产核算岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_TRANSFER_CROSS' model_key, 'Task_10' node_key, 'ROLE' exp_type, 'FIN_AP' exp_role, '买方接收发票与采购校验（作业池）【应付会计】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_TRANSFER_CROSS' model_key, 'Task_12' node_key, 'ROLE' exp_type, 'FA_ASSET_ACCOUNT' exp_role, '买方新增资产卡片与入账（作业池）【资产核算岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_CER' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'FA_ASSET_ADMIN' exp_role, '资产归口部门经办审核【资产管理员】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CER' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '板块归口部门经理审核【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CER' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CER' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'FSSC_AUDITOR' exp_role, '共享中心资产核算岗审核【共享会计】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CER' model_key, 'Task_9' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '共享中心稽核岗复核【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CER' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'BASE_GM' exp_role, '基地总经理审批（超 50 万）【基地总经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CER' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'PLATE_DIV_LEADER' exp_role, '板块分管领导审批（超 500 万）【板块分管领导】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CHECK_DIFF' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '业务部门经理审批差异【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CHECK_DIFF' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审批差异【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CHECK_DIFF' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批（差异超 50 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CHECK_DIFF' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'FA_ASSET_ADMIN' exp_role, '资产管理岗复核卡片差异【资产管理员】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CHECK_DIFF' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '稽核岗复核差异处理【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CHECK_DIFF' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'FA_ASSET_ACCOUNT' exp_role, '差异处理后维护资产卡片并调账（作业池）【资产核算岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_VALUE_ADJUST' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '业务部门经理审批原值调整【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_VALUE_ADJUST' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_VALUE_ADJUST' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'BASE_GM' exp_role, '基地总经理审批（调整超 5 万）【基地总经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_VALUE_ADJUST' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批（调整超 50 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_VALUE_ADJUST' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '稽核岗复核原值调整【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_VALUE_ADJUST' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FA_ASSET_ACCOUNT' exp_role, '调整生效后维护资产卡片并制作调整凭证（作业池）【资产核算岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_CIP_TO_ASSET' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'SCAN_STAFF' exp_role, '转资资料扫描与现场验收齐套（作业池）【财务扫描岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_CIP_TO_ASSET' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核转资资料与合规性【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA_CIP_TO_ASSET' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'FA_ASSET_ACCOUNT' exp_role, '投资已登记确认与暂估冲回（作业池）【资产核算岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FA_CIP_TO_ASSET' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '转固价值与记账单复核【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
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

-- 4) 复核 B：本族 17 个角色的存在性与 co1 在编持岗人数（口径 status='NORMAL' 且 is_admin=0；
--    COMPANY 范围角色必须问"该公司有没有在编持有人"。active_co1=0 即为 §5.4 补岗清单候选，不是脚本缺陷）
SELECT r.role_code, ANY_VALUE(r.role_name) role_name, ANY_VALUE(r.org_scope) org_scope, ANY_VALUE(r.status) status,
       COUNT(DISTINCT ur.user_id) holders_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 THEN 1 ELSE 0 END) active_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 AND u.company_id = 1 THEN 1 ELSE 0 END) active_co1
  FROM sys_role r
  LEFT JOIN sys_user_role ur ON ur.role_id = r.id
  LEFT JOIN sys_user u ON u.id = ur.user_id AND u.del_flag = 0
 WHERE r.role_code IN ('BASE_FIN_MANAGER','BASE_GM','BOARD_OFFICE','CFO','DEPT_MANAGER','FA_ASSET_ACCOUNT','FA_ASSET_ADMIN','FIN_AP','FIN_AUDIT','FIN_GL','FIN_MANAGER','FSSC_AUDITOR','GM','PLATE_DIV_LEADER','PRESIDENT','PURCHASE_MANAGER','SCAN_STAFF') AND r.del_flag = 0
 GROUP BY r.role_code ORDER BY active_co1, r.role_code;

-- 5) 复核 C：配置行盘点 = 新 BPMN 有效节点(60) + 出链后的幽灵行（仅盘点，不改；期望 rows_in_new_bpmn 合计 60）
SELECT model_key, COUNT(*) rows_total,
       SUM((model_key, node_key) IN (('FA_ADD','Task_1'),('FA_ADD','Task_2'),('FA_ADD','Task_3'),('FA_ADD','Task_4'),('FA_ADD','Task_5'),('FA_ADD','Task_6'),('FA_ADD','Task_7'),('FA_ALIENATE','Task_1'),('FA_ALIENATE','Task_2'),('FA_ALIENATE','Task_4'),('FA_ALIENATE','Task_3'),('FA_ALIENATE','Task_6'),('FA_ALIENATE','Task_7'),('FA_DISPOSE','Task_3'),('FA_DISPOSE','Task_4'),('FA_DISPOSE','Task_5'),('FA_DISPOSE','Task_6'),('FA_DISPOSE','Task_9'),('FA_DISPOSE','Task_7'),('FA_DISPOSE','Task_10'),('FA_DEPRECIATION','Task_1'),('FA_DEPRECIATION','Task_3'),('FA_IMPAIRMENT','Task_1'),('FA_IMPAIRMENT','Task_3'),('FA_IMPAIRMENT','Task_4'),('FA_TRANSFER','Task_2'),('FA_TRANSFER','Task_3'),('FA_TRANSFER','Task_4'),('FA_TRANSFER','Task_5'),('FA_TRANSFER','Task_7'),('FA_TRANSFER_CROSS','Task_2'),('FA_TRANSFER_CROSS','Task_3'),('FA_TRANSFER_CROSS','Task_4'),('FA_TRANSFER_CROSS','Task_7'),('FA_TRANSFER_CROSS','Task_8'),('FA_TRANSFER_CROSS','Task_10'),('FA_TRANSFER_CROSS','Task_12'),('FA_CER','Task_1'),('FA_CER','Task_3'),('FA_CER','Task_7'),('FA_CER','Task_8'),('FA_CER','Task_9'),('FA_CER','Task_5'),('FA_CER','Task_4'),('FA_CHECK_DIFF','Task_2'),('FA_CHECK_DIFF','Task_3'),('FA_CHECK_DIFF','Task_5'),('FA_CHECK_DIFF','Task_6'),('FA_CHECK_DIFF','Task_7'),('FA_CHECK_DIFF','Task_8'),('FA_VALUE_ADJUST','Task_2'),('FA_VALUE_ADJUST','Task_4'),('FA_VALUE_ADJUST','Task_3'),('FA_VALUE_ADJUST','Task_5'),('FA_VALUE_ADJUST','Task_6'),('FA_VALUE_ADJUST','Task_7'),('FA_CIP_TO_ASSET','Task_2'),('FA_CIP_TO_ASSET','Task_3'),('FA_CIP_TO_ASSET','Task_6'),('FA_CIP_TO_ASSET','Task_8'))) rows_in_new_bpmn
  FROM wf_node_config
 WHERE model_key IN ('FA_ADD','FA_ALIENATE','FA_DISPOSE','FA_DEPRECIATION','FA_IMPAIRMENT','FA_TRANSFER','FA_TRANSFER_CROSS','FA_CER','FA_CHECK_DIFF','FA_VALUE_ADJUST','FA_CIP_TO_ASSET') AND del_flag = 0
 GROUP BY model_key ORDER BY model_key;

-- 6) 复核 D：作业池档位落地（期望 14 行 POOL）
SELECT model_key, node_key, handling_mode, assign_value, node_name FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('FA_ADD','Task_1'),('FA_ADD','Task_2'),('FA_ADD','Task_3'),('FA_ADD','Task_4'),('FA_ADD','Task_5'),('FA_ADD','Task_6'),('FA_ADD','Task_7'),('FA_ALIENATE','Task_1'),('FA_ALIENATE','Task_2'),('FA_ALIENATE','Task_4'),('FA_ALIENATE','Task_3'),('FA_ALIENATE','Task_6'),('FA_ALIENATE','Task_7'),('FA_DISPOSE','Task_3'),('FA_DISPOSE','Task_4'),('FA_DISPOSE','Task_5'),('FA_DISPOSE','Task_6'),('FA_DISPOSE','Task_9'),('FA_DISPOSE','Task_7'),('FA_DISPOSE','Task_10'),('FA_DEPRECIATION','Task_1'),('FA_DEPRECIATION','Task_3'),('FA_IMPAIRMENT','Task_1'),('FA_IMPAIRMENT','Task_3'),('FA_IMPAIRMENT','Task_4'),('FA_TRANSFER','Task_2'),('FA_TRANSFER','Task_3'),('FA_TRANSFER','Task_4'),('FA_TRANSFER','Task_5'),('FA_TRANSFER','Task_7'),('FA_TRANSFER_CROSS','Task_2'),('FA_TRANSFER_CROSS','Task_3'),('FA_TRANSFER_CROSS','Task_4'),('FA_TRANSFER_CROSS','Task_7'),('FA_TRANSFER_CROSS','Task_8'),('FA_TRANSFER_CROSS','Task_10'),('FA_TRANSFER_CROSS','Task_12'),('FA_CER','Task_1'),('FA_CER','Task_3'),('FA_CER','Task_7'),('FA_CER','Task_8'),('FA_CER','Task_9'),('FA_CER','Task_5'),('FA_CER','Task_4'),('FA_CHECK_DIFF','Task_2'),('FA_CHECK_DIFF','Task_3'),('FA_CHECK_DIFF','Task_5'),('FA_CHECK_DIFF','Task_6'),('FA_CHECK_DIFF','Task_7'),('FA_CHECK_DIFF','Task_8'),('FA_VALUE_ADJUST','Task_2'),('FA_VALUE_ADJUST','Task_4'),('FA_VALUE_ADJUST','Task_3'),('FA_VALUE_ADJUST','Task_5'),('FA_VALUE_ADJUST','Task_6'),('FA_VALUE_ADJUST','Task_7'),('FA_CIP_TO_ASSET','Task_2'),('FA_CIP_TO_ASSET','Task_3'),('FA_CIP_TO_ASSET','Task_6'),('FA_CIP_TO_ASSET','Task_8')) AND handling_mode = 'POOL'
 ORDER BY model_key, node_key;

-- 7) 复核 E：零 admin 硬门槛 —— 本族任一节点都不得由 admin 参与；点名残留（assign_value 是纯数字 userId）也算违规
SELECT c.model_key, c.node_key, c.assign_type, c.assign_value
  FROM wf_node_config c
 WHERE c.del_flag = 0 AND (c.model_key, c.node_key) IN (('FA_ADD','Task_1'),('FA_ADD','Task_2'),('FA_ADD','Task_3'),('FA_ADD','Task_4'),('FA_ADD','Task_5'),('FA_ADD','Task_6'),('FA_ADD','Task_7'),('FA_ALIENATE','Task_1'),('FA_ALIENATE','Task_2'),('FA_ALIENATE','Task_4'),('FA_ALIENATE','Task_3'),('FA_ALIENATE','Task_6'),('FA_ALIENATE','Task_7'),('FA_DISPOSE','Task_3'),('FA_DISPOSE','Task_4'),('FA_DISPOSE','Task_5'),('FA_DISPOSE','Task_6'),('FA_DISPOSE','Task_9'),('FA_DISPOSE','Task_7'),('FA_DISPOSE','Task_10'),('FA_DEPRECIATION','Task_1'),('FA_DEPRECIATION','Task_3'),('FA_IMPAIRMENT','Task_1'),('FA_IMPAIRMENT','Task_3'),('FA_IMPAIRMENT','Task_4'),('FA_TRANSFER','Task_2'),('FA_TRANSFER','Task_3'),('FA_TRANSFER','Task_4'),('FA_TRANSFER','Task_5'),('FA_TRANSFER','Task_7'),('FA_TRANSFER_CROSS','Task_2'),('FA_TRANSFER_CROSS','Task_3'),('FA_TRANSFER_CROSS','Task_4'),('FA_TRANSFER_CROSS','Task_7'),('FA_TRANSFER_CROSS','Task_8'),('FA_TRANSFER_CROSS','Task_10'),('FA_TRANSFER_CROSS','Task_12'),('FA_CER','Task_1'),('FA_CER','Task_3'),('FA_CER','Task_7'),('FA_CER','Task_8'),('FA_CER','Task_9'),('FA_CER','Task_5'),('FA_CER','Task_4'),('FA_CHECK_DIFF','Task_2'),('FA_CHECK_DIFF','Task_3'),('FA_CHECK_DIFF','Task_5'),('FA_CHECK_DIFF','Task_6'),('FA_CHECK_DIFF','Task_7'),('FA_CHECK_DIFF','Task_8'),('FA_VALUE_ADJUST','Task_2'),('FA_VALUE_ADJUST','Task_4'),('FA_VALUE_ADJUST','Task_3'),('FA_VALUE_ADJUST','Task_5'),('FA_VALUE_ADJUST','Task_6'),('FA_VALUE_ADJUST','Task_7'),('FA_CIP_TO_ASSET','Task_2'),('FA_CIP_TO_ASSET','Task_3'),('FA_CIP_TO_ASSET','Task_6'),('FA_CIP_TO_ASSET','Task_8'))
   AND ( c.assign_type <> 'ROLE'
      OR c.assign_value REGEXP '^[0-9]+$'
      OR EXISTS (SELECT 1 FROM sys_user u
                   JOIN sys_user_role ur ON ur.user_id = u.id
                   JOIN sys_role r ON r.id = ur.role_id
                  WHERE u.is_admin = 1 AND u.del_flag = 0 AND r.role_code = c.assign_value) );
