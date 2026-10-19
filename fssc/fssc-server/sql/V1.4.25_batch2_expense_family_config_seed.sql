-- ============================================================================
-- V1.4.25 批次 2 · 费用报销族（第一刀）配置落位：扫描作业 POOL + §5.9 超时矩阵补做 + 灰度白名单扩族
-- 依据：财务共享流程全面优化方案_20260922.md §5.1（扫描=段2 作业抢单、齐套）、§5.9 超时矩阵、
--       用户 2026-09-22「认可，开始批次 2」。
-- 顺序纪律：本脚本是"改配置"，与"改 XML"（三链 publish，已于 17:44~17:47 完成并留 diff）**分成两步**，不混在一次动作里。
-- 前置实测（2026-09-22 17:50 只读盘点）：
--   · CLAIM(107)/CLAIM_TRAVEL(106)/SETTLE(109) 三模型 wf_instance / wf_task / ACT_RU_TASK 在途均为 0 → 重发布无在途风险
--   · wf_node_config 三模型 45 行：timeout_action 全为 NOTIFY（§5.9 未落位，批次 1 遗留）、
--     handling_mode 全为 AUTO（扫描作业未落 POOL）、PICK 只登记在 9 个业务审批节点上但因灰度白名单不含本族而不生效
--   · sys_config.approve.pick_mode.enabled=1、models='LOAN'
--   · CLAIM_TRAVEL.Task_10 node_name 脏值「基地总经理 / 审核【基地总经理】」（BPMN 侧本次已改净，配置侧本脚本跟改）
-- 不做（留给裁决）：
--   · Task_2 empty_strategy 维持 ERROR（§5.9 作业口口径是 SKIP，但改成 SKIP 等于"没扫描也能往下走"，属控制让步）
--   · Task_1/Task_3/Task_5/Task_15/Task_16 等本次已从 BPMN 删除的节点，其配置行**保留不删**（引擎按 node_key 取配置，
--     节点不存在即永不命中；删行属破坏性动作，不在增量口径内）
--   · fraud.shadow_mode 不启用（演练只需 3 张样单，离反欺诈高频阈值很远，保持生产语义真实）
-- 回滚：V1.4.25_batch2_expense_family_config_seed_rollback.sql
-- ============================================================================

SET SESSION lock_wait_timeout = 10;
SET SESSION innodb_lock_wait_timeout = 10;
SELECT COUNT(*) AS open_trx_should_be_0 FROM information_schema.INNODB_TRX;

-- ---------- 0. 写前备份（只备份将被改动的对象） ----------
CREATE TABLE IF NOT EXISTS wf_node_config_bak_v1425_20260922 AS
  SELECT id, tenant_id, model_key, node_key, node_name, assign_type, assign_value,
         handling_mode, timeout_hours, timeout_action, empty_strategy
    FROM wf_node_config
   WHERE del_flag = 0 AND model_key IN ('CLAIM','CLAIM_TRAVEL','SETTLE');
CREATE TABLE IF NOT EXISTS sys_config_bak_v1425_20260922 AS
  SELECT * FROM sys_config WHERE config_key = 'approve.pick_mode.models';

-- ---------- 1. §5.1 扫描作业改抢单（POOL），成为影像齐套 ----------
UPDATE wf_node_config
   SET handling_mode = 'POOL'
 WHERE del_flag = 0 AND model_key IN ('CLAIM','CLAIM_TRAVEL','SETTLE')
   AND node_key = 'Task_2' AND assign_value = 'SCAN_STAFF';

-- ---------- 2. §5.9 超时矩阵落位（本族只到 AUTO_REMIND，一律不自动通过、不自动改派） ----------
-- 2a 授权层级（基地财务经理→基地总经理→板块财务经理→财务总监→分管领导→总裁）：48h 提醒
UPDATE wf_node_config
   SET timeout_hours = 48, timeout_action = 'AUTO_REMIND'
 WHERE del_flag = 0 AND model_key IN ('CLAIM','CLAIM_TRAVEL','SETTLE')
   AND assign_type = 'ROLE'
   AND assign_value IN ('BASE_FIN_MANAGER','BASE_GM','PLATE_FIN_MANAGER','CFO','PLATE_DIV_LEADER','PRESIDENT');
