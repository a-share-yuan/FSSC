-- ============================================================================
-- 回滚：V1.4.23_batch1_wf_matrix_seed.sql
-- 效果：602 行 wf_node_config 的 empty_strategy / timeout_hours 按备份表逐值还原（期望回到 ERROR 602、NULL 时限 74）；
--       本批次新登记的 5 个 sys_config 键删除（seed 时已存在、只是没被覆盖的键不删）。
-- 只还原被本脚本改过的列值与新增的行；不删 wf_node_config 任何行、不动备份表（留档，由运维按保留期清理）。
-- ============================================================================

SET SESSION lock_wait_timeout = 10;
SET SESSION innodb_lock_wait_timeout = 10;
SELECT COUNT(*) AS open_trx_should_be_0 FROM information_schema.INNODB_TRX;

-- ---------- 1. 节点配置逐值还原 ----------
UPDATE wf_node_config c
  JOIN wf_node_config_bak_v1423_20260922 b ON b.id = c.id
   SET c.empty_strategy = b.empty_strategy,
       c.timeout_hours  = b.timeout_hours;

-- ---------- 2. 删除本批次新增的参数键（seed 前已存在的键保留原样） ----------
DELETE FROM sys_config
 WHERE tenant_id = 1
   AND config_key IN ('wf.timeout.autopass.nodes','wf.timeout.autopass.max_amount',
                      'fraud.block_on_submit.enabled','fraud.shadow_mode.enabled',
                      'approve.deny_serial_same_handler')
   AND id NOT IN (SELECT id FROM sys_config_bak_v1423_20260922);

-- ---------- 3. 自检（期望：ERROR 602 / ESCALATE 0 / SKIP 0 / timeout_hours NULL 74 / 5 键均无） ----------
SELECT 'empty_strategy' chk, empty_strategy v, COUNT(*) n
  FROM wf_node_config WHERE del_flag = 0 GROUP BY empty_strategy
UNION ALL
SELECT 'timeout_hours', IFNULL(CAST(timeout_hours AS CHAR),'NULL'), COUNT(*)
  FROM wf_node_config WHERE del_flag = 0 GROUP BY timeout_hours
UNION ALL
SELECT 'v1423_keys_left', 'x', COUNT(*) FROM sys_config
 WHERE config_key IN ('wf.timeout.autopass.nodes','wf.timeout.autopass.max_amount',
                      'fraud.block_on_submit.enabled','fraud.shadow_mode.enabled',
                      'approve.deny_serial_same_handler');
SELECT COUNT(*) AS diff_vs_backup_should_be_0
  FROM wf_node_config c JOIN wf_node_config_bak_v1423_20260922 b ON b.id = c.id
 WHERE NOT (c.empty_strategy <=> b.empty_strategy) OR NOT (c.timeout_hours <=> b.timeout_hours);
