-- V1.4.27 回滚：仅回滚 wf_node_config 配置侧（批次2 第二族 37 个节点）
-- 注意：BPMN 侧不在本文件范围内 —— 必须另外用 bpmn-repo/models/<KEY>/.bak-fam2-pre.xml 逐个 /api/tenant/wf/designer/publish
--       重发布，才能把流程定义退回旧结构；本文件不删备份表（增量原则，留取证）。
UPDATE wf_node_config c
  JOIN wf_node_config_bak_v1427_20260922 b
    ON b.model_key = c.model_key AND b.node_key = c.node_key
   SET c.node_name      = b.node_name,
       c.assign_type    = b.assign_type,
       c.assign_value   = b.assign_value,
       c.empty_strategy = b.empty_strategy,
       c.timeout_hours  = b.timeout_hours,
       c.timeout_action = b.timeout_action,
       c.update_time    = NOW();

-- 复核：已回到备份态的行数（期望 = 备份表行数）
SELECT (SELECT COUNT(*) FROM wf_node_config_bak_v1427_20260922) AS bak_rows,
       (SELECT COUNT(*) FROM wf_node_config c JOIN wf_node_config_bak_v1427_20260922 b
          ON b.model_key = c.model_key AND b.node_key = c.node_key
        WHERE IFNULL(c.assign_value,'') = IFNULL(b.assign_value,'')
          AND IFNULL(c.node_name,'') = IFNULL(b.node_name,'')
          AND IFNULL(c.empty_strategy,'') = IFNULL(b.empty_strategy,'')
          AND IFNULL(c.timeout_hours,-1) = IFNULL(b.timeout_hours,-1)) AS restored_rows;
