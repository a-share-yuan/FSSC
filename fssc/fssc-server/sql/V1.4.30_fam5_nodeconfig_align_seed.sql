-- V1.4.30  批次2 第五族（存货 INV_* + FA11-01/FA11-04）配置侧对齐：wf_node_config <- 已发布 BPMN 唯一真值
-- 前置：11 个模型已 /api/tenant/wf/designer/publish 重发布（FA11-01 / FA11-04 / INV_DEVAL / INV_DISPOSAL_APPLY / INV_DISPOSAL_CHECK / INV_DISPOSAL_EXEC / INV_DISPOSE / INV_INTERNAL_SALE / INV_ISSUE_NONPROJECT / INV_ISSUE_PROJECT / INV_PHYSICAL_CHECK），
--   发布前回读复核 11/11 与 draft 逐字节一致（e2e-ui/batch2/fam5-verify.log）、在途实例双口径均为 0
--   （designer/in-flight runningTotal=0 且 ACT_RU_EXECUTION/ACT_RU_TASK 按 KEY_ 前缀查得 0）。
-- 结构：本族 11 模型 79 个人工节点 -> 42 个（-37，-47%）；
--   作业池 5 个（handling_mode=POOL）、排他网关 8 个（金额 DOA 阶梯，每档显式默认流）、并联会审 3 组
--   （INV_DISPOSAL_APPLY 仓库+归口+基地财务 3 出、INV_DISPOSAL_EXEC 同 3 出、INV_PHYSICAL_CHECK 仓库录入+稽核 2 出）。
-- 相邻同角色：重造前 19 对（WH_KEEPER×4 出现在 4 个模型、FIN_AUDIT×3、FIN_GL×3）-> 重造后 0 对，
--   且【同一模型内同一角色只出现一次】（自检断言）。
-- 已出链旧节点的配置行按批次2口径【保留不删】（DB 增量原则）：引擎按 BPMN 节点反查配置，幽灵行不产生运行时影响，仅在复核 C 盘点计数。
-- 本文件做三件事：1) 38 个已存在节点对齐 node_name/assign_type/assign_value/handling_mode/empty_strategy/超时；
--   2) 4 个新 key 插行；3) 复核 A~E。
-- 改岗清单（BPMN candidateGroups 与旧 assign_value 不同者，共 12 处）：
--   FA11-01.Task_2 FA_ASSET_ADMIN->FSSC_AUDITOR、FA11-04.Task_2 FA_ASSET_ADMIN->FSSC_AUDITOR、INV_DISPOSAL_APPLY.Task_6 FIN_AUDIT->DEPT_MANAGER、INV_DISPOSAL_APPLY.Task_8 FIN_AUDIT->BASE_FIN_MANAGER、INV_DISPOSAL_EXEC.Task_4 WH_KEEPER->DEPT_MANAGER、INV_DISPOSAL_EXEC.Task_5 WH_KEEPER->BASE_FIN_MANAGER、INV_DISPOSAL_EXEC.Task_8 FIN_STAFF->CFO、INV_DISPOSE.Task_2 DEPT_MANAGER->SCAN_STAFF、INV_DISPOSE.Task_10 FA_ASSET_ACCOUNT->FIN_GL、INV_INTERNAL_SALE.Task_6 NULL->SCAN_STAFF、INV_ISSUE_PROJECT.Task_3 FIN_AP->FIN_GL、INV_PHYSICAL_CHECK.Task_6 FA_ASSET_ADMIN->WH_KEEPER
-- 档位口径（方案 §5.9）：授权层级(BASE_GM/GM/CFO/PRESIDENT/板块分管领导) 48h AUTO_REMIND 且永不 AUTO_PASS；
--   集体决策 BOARD_OFFICE 168h NOTIFY；共享审单(FSSC_AUDITOR/FIN_AUDIT/SCAN_STAFF) 8h AUTO_REMIND；
--   业务审批 24h AUTO_REMIND；作业池 48h AUTO_REMIND。empty_strategy 全族 ESCALATE。
-- allow_transfer / multi_type / auto_pass_self / form_perm 一律不动（沿用批次1 定档）。
-- 不删任何数据；回滚见 V1.4.30_fam5_nodeconfig_align_rollback.sql
-- 执行：mysql -uroot -p123456 -h127.0.0.1 -P3306 -Dfssc --default-character-set=utf8mb4 < 本文件

