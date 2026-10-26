-- ============================================================================
-- V1.4.26 R2-01 处置 A：CLAIM 链"审核预制凭证"节点参与人与另两链对齐（FSSC_AUDITOR → FIN_GL）
-- 依据：财务共享流程全面优化方案_20260922.md §8.4-D R2-01；用户 2026-09-22「按 A 处理 R2-01，继续推进下一族」。
-- 根因（实测，非推断）：CLAIM 的 Task_7「财务共享审核单据【共享会计】」与 Task_14「审核预制凭证」
--   同为 FSSC_AUDITOR，低额档走 Gw_16 默认流时两节点**相邻**，样单 5902/5903/5904 三张均复现付程鹏(Y000185)一人连办；
--   穷举三链全分支后确认唯 CLAIM 有此对——CLAIM_TRAVEL.Task_15、SETTLE.Task_14 本就是 FIN_GL。
--   本脚本只做"节点角色对齐"，不减控制点（Task_7 保留 FSSC_AUDITOR，凭证复核改由总账会计签）。
-- 顺序纪律：改 XML 与改配置**分两步**——BPMN 侧已于 18:22 publish（CLAIM v15→v16，
--   proc_def_id=CLAIM:230:74d72de2-b66f-11f1-b3a2-d8bbc1e55a1a，发布前 wf_instance CLAIM RUNNING=0），
--   本脚本是紧随其后的配置侧跟改，两处真值必须同批，否则配置会把 XML 盖回去（WfEngineFacade.applyNodeConfig
--   的 ROLE 分支是追加候选组、从不摘除）。
-- 前置实测（2026-09-22 18:20 只读盘点）：wf_node_config CLAIM 相关行 id=1348(Task_7,FSSC_AUDITOR)、
--   id=1355(Task_14, node_name 仍为脏值「审核预制凭证【共享会计】」)。
-- 不做：Task_4→Task_5 的同人连办**不在本脚本范围**（根因是 co1 的 DIRECT_SUPERVISOR 零在职成员 →
--   ESCALATE 兜到唯一 DEPT_MANAGER，属批次 1《零人公司补岗清单》人工补岗事项，不是结构缺陷）；
--   sys_config.approve.deny_serial_same_handler 维持空值（A 落地后仍需观察，贸然开启会批量卡单）。
-- 回滚：V1.4.26_r201_claim_task14_fin_gl_rollback.sql（同表反向还原，或直接用备份表）
-- ============================================================================

SET SESSION lock_wait_timeout = 10;
SET SESSION innodb_lock_wait_timeout = 10;
SELECT COUNT(*) AS open_trx_should_be_0 FROM information_schema.INNODB_TRX;

-- 0) 备份本次唯一改动行（成对 rollback 的默认处理真值）
DROP TABLE IF EXISTS wf_node_config_bak_v1426_20260922;
CREATE TABLE wf_node_config_bak_v1426_20260922 AS
SELECT * FROM wf_node_config WHERE model_key = 'CLAIM' AND node_key = 'Task_14';
SELECT COUNT(*) AS bak_rows_should_be_1 FROM wf_node_config_bak_v1426_20260922;

-- 1) 参与人真值对齐 + 节点名跟 BPMN 一致（去重：仅当前值为 FSSC_AUDITOR 时命中）
UPDATE wf_node_config
   SET assign_value = 'FIN_GL',
       node_name    = '审核预制凭证【总账会计】',
       update_time  = NOW()
 WHERE model_key = 'CLAIM' AND node_key = 'Task_14'
   AND assign_type = 'ROLE' AND assign_value = 'FSSC_AUDITOR';
SELECT ROW_COUNT() AS updated_rows;

-- 2) 复核：三链同一职能节点规则已统一，且 CLAIM.Task_7 未被连带改动
--    （注意本库 sys_role/sys_user.status 取值为 'NORMAL'，不是 '0'；写成 '0' 会让复核假报"零命中"）
SELECT c.model_key, c.node_key, c.node_name, c.assign_value,
       (SELECT COUNT(*) FROM sys_role r WHERE r.role_code = c.assign_value AND r.status = 'NORMAL' AND r.del_flag = '0') AS role_exists,
       (SELECT COUNT(DISTINCT u.id) FROM sys_user_role ur
          JOIN sys_user u ON u.id = ur.user_id AND u.status = 'NORMAL' AND u.del_flag = '0'
          JOIN sys_role r ON r.id = ur.role_id AND r.role_code = c.assign_value) AS holders_total
  FROM wf_node_config c
 WHERE (c.model_key = 'CLAIM' AND c.node_key IN ('Task_7','Task_14'))
    OR (c.model_key = 'CLAIM_TRAVEL' AND c.node_key = 'Task_15')
    OR (c.model_key = 'SETTLE' AND c.node_key = 'Task_14')
 ORDER BY c.model_key, c.node_key;

-- 3) 复核：本脚本除该行外零连带（与备份表逐列比对，非本节点的行不参与比对）
SELECT (SELECT CONCAT(IFNULL(assign_value,'~'),'|',IFNULL(node_name,'~')) FROM wf_node_config WHERE model_key='CLAIM' AND node_key='Task_14') AS now_val,
       (SELECT CONCAT(IFNULL(assign_value,'~'),'|',IFNULL(node_name,'~')) FROM wf_node_config_bak_v1426_20260922 WHERE node_key='Task_14') AS before_val,
       (SELECT COUNT(*) FROM wf_node_config WHERE model_key='CLAIM' AND node_key='Task_7' AND assign_value='FSSC_AUDITOR') AS task7_untouched_should_be_1;
