-- V1.4.29 回滚：仅回滚 wf_node_config 配置侧（批次2 第四族 60 个 UPDATE 节点）
-- 注意：
--  a) BPMN 侧不在本文件范围内 —— 必须另外用 bpmn-repo/drafts/fam4/live/<KEY>.xml（发布前从 /designer/export 拉取的活体快照，11 份已入仓）
--     逐个 /api/tenant/wf/designer/publish 重发布，才能把流程定义退回旧结构。
--  b) 本族无新插行（原 92 个 node_key 已覆盖新结构的 60 个节点），故本文件无 1b 段
--  c) 出链而配置行仍保留的幽灵行（92-60=32 行）不需要回滚处理：它们本来就没被改动。
-- 不删任何数据、不删备份表。

-- 1) 回滚被 UPDATE 的行
UPDATE wf_node_config c
  JOIN wf_node_config_bak_V1429_20260922 b
    ON b.model_key = c.model_key AND b.node_key = c.node_key
   SET c.node_name      = b.node_name,
       c.assign_type    = b.assign_type,
       c.assign_value   = b.assign_value,
       c.handling_mode  = b.handling_mode,
       c.empty_strategy = b.empty_strategy,
       c.timeout_hours  = b.timeout_hours,
       c.timeout_action = b.timeout_action,
       c.update_time    = NOW();


-- 2) 复核：已回到备份态的行数（期望 restored_rows = 备份表行数 60）
SELECT (SELECT COUNT(*) FROM wf_node_config_bak_V1429_20260922) AS bak_rows,
       (SELECT COUNT(*) FROM wf_node_config c JOIN wf_node_config_bak_V1429_20260922 b
          ON b.model_key = c.model_key AND b.node_key = c.node_key
        WHERE IFNULL(c.assign_value,'') = IFNULL(b.assign_value,'')
          AND IFNULL(c.node_name,'') = IFNULL(b.node_name,'')
          AND IFNULL(c.empty_strategy,'') = IFNULL(b.empty_strategy,'')
          AND IFNULL(c.handling_mode,'') = IFNULL(b.handling_mode,'')
          AND IFNULL(c.timeout_hours,-1) = IFNULL(b.timeout_hours,-1)) AS restored_rows;