-- 0) 备份将被改动的行（幂等：表已存在则跳过，备份表不删）
CREATE TABLE IF NOT EXISTS wf_node_config_bak_V1430_20260922 AS
SELECT * FROM wf_node_config WHERE (model_key, node_key) IN (('FA11-01','Task_2'),('FA11-01','Task_3'),('FA11-04','Task_2'),('FA11-04','Task_3'),('INV_DEVAL','Task_2'),('INV_DEVAL','Task_3'),('INV_DEVAL','Task_4'),('INV_DEVAL','Task_5'),('INV_DISPOSAL_APPLY','Task_2'),('INV_DISPOSAL_APPLY','Task_6'),('INV_DISPOSAL_APPLY','Task_8'),('INV_DISPOSAL_APPLY','Task_7'),('INV_DISPOSAL_CHECK','Task_1'),('INV_DISPOSAL_CHECK','Task_3'),('INV_DISPOSAL_EXEC','Task_2'),('INV_DISPOSAL_EXEC','Task_3'),('INV_DISPOSAL_EXEC','Task_4'),('INV_DISPOSAL_EXEC','Task_5'),('INV_DISPOSAL_EXEC','Task_7'),('INV_DISPOSAL_EXEC','Task_8'),('INV_DISPOSE','Task_2'),('INV_DISPOSE','Task_3'),('INV_DISPOSE','Task_4'),('INV_DISPOSE','Task_5'),('INV_DISPOSE','Task_8'),('INV_DISPOSE','Task_6'),('INV_DISPOSE','Task_9'),('INV_DISPOSE','Task_10'),('INV_INTERNAL_SALE','Task_6'),('INV_INTERNAL_SALE','Task_13'),('INV_INTERNAL_SALE','Task_10'),('INV_INTERNAL_SALE','Task_11'),('INV_ISSUE_NONPROJECT','Task_2'),('INV_ISSUE_NONPROJECT','Task_7'),('INV_ISSUE_PROJECT','Task_2'),('INV_ISSUE_PROJECT','Task_3'),('INV_PHYSICAL_CHECK','Task_2'),('INV_PHYSICAL_CHECK','Task_3'),('INV_PHYSICAL_CHECK','Task_4'),('INV_PHYSICAL_CHECK','Task_5'),('INV_PHYSICAL_CHECK','Task_6'),('INV_PHYSICAL_CHECK','Task_7'));

-- 0b) 新 key 插行前体检（期望 4 行，与下方 INSERT 一一对应）
SELECT k.model_key, k.node_key FROM (
  SELECT 'FA11-01' model_key, 'Task_3' node_key
  UNION ALL
  SELECT 'FA11-04' model_key, 'Task_3' node_key
  UNION ALL
  SELECT 'INV_DEVAL' model_key, 'Task_5' node_key
  UNION ALL
  SELECT 'INV_INTERNAL_SALE' model_key, 'Task_13' node_key
) k LEFT JOIN wf_node_config c ON c.model_key = k.model_key AND c.node_key = k.node_key AND c.del_flag = 0
 WHERE c.node_key IS NULL;

-- 1) 逐节点对齐（38 行）
-- FA11-01.Task_2  共享会计审核领用出库与成本归属【共享会计】
UPDATE wf_node_config
   SET node_name      = '共享会计审核领用出库与成本归属【共享会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_AUDITOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA11-01' AND node_key = 'Task_2';

-- FA11-04.Task_2  共享会计审核存货处置方案【共享会计】
UPDATE wf_node_config
   SET node_name      = '共享会计审核存货处置方案【共享会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_AUDITOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FA11-04' AND node_key = 'Task_2';

-- INV_DEVAL.Task_2  审核跌价准备测算与可变现净值【共享会计】
UPDATE wf_node_config
   SET node_name      = '审核跌价准备测算与可变现净值【共享会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_AUDITOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DEVAL' AND node_key = 'Task_2';

-- INV_DEVAL.Task_3  稽核岗复核跌价计提口径【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '稽核岗复核跌价计提口径【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DEVAL' AND node_key = 'Task_3';

-- INV_DEVAL.Task_4  基地财务经理审批计提【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审批计提【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DEVAL' AND node_key = 'Task_4';

-- INV_DISPOSAL_APPLY.Task_2  仓库确认物资状态与处置建议【仓库管理员】
UPDATE wf_node_config
   SET node_name      = '仓库确认物资状态与处置建议【仓库管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'WH_KEEPER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSAL_APPLY' AND node_key = 'Task_2';

-- INV_DISPOSAL_APPLY.Task_6  归口部门审核处置必要性【部门经理】
UPDATE wf_node_config
   SET node_name      = '归口部门审核处置必要性【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSAL_APPLY' AND node_key = 'Task_6';

