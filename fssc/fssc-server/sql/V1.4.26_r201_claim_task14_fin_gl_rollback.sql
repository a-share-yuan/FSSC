-- ============================================================================
-- V1.4.26 回滚：把 CLAIM.Task_14 的参与人/节点名还原为 FSSC_AUDITOR /「审核预制凭证【共享会计】」
-- 注意：本脚本只回滚**配置侧**。BPMN 侧（CLAIM v16 已把该节点改为 FIN_GL）必须同时重发布旧 XML，
--   否则配置与 XML 两处真值不一致（引擎侧 XML 的 candidateGroups 仍会追加为候选组）。
--   旧 XML 在仓库里可直接取回：bpmn-repo/drafts/.bak-CLAIM_draft-v15.xml（v15 版面，Task_14=FSSC_AUDITOR），
--   或 POST /api/tenant/wf/designer/publish {id:107, bpmnXml:<该文件>} 回到 v17。
-- 执行顺序：① 先跑本 SQL ② 再重发布 v15 XML ③ 核 wf_instance CLAIM RUNNING=0 时才允许执行。
-- ============================================================================

SET SESSION lock_wait_timeout = 10;
SET SESSION innodb_lock_wait_timeout = 10;
SELECT COUNT(*) AS open_trx_should_be_0 FROM information_schema.INNODB_TRX;

UPDATE wf_node_config c
  JOIN wf_node_config_bak_v1426_20260922 b ON b.model_key = c.model_key AND b.node_key = c.node_key
   SET c.assign_value = b.assign_value,
       c.node_name    = b.node_name,
       c.update_time  = NOW()
 WHERE c.model_key = 'CLAIM' AND c.node_key = 'Task_14';
SELECT ROW_COUNT() AS reverted_rows;

SELECT model_key, node_key, node_name, assign_value FROM wf_node_config
 WHERE model_key = 'CLAIM' AND node_key IN ('Task_7','Task_14') ORDER BY node_key;

-- 备份表**不删**（保留取证；如需清理请另行确认后 DROP TABLE wf_node_config_bak_v1426_20260922）
