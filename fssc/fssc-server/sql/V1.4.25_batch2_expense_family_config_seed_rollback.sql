-- ============================================================================
-- 回滚：V1.4.25_batch2_expense_family_config_seed.sql
-- 效果：费用报销族三模型 45 行 wf_node_config 的 handling_mode / timeout_hours / timeout_action /
--       node_name 按备份表逐值还原；sys_config.approve.pick_mode.models 还原为备份值（期望回到 'LOAN'）。
-- 注意：本脚本**不回滚 BPMN**——三链结构重造是独立一步（见 bpmn-repo/publish/<key>/*.diff，
--       结构回滚＝把 bpmn-repo/models/<key>/107_v14 等旧 XML 再 publish 一次）。
--       若只回滚配置而保留新 BPMN，扫描节点回到 AUTO（单人兜底取首个）、超时退回 NOTIFY，链形不变。
-- 只还原被本脚本改过的列值；不删任何行、不动备份表。
-- ============================================================================

SET SESSION lock_wait_timeout = 10;
SET SESSION innodb_lock_wait_timeout = 10;
SELECT COUNT(*) AS open_trx_should_be_0 FROM information_schema.INNODB_TRX;

UPDATE wf_node_config c
  JOIN wf_node_config_bak_v1425_20260922 b ON b.id = c.id
   SET c.handling_mode = b.handling_mode,
       c.timeout_hours = b.timeout_hours,
       c.timeout_action = b.timeout_action,
       c.node_name      = b.node_name;

UPDATE sys_config s
  JOIN sys_config_bak_v1425_20260922 b ON b.id = s.id AND b.config_key = s.config_key
   SET s.config_value = b.config_value;

SELECT model_key, handling_mode, timeout_action, COUNT(*) n
  FROM wf_node_config
 WHERE del_flag = 0 AND model_key IN ('CLAIM','CLAIM_TRAVEL','SETTLE')
 GROUP BY model_key, handling_mode, timeout_action
 ORDER BY model_key;
SELECT config_key, config_value FROM sys_config WHERE config_key = 'approve.pick_mode.models';