-- INV_DISPOSAL_APPLY.Task_8  基地财务经理审核处置损益【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核处置损益【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSAL_APPLY' AND node_key = 'Task_8';

-- INV_DISPOSAL_APPLY.Task_7  财务审核岗审单并确认可折价销售或厂家回收【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '财务审核岗审单并确认可折价销售或厂家回收【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSAL_APPLY' AND node_key = 'Task_7';

-- INV_DISPOSAL_CHECK.Task_1  仓库确认物资状态与可用性【仓库管理员】
UPDATE wf_node_config
   SET node_name      = '仓库确认物资状态与可用性【仓库管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'WH_KEEPER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSAL_CHECK' AND node_key = 'Task_1';

-- INV_DISPOSAL_CHECK.Task_3  财务审核岗审核处置明细表【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '财务审核岗审核处置明细表【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSAL_CHECK' AND node_key = 'Task_3';

-- INV_DISPOSAL_EXEC.Task_2  处置协议、收款与发票影像齐套（作业池）【财务扫描岗】
UPDATE wf_node_config
   SET node_name      = '处置协议、收款与发票影像齐套（作业池）【财务扫描岗】',
       assign_type    = 'ROLE',
       assign_value   = 'SCAN_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSAL_EXEC' AND node_key = 'Task_2';

-- INV_DISPOSAL_EXEC.Task_3  仓库确认处置物资已出库【仓库管理员】
UPDATE wf_node_config
   SET node_name      = '仓库确认处置物资已出库【仓库管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'WH_KEEPER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSAL_EXEC' AND node_key = 'Task_3';

-- INV_DISPOSAL_EXEC.Task_4  归口部门审核处置方式合规【部门经理】
UPDATE wf_node_config
   SET node_name      = '归口部门审核处置方式合规【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSAL_EXEC' AND node_key = 'Task_4';

-- INV_DISPOSAL_EXEC.Task_5  基地财务经理审核处置收入与损益【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核处置收入与损益【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSAL_EXEC' AND node_key = 'Task_5';

-- INV_DISPOSAL_EXEC.Task_7  财务审核岗审单（协议·收款·开票齐套）【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '财务审核岗审单（协议·收款·开票齐套）【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSAL_EXEC' AND node_key = 'Task_7';

-- INV_DISPOSAL_EXEC.Task_8  财务总监审批处置（超 50 万）【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批处置（超 50 万）【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSAL_EXEC' AND node_key = 'Task_8';

-- INV_DISPOSE.Task_2  报废技术鉴定单与影像齐套（作业池）【财务扫描岗】
UPDATE wf_node_config
   SET node_name      = '报废技术鉴定单与影像齐套（作业池）【财务扫描岗】',
       assign_type    = 'ROLE',
       assign_value   = 'SCAN_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSE' AND node_key = 'Task_2';

-- INV_DISPOSE.Task_3  业务部门经理审批报废【部门经理】
UPDATE wf_node_config
   SET node_name      = '业务部门经理审批报废【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSE' AND node_key = 'Task_3';

-- INV_DISPOSE.Task_4  基地财务经理审核报废损失【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审核报废损失【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSE' AND node_key = 'Task_4';

-- INV_DISPOSE.Task_5  基地总经理审批报废（超 20 万）【基地总经理】
UPDATE wf_node_config
   SET node_name      = '基地总经理审批报废（超 20 万）【基地总经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSE' AND node_key = 'Task_5';

-- INV_DISPOSE.Task_8  财务总监审批报废（超 50 万）【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批报废（超 50 万）【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSE' AND node_key = 'Task_8';

-- INV_DISPOSE.Task_6  板块分管领导审批报废（超 200 万）【板块分管领导】
UPDATE wf_node_config
   SET node_name      = '板块分管领导审批报废（超 200 万）【板块分管领导】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_DIV_LEADER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSE' AND node_key = 'Task_6';

-- INV_DISPOSE.Task_9  董办/董会上会决议（超 500 万）【董办/总裁办公会】
UPDATE wf_node_config
   SET node_name      = '董办/董会上会决议（超 500 万）【董办/总裁办公会】',
       assign_type    = 'ROLE',
       assign_value   = 'BOARD_OFFICE',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 168,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSE' AND node_key = 'Task_9';

-- INV_DISPOSE.Task_10  报废存货核销与制证（作业池）【总账会计】
UPDATE wf_node_config
   SET node_name      = '报废存货核销与制证（作业池）【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_DISPOSE' AND node_key = 'Task_10';

