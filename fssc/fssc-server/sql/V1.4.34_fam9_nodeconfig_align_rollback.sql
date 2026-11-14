-- V1.4.34 回滚：仅回滚 wf_node_config 配置侧（批次2 第九族 PREPAY/CLAIM_G1 共 13 个 UPDATE 节点 + 0 个 INSERT）
-- 注意：
--  a) BPMN 侧不在本文件范围内 —— 必须另外用 bpmn-repo/drafts/fam9/live/PREPAY.xml 与 .../CLAIM_G1.xml
--     （发布前活体快照，取自 wf_model.bpmn_xml，已交叉验证与仓内基线 latest.bpmn20.xml 逐字节一致，
--      md5 84726cfb… / ea835485…，见 fam9-baseline-crosscheck.log）逐个 /api/tenant/wf/designer/publish 重发布，
--      才能把流程定义退回 v11 的 13 节点串行结构与 v4 的 7 节点含恒真网关结构。
--  b) 本族零插行，回滚只需按备份表 UPDATE 还原（含 sort）。
--  c) 出链而配置行仍保留的幽灵行（CLAIM_G1.Task_1 / CLAIM_G1.Task_6 / PREPAY.Task_1 / PREPAY.Task_13 / PREPAY.Task_5 / PREPAY.Task_6 / PREPAY.Task_8）不需要回滚处理：它们本来就没被改动。
-- 不删任何数据、不删备份表。

-- 1) 回滚被 UPDATE 的行（含 sort）
UPDATE wf_node_config c
  JOIN wf_node_config_bak_V1434_20260923 b
    ON b.model_key = c.model_key AND b.node_key = c.node_key
   SET c.node_name      = b.node_name,
       c.assign_type    = b.assign_type,
       c.assign_value   = b.assign_value,
       c.handling_mode  = b.handling_mode,
       c.empty_strategy = b.empty_strategy,
       c.timeout_hours  = b.timeout_hours,
       c.timeout_action = b.timeout_action,
       c.sort           = b.sort,
       c.update_time    = NOW();

-- 2) 复核：已回到备份态的行数（期望 restored_rows = 备份表行数 13）
SELECT (SELECT COUNT(*) FROM wf_node_config_bak_V1434_20260923) AS bak_rows,
       (SELECT COUNT(*) FROM wf_node_config c JOIN wf_node_config_bak_V1434_20260923 b
          ON b.model_key = c.model_key AND b.node_key = c.node_key
        WHERE IFNULL(c.assign_value,'') = IFNULL(b.assign_value,'')
          AND IFNULL(c.node_name,'') = IFNULL(b.node_name,'')
          AND IFNULL(c.empty_strategy,'') = IFNULL(b.empty_strategy,'')
          AND IFNULL(c.handling_mode,'') = IFNULL(b.handling_mode,'')
          AND IFNULL(c.timeout_hours,-1) = IFNULL(b.timeout_hours,-1)
          AND IFNULL(c.sort,-1) = IFNULL(b.sort,-1)) AS restored_rows;
