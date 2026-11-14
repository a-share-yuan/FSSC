-- ============================================================================
-- 回滚：V1.4.24_proc_def_id_backfill.sql
-- 效果：wf_model.proc_def_id / deploy_id 按备份表 wf_model_bak_v1424_20260922 逐值还原
--       （含还原成 NULL 的那 40 余行）。备份表留档不删，由运维按保留期清理。
-- 只还原被本脚本改过的两列；不动 version / status / bpmn_xml、不删任何行。
-- ============================================================================

SET SESSION lock_wait_timeout = 10;
SET SESSION innodb_lock_wait_timeout = 10;
SELECT COUNT(*) AS open_trx_should_be_0 FROM information_schema.INNODB_TRX;

UPDATE wf_model m
  JOIN wf_model_bak_v1424_20260922 b ON b.id = m.id
   SET m.proc_def_id = b.proc_def_id,
       m.deploy_id   = b.deploy_id;

SELECT SUM(m.proc_def_id IS NULL)     AS null_ptr_restored,
       SUM(m.proc_def_id = b.proc_def_id) AS matched_backup,
       COUNT(*)                        AS total
  FROM wf_model m
  JOIN wf_model_bak_v1424_20260922 b ON b.id = m.id
 WHERE m.bpmn_xml IS NOT NULL AND m.del_flag = 0;