-- INV_INTERNAL_SALE.Task_6  内部销售开票与发票影像齐套（作业池）【财务扫描岗】
UPDATE wf_node_config
   SET node_name      = '内部销售开票与发票影像齐套（作业池）【财务扫描岗】',
       assign_type    = 'ROLE',
       assign_value   = 'SCAN_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_INTERNAL_SALE' AND node_key = 'Task_6';

-- INV_INTERNAL_SALE.Task_10  财务审核岗审核内部销售单据【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '财务审核岗审核内部销售单据【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_INTERNAL_SALE' AND node_key = 'Task_10';

-- INV_INTERNAL_SALE.Task_11  应付会计执行采购发票校验【应付会计】
UPDATE wf_node_config
   SET node_name      = '应付会计执行采购发票校验【应付会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AP',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_INTERNAL_SALE' AND node_key = 'Task_11';

-- INV_ISSUE_NONPROJECT.Task_2  仓库审核领料申请并确认库存可用【仓库管理员】
UPDATE wf_node_config
   SET node_name      = '仓库审核领料申请并确认库存可用【仓库管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'WH_KEEPER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_ISSUE_NONPROJECT' AND node_key = 'Task_2';

-- INV_ISSUE_NONPROJECT.Task_7  总账会计指定成本科目并复核领用归集【总账会计】
UPDATE wf_node_config
   SET node_name      = '总账会计指定成本科目并复核领用归集【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_ISSUE_NONPROJECT' AND node_key = 'Task_7';

-- INV_ISSUE_PROJECT.Task_2  仓库下定额并确认领用出库【仓库管理员】
UPDATE wf_node_config
   SET node_name      = '仓库下定额并确认领用出库【仓库管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'WH_KEEPER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_ISSUE_PROJECT' AND node_key = 'Task_2';

-- INV_ISSUE_PROJECT.Task_3  总账会计复核项目成本归集与科目【总账会计】
UPDATE wf_node_config
   SET node_name      = '总账会计复核项目成本归集与科目【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_ISSUE_PROJECT' AND node_key = 'Task_3';

-- INV_PHYSICAL_CHECK.Task_2  业务部门经理审批盘点差异明细表【部门经理】
UPDATE wf_node_config
   SET node_name      = '业务部门经理审批盘点差异明细表【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_PHYSICAL_CHECK' AND node_key = 'Task_2';

-- INV_PHYSICAL_CHECK.Task_3  基地财务经理审批盘点差异【基地财务经理】
UPDATE wf_node_config
   SET node_name      = '基地财务经理审批盘点差异【基地财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_PHYSICAL_CHECK' AND node_key = 'Task_3';

-- INV_PHYSICAL_CHECK.Task_4  基地总经理审批差异（超 20 万）【基地总经理】
UPDATE wf_node_config
   SET node_name      = '基地总经理审批差异（超 20 万）【基地总经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_PHYSICAL_CHECK' AND node_key = 'Task_4';

-- INV_PHYSICAL_CHECK.Task_5  财务总监审批差异（超 50 万）【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批差异（超 50 万）【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_PHYSICAL_CHECK' AND node_key = 'Task_5';

-- INV_PHYSICAL_CHECK.Task_6  录入盘点差异数据并核对账存（作业池）【仓库管理员】
UPDATE wf_node_config
   SET node_name      = '录入盘点差异数据并核对账存（作业池）【仓库管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'WH_KEEPER',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_PHYSICAL_CHECK' AND node_key = 'Task_6';

-- INV_PHYSICAL_CHECK.Task_7  稽核岗审核确认差异数据【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '稽核岗审核确认差异数据【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'INV_PHYSICAL_CHECK' AND node_key = 'Task_7';

-- 2) 新 key 插行（4 行，NOT EXISTS 守卫保证幂等）
-- FA11-01.Task_3  财务审核岗复核领用科目与结转【财务审核岗】
INSERT INTO wf_node_config
  (tenant_id, model_key, node_key, node_name, assign_type, assign_value, handling_mode, up_level, multi_type,
   vote_ratio, allow_reject, reject_to, allow_transfer, allow_addsign, allow_edit, empty_strategy, auto_pass_self,
   timeout_hours, timeout_action, sort, create_time, update_time, del_flag)
SELECT 1, 'FA11-01', 'Task_3', '财务审核岗复核领用科目与结转【财务审核岗】', 'ROLE', 'FIN_AUDIT', 'AUTO', 1, 'ANY',
       50, 1, 'INITIATOR', 1, 0, 0, 'ESCALATE', 0, 8, 'AUTO_REMIND', 100, NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM wf_node_config WHERE model_key = 'FA11-01' AND node_key = 'Task_3' AND del_flag = 0);