-- 2b 业务审批（直线上级/部门经理/项目经理/归口部门）：24h 提醒
UPDATE wf_node_config
   SET timeout_hours = 24, timeout_action = 'AUTO_REMIND'
 WHERE del_flag = 0 AND model_key IN ('CLAIM','CLAIM_TRAVEL','SETTLE')
   AND assign_type = 'ROLE'
   AND assign_value IN ('DIRECT_SUPERVISOR','DEPT_MANAGER','PROJECT_MANAGER');
-- 2c 共享审单（合规审核）：8h 提醒
UPDATE wf_node_config
   SET timeout_hours = 8, timeout_action = 'AUTO_REMIND'
 WHERE del_flag = 0 AND model_key IN ('CLAIM','CLAIM_TRAVEL','SETTLE')
   AND node_key = 'Task_7' AND assign_value = 'FSSC_AUDITOR';
-- 2d 扫描作业：8h 只提醒
UPDATE wf_node_config SET timeout_hours = 8,  timeout_action = 'AUTO_REMIND'
 WHERE del_flag = 0 AND model_key IN ('CLAIM','CLAIM_TRAVEL','SETTLE')
   AND node_key = 'Task_2' AND assign_value = 'SCAN_STAFF';
-- 2e 预制凭证复核：24h 只提醒。
-- 教训（本次写后复核抓到）：**不能按 node_key 定位**——三链的 node_key 序号并不同构，
--   CLAIM/SETTLE 的 Task_14 是"审核预制凭证"，而 CLAIM_TRAVEL 的 Task_14 是"总裁审批"（Task_15 才是预制凭证）。
--   按 node_key 写会把总裁档从 48h 压成 24h。改按节点名定位，对三链同义节点一次命中。
UPDATE wf_node_config SET timeout_hours = 24, timeout_action = 'AUTO_REMIND'
 WHERE del_flag = 0 AND model_key IN ('CLAIM','CLAIM_TRAVEL','SETTLE')
   AND node_name LIKE '审核预制凭证%';

-- ---------- 3. 脏节点名跟改（BPMN 与配置两处显示真值一致） ----------
UPDATE wf_node_config SET node_name = '基地总经理审核【基地总经理】'
 WHERE del_flag = 0 AND model_key = 'CLAIM_TRAVEL' AND node_key = 'Task_10';

-- ---------- 4. 灰度白名单扩到本族（POOL/PICK 三档才会真正生效） ----------
-- 注意：扩族同时会激活本族已登记为 PICK 的 9 个业务审批节点（提单人点名办理人）。
UPDATE sys_config
   SET config_value = 'LOAN,CLAIM,CLAIM_TRAVEL,SETTLE', update_time = NOW()
 WHERE tenant_id = 1 AND config_key = 'approve.pick_mode.models';

-- ---------- 5. 写后复核 ----------
SELECT model_key, node_key, node_name, assign_value, handling_mode, timeout_hours, timeout_action, empty_strategy
  FROM wf_node_config
 WHERE del_flag = 0 AND model_key IN ('CLAIM','CLAIM_TRAVEL','SETTLE')
 ORDER BY model_key, CAST(SUBSTRING(node_key,6) AS UNSIGNED);
SELECT config_key, config_value FROM sys_config
 WHERE config_key IN ('approve.pick_mode.enabled','approve.pick_mode.models');
-- 全库口径确认：不应出现 POOL 以外的 handling_mode 变化，也不应有任何节点 timeout_action='AUTO_PASS'
SELECT timeout_action, COUNT(*) FROM wf_node_config
 WHERE del_flag = 0 AND model_key IN ('CLAIM','CLAIM_TRAVEL','SETTLE') GROUP BY timeout_action;
