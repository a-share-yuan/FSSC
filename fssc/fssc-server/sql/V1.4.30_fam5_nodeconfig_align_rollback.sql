-- V1.4.30 回滚：仅回滚 wf_node_config 配置侧（批次2 第五族 38 个 UPDATE 节点 + 4 个 INSERT）
-- 注意：
--  a) BPMN 侧不在本文件范围内 —— 必须另外用 bpmn-repo/drafts/fam5/live-pre-publish/<KEY>.xml
--     （发布前活体快照，与 bpmn-repo/models/<KEY>/<id>_v<N>.xml 基线已 11/11 逐字节交叉验证一致）
--     逐个 /api/tenant/wf/designer/publish 重发布，才能把流程定义退回旧结构。
--  b) 本族新插的 4 行备份表里没有，回滚用 del_flag=1 逻辑删除而非物理删（增量原则），见 1b
--  c) 出链而配置行仍保留的幽灵行（79-42=37 行）不需要回滚处理：它们本来就没被改动。
-- 不删任何数据、不删备份表。

-- 1) 回滚被 UPDATE 的行
UPDATE wf_node_config c
  JOIN wf_node_config_bak_V1430_20260922 b
    ON b.model_key = c.model_key AND b.node_key = c.node_key
   SET c.node_name      = b.node_name,
       c.assign_type    = b.assign_type,
       c.assign_value   = b.assign_value,
       c.handling_mode  = b.handling_mode,
       c.empty_strategy = b.empty_strategy,
       c.timeout_hours  = b.timeout_hours,
       c.timeout_action = b.timeout_action,
       c.update_time    = NOW();

-- 1b) 逻辑删除本族新插的行（仅当 BPMN 也已回滚到旧结构时才需要）
UPDATE wf_node_config SET del_flag = 1, update_time = NOW() WHERE model_key = 'FA11-01' AND node_key = 'Task_3';
UPDATE wf_node_config SET del_flag = 1, update_time = NOW() WHERE model_key = 'FA11-04' AND node_key = 'Task_3';
UPDATE wf_node_config SET del_flag = 1, update_time = NOW() WHERE model_key = 'INV_DEVAL' AND node_key = 'Task_5';
UPDATE wf_node_config SET del_flag = 1, update_time = NOW() WHERE model_key = 'INV_INTERNAL_SALE' AND node_key = 'Task_13';

-- 2) 复核：已回到备份态的行数（期望 restored_rows = 备份表行数 42）
SELECT (SELECT COUNT(*) FROM wf_node_config_bak_V1430_20260922) AS bak_rows,
       (SELECT COUNT(*) FROM wf_node_config c JOIN wf_node_config_bak_V1430_20260922 b
          ON b.model_key = c.model_key AND b.node_key = c.node_key
        WHERE IFNULL(c.assign_value,'') = IFNULL(b.assign_value,'')
          AND IFNULL(c.node_name,'') = IFNULL(b.node_name,'')
          AND IFNULL(c.empty_strategy,'') = IFNULL(b.empty_strategy,'')
          AND IFNULL(c.handling_mode,'') = IFNULL(b.handling_mode,'')
          AND IFNULL(c.timeout_hours,-1) = IFNULL(b.timeout_hours,-1)) AS restored_rows;