-- FA11-04.Task_3  财务审核岗复核处置损益【财务审核岗】
INSERT INTO wf_node_config
  (tenant_id, model_key, node_key, node_name, assign_type, assign_value, handling_mode, up_level, multi_type,
   vote_ratio, allow_reject, reject_to, allow_transfer, allow_addsign, allow_edit, empty_strategy, auto_pass_self,
   timeout_hours, timeout_action, sort, create_time, update_time, del_flag)
SELECT 1, 'FA11-04', 'Task_3', '财务审核岗复核处置损益【财务审核岗】', 'ROLE', 'FIN_AUDIT', 'AUTO', 1, 'ANY',
       50, 1, 'INITIATOR', 1, 0, 0, 'ESCALATE', 0, 8, 'AUTO_REMIND', 101, NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM wf_node_config WHERE model_key = 'FA11-04' AND node_key = 'Task_3' AND del_flag = 0);

-- INV_DEVAL.Task_5  财务总监审批跌价准备（超 50 万）【财务总监】
INSERT INTO wf_node_config
  (tenant_id, model_key, node_key, node_name, assign_type, assign_value, handling_mode, up_level, multi_type,
   vote_ratio, allow_reject, reject_to, allow_transfer, allow_addsign, allow_edit, empty_strategy, auto_pass_self,
   timeout_hours, timeout_action, sort, create_time, update_time, del_flag)
SELECT 1, 'INV_DEVAL', 'Task_5', '财务总监审批跌价准备（超 50 万）【财务总监】', 'ROLE', 'CFO', 'AUTO', 1, 'ANY',
       50, 1, 'INITIATOR', 1, 0, 0, 'ESCALATE', 0, 48, 'AUTO_REMIND', 102, NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM wf_node_config WHERE model_key = 'INV_DEVAL' AND node_key = 'Task_5' AND del_flag = 0);

-- INV_INTERNAL_SALE.Task_13  基地财务经理审核转移定价与内部毛利【基地财务经理】
INSERT INTO wf_node_config
  (tenant_id, model_key, node_key, node_name, assign_type, assign_value, handling_mode, up_level, multi_type,
   vote_ratio, allow_reject, reject_to, allow_transfer, allow_addsign, allow_edit, empty_strategy, auto_pass_self,
   timeout_hours, timeout_action, sort, create_time, update_time, del_flag)
SELECT 1, 'INV_INTERNAL_SALE', 'Task_13', '基地财务经理审核转移定价与内部毛利【基地财务经理】', 'ROLE', 'BASE_FIN_MANAGER', 'AUTO', 1, 'ANY',
       50, 1, 'INITIATOR', 1, 0, 0, 'ESCALATE', 0, 24, 'AUTO_REMIND', 103, NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM wf_node_config WHERE model_key = 'INV_INTERNAL_SALE' AND node_key = 'Task_13' AND del_flag = 0);

-- 3) 复核 A：逐节点期望值对账（期望 0 行；输出任一行即为 BPMN 与 config 两处真值漂移）
SELECT e.model_key, e.node_key, e.exp_role AS bpmn_groups, c.assign_value AS config_role,
       e.exp_name AS bpmn_name, c.node_name AS config_name, e.exp_es AS exp_empty, IFNULL(c.empty_strategy,'') got_empty,
       e.exp_th AS exp_th, IFNULL(c.timeout_hours,-1) got_th, e.exp_ta exp_ta, IFNULL(c.timeout_action,'') got_ta,
       e.exp_hm exp_hm, IFNULL(c.handling_mode,'') got_hm
  FROM (
    SELECT 'FA11-01' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FSSC_AUDITOR' exp_role, '共享会计审核领用出库与成本归属【共享会计】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA11-01' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗复核领用科目与结转【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA11-04' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FSSC_AUDITOR' exp_role, '共享会计审核存货处置方案【共享会计】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FA11-04' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗复核处置损益【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DEVAL' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FSSC_AUDITOR' exp_role, '审核跌价准备测算与可变现净值【共享会计】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DEVAL' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '稽核岗复核跌价计提口径【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DEVAL' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审批计提【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DEVAL' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批跌价准备（超 50 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSAL_APPLY' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'WH_KEEPER' exp_role, '仓库确认物资状态与处置建议【仓库管理员】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSAL_APPLY' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '归口部门审核处置必要性【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSAL_APPLY' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核处置损益【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSAL_APPLY' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗审单并确认可折价销售或厂家回收【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSAL_CHECK' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'WH_KEEPER' exp_role, '仓库确认物资状态与可用性【仓库管理员】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSAL_CHECK' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗审核处置明细表【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSAL_EXEC' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'SCAN_STAFF' exp_role, '处置协议、收款与发票影像齐套（作业池）【财务扫描岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSAL_EXEC' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'WH_KEEPER' exp_role, '仓库确认处置物资已出库【仓库管理员】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSAL_EXEC' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '归口部门审核处置方式合规【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSAL_EXEC' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核处置收入与损益【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSAL_EXEC' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗审单（协议·收款·开票齐套）【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSAL_EXEC' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批处置（超 50 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSE' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'SCAN_STAFF' exp_role, '报废技术鉴定单与影像齐套（作业池）【财务扫描岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSE' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '业务部门经理审批报废【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSE' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核报废损失【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSE' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'BASE_GM' exp_role, '基地总经理审批报废（超 20 万）【基地总经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSE' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批报废（超 50 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSE' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'PLATE_DIV_LEADER' exp_role, '板块分管领导审批报废（超 200 万）【板块分管领导】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSE' model_key, 'Task_9' node_key, 'ROLE' exp_type, 'BOARD_OFFICE' exp_role, '董办/董会上会决议（超 500 万）【董办/总裁办公会】' exp_name, 'ESCALATE' exp_es, 168 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_DISPOSE' model_key, 'Task_10' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '报废存货核销与制证（作业池）【总账会计】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'INV_INTERNAL_SALE' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'SCAN_STAFF' exp_role, '内部销售开票与发票影像齐套（作业池）【财务扫描岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'INV_INTERNAL_SALE' model_key, 'Task_13' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审核转移定价与内部毛利【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_INTERNAL_SALE' model_key, 'Task_10' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗审核内部销售单据【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_INTERNAL_SALE' model_key, 'Task_11' node_key, 'ROLE' exp_type, 'FIN_AP' exp_role, '应付会计执行采购发票校验【应付会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_ISSUE_NONPROJECT' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'WH_KEEPER' exp_role, '仓库审核领料申请并确认库存可用【仓库管理员】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_ISSUE_NONPROJECT' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '总账会计指定成本科目并复核领用归集【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_ISSUE_PROJECT' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'WH_KEEPER' exp_role, '仓库下定额并确认领用出库【仓库管理员】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_ISSUE_PROJECT' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '总账会计复核项目成本归集与科目【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_PHYSICAL_CHECK' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '业务部门经理审批盘点差异明细表【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_PHYSICAL_CHECK' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'BASE_FIN_MANAGER' exp_role, '基地财务经理审批盘点差异【基地财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_PHYSICAL_CHECK' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'BASE_GM' exp_role, '基地总经理审批差异（超 20 万）【基地总经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_PHYSICAL_CHECK' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批差异（超 50 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'INV_PHYSICAL_CHECK' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'WH_KEEPER' exp_role, '录入盘点差异数据并核对账存（作业池）【仓库管理员】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'INV_PHYSICAL_CHECK' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '稽核岗审核确认差异数据【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
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

-- 4) 复核 B：本族 12 个角色的存在性与 co1 在编持岗人数（口径 status='NORMAL' 且 is_admin=0；
--    COMPANY 范围角色必须问"该公司有没有在编持有人"。active_co1=0 即为 §5.4 补岗清单候选，不是脚本缺陷。
--     WH_KEEPER：本族挂载点已由 20 降到 6（APPLY/CHECK/EXEC/PHYSICAL_CHECK + 两条领用链），但该角色全租户仍仅 1 人
--      → 这 6 个节点在任何一张单据上都只能靠 B 角补岗或 ESCALATE 沿汇报线兜底，属 §5.4 四选一未裁决项）
SELECT r.role_code, ANY_VALUE(r.role_name) role_name, ANY_VALUE(r.org_scope) org_scope, ANY_VALUE(r.status) status,
       COUNT(DISTINCT ur.user_id) holders_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 THEN 1 ELSE 0 END) active_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 AND u.company_id = 1 THEN 1 ELSE 0 END) active_co1
  FROM sys_role r
  LEFT JOIN sys_user_role ur ON ur.role_id = r.id
  LEFT JOIN sys_user u ON u.id = ur.user_id AND u.del_flag = 0
 WHERE r.role_code IN ('BASE_FIN_MANAGER','BASE_GM','BOARD_OFFICE','CFO','DEPT_MANAGER','FIN_AP','FIN_AUDIT','FIN_GL','FSSC_AUDITOR','PLATE_DIV_LEADER','SCAN_STAFF','WH_KEEPER') AND r.del_flag = 0
 GROUP BY r.role_code ORDER BY active_co1, r.role_code;

-- 5) 复核 C：配置行盘点 = 新 BPMN 有效节点(42) + 出链后的幽灵行（仅盘点，不改；期望 rows_in_new_bpmn 合计 42）
SELECT model_key, COUNT(*) rows_total,
       SUM((model_key, node_key) IN (('FA11-01','Task_2'),('FA11-01','Task_3'),('FA11-04','Task_2'),('FA11-04','Task_3'),('INV_DEVAL','Task_2'),('INV_DEVAL','Task_3'),('INV_DEVAL','Task_4'),('INV_DEVAL','Task_5'),('INV_DISPOSAL_APPLY','Task_2'),('INV_DISPOSAL_APPLY','Task_6'),('INV_DISPOSAL_APPLY','Task_8'),('INV_DISPOSAL_APPLY','Task_7'),('INV_DISPOSAL_CHECK','Task_1'),('INV_DISPOSAL_CHECK','Task_3'),('INV_DISPOSAL_EXEC','Task_2'),('INV_DISPOSAL_EXEC','Task_3'),('INV_DISPOSAL_EXEC','Task_4'),('INV_DISPOSAL_EXEC','Task_5'),('INV_DISPOSAL_EXEC','Task_7'),('INV_DISPOSAL_EXEC','Task_8'),('INV_DISPOSE','Task_2'),('INV_DISPOSE','Task_3'),('INV_DISPOSE','Task_4'),('INV_DISPOSE','Task_5'),('INV_DISPOSE','Task_8'),('INV_DISPOSE','Task_6'),('INV_DISPOSE','Task_9'),('INV_DISPOSE','Task_10'),('INV_INTERNAL_SALE','Task_6'),('INV_INTERNAL_SALE','Task_13'),('INV_INTERNAL_SALE','Task_10'),('INV_INTERNAL_SALE','Task_11'),('INV_ISSUE_NONPROJECT','Task_2'),('INV_ISSUE_NONPROJECT','Task_7'),('INV_ISSUE_PROJECT','Task_2'),('INV_ISSUE_PROJECT','Task_3'),('INV_PHYSICAL_CHECK','Task_2'),('INV_PHYSICAL_CHECK','Task_3'),('INV_PHYSICAL_CHECK','Task_4'),('INV_PHYSICAL_CHECK','Task_5'),('INV_PHYSICAL_CHECK','Task_6'),('INV_PHYSICAL_CHECK','Task_7'))) rows_in_new_bpmn
  FROM wf_node_config
 WHERE model_key IN ('FA11-01','FA11-04','INV_DEVAL','INV_DISPOSAL_APPLY','INV_DISPOSAL_CHECK','INV_DISPOSAL_EXEC','INV_DISPOSE','INV_INTERNAL_SALE','INV_ISSUE_NONPROJECT','INV_ISSUE_PROJECT','INV_PHYSICAL_CHECK') AND del_flag = 0
 GROUP BY model_key ORDER BY model_key;

-- 6) 复核 D：作业池档位落地（期望 5 行 POOL）
SELECT model_key, node_key, handling_mode, assign_value, node_name FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('FA11-01','Task_2'),('FA11-01','Task_3'),('FA11-04','Task_2'),('FA11-04','Task_3'),('INV_DEVAL','Task_2'),('INV_DEVAL','Task_3'),('INV_DEVAL','Task_4'),('INV_DEVAL','Task_5'),('INV_DISPOSAL_APPLY','Task_2'),('INV_DISPOSAL_APPLY','Task_6'),('INV_DISPOSAL_APPLY','Task_8'),('INV_DISPOSAL_APPLY','Task_7'),('INV_DISPOSAL_CHECK','Task_1'),('INV_DISPOSAL_CHECK','Task_3'),('INV_DISPOSAL_EXEC','Task_2'),('INV_DISPOSAL_EXEC','Task_3'),('INV_DISPOSAL_EXEC','Task_4'),('INV_DISPOSAL_EXEC','Task_5'),('INV_DISPOSAL_EXEC','Task_7'),('INV_DISPOSAL_EXEC','Task_8'),('INV_DISPOSE','Task_2'),('INV_DISPOSE','Task_3'),('INV_DISPOSE','Task_4'),('INV_DISPOSE','Task_5'),('INV_DISPOSE','Task_8'),('INV_DISPOSE','Task_6'),('INV_DISPOSE','Task_9'),('INV_DISPOSE','Task_10'),('INV_INTERNAL_SALE','Task_6'),('INV_INTERNAL_SALE','Task_13'),('INV_INTERNAL_SALE','Task_10'),('INV_INTERNAL_SALE','Task_11'),('INV_ISSUE_NONPROJECT','Task_2'),('INV_ISSUE_NONPROJECT','Task_7'),('INV_ISSUE_PROJECT','Task_2'),('INV_ISSUE_PROJECT','Task_3'),('INV_PHYSICAL_CHECK','Task_2'),('INV_PHYSICAL_CHECK','Task_3'),('INV_PHYSICAL_CHECK','Task_4'),('INV_PHYSICAL_CHECK','Task_5'),('INV_PHYSICAL_CHECK','Task_6'),('INV_PHYSICAL_CHECK','Task_7')) AND handling_mode = 'POOL'
 ORDER BY model_key, node_key;

-- 7) 复核 E：零 admin 硬门槛 —— 本族任一节点都不得由 admin 参与；点名残留（assign_value 是纯数字 userId）也算违规
SELECT c.model_key, c.node_key, c.assign_type, c.assign_value
  FROM wf_node_config c
 WHERE c.del_flag = 0 AND (c.model_key, c.node_key) IN (('FA11-01','Task_2'),('FA11-01','Task_3'),('FA11-04','Task_2'),('FA11-04','Task_3'),('INV_DEVAL','Task_2'),('INV_DEVAL','Task_3'),('INV_DEVAL','Task_4'),('INV_DEVAL','Task_5'),('INV_DISPOSAL_APPLY','Task_2'),('INV_DISPOSAL_APPLY','Task_6'),('INV_DISPOSAL_APPLY','Task_8'),('INV_DISPOSAL_APPLY','Task_7'),('INV_DISPOSAL_CHECK','Task_1'),('INV_DISPOSAL_CHECK','Task_3'),('INV_DISPOSAL_EXEC','Task_2'),('INV_DISPOSAL_EXEC','Task_3'),('INV_DISPOSAL_EXEC','Task_4'),('INV_DISPOSAL_EXEC','Task_5'),('INV_DISPOSAL_EXEC','Task_7'),('INV_DISPOSAL_EXEC','Task_8'),('INV_DISPOSE','Task_2'),('INV_DISPOSE','Task_3'),('INV_DISPOSE','Task_4'),('INV_DISPOSE','Task_5'),('INV_DISPOSE','Task_8'),('INV_DISPOSE','Task_6'),('INV_DISPOSE','Task_9'),('INV_DISPOSE','Task_10'),('INV_INTERNAL_SALE','Task_6'),('INV_INTERNAL_SALE','Task_13'),('INV_INTERNAL_SALE','Task_10'),('INV_INTERNAL_SALE','Task_11'),('INV_ISSUE_NONPROJECT','Task_2'),('INV_ISSUE_NONPROJECT','Task_7'),('INV_ISSUE_PROJECT','Task_2'),('INV_ISSUE_PROJECT','Task_3'),('INV_PHYSICAL_CHECK','Task_2'),('INV_PHYSICAL_CHECK','Task_3'),('INV_PHYSICAL_CHECK','Task_4'),('INV_PHYSICAL_CHECK','Task_5'),('INV_PHYSICAL_CHECK','Task_6'),('INV_PHYSICAL_CHECK','Task_7'))
   AND ( c.assign_type <> 'ROLE'
      OR c.assign_value REGEXP '^[0-9]+$'
      OR EXISTS (SELECT 1 FROM sys_user u
                   JOIN sys_user_role ur ON ur.user_id = u.id
                   JOIN sys_role r ON r.id = ur.role_id
                  WHERE u.is_admin = 1 AND u.del_flag = 0 AND r.role_code = c.assign_value) );

-- 8) 复核 F：出链后仍在的【点名 assignee】残留普查（本族重造把 7 个 ${approver} 节点请出链，
--    此处确认已发布 BPMN 里再无 flowable:assignee 点名；期望 0 行）
SELECT model_key, node_key, assign_type, assign_value FROM wf_node_config
 WHERE del_flag = 0 AND model_key IN ('FA11-01','FA11-04','INV_DEVAL','INV_DISPOSAL_APPLY','INV_DISPOSAL_CHECK','INV_DISPOSAL_EXEC','INV_DISPOSE','INV_INTERNAL_SALE','INV_ISSUE_NONPROJECT','INV_ISSUE_PROJECT','INV_PHYSICAL_CHECK') AND assign_type = 'USER';
