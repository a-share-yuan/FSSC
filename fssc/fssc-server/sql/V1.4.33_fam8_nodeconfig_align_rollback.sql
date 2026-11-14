-- V1.4.33 回滚：仅回滚 wf_node_config 配置侧（批次2 第八族 REPAY 2 个 UPDATE 节点 + 1 个 INSERT）
-- 注意：
--  a) BPMN 侧不在本文件范围内 —— 必须另外用 bpmn-repo/drafts/fam8/live/REPAY.xml
--     （发布前活体快照，取自 wf_model.bpmn_xml，已交叉验证与仓内基线 bpmn-repo/models/REPAY/latest.bpmn20.xml 逐字节一致，
--      见 fam8-baseline-crosscheck.log）逐个 /api/tenant/wf/designer/publish 重发布，才能把流程定义退回 v7 的 5 节点结构。
--  b) 新插的 1 行（REPAY.Task_6）备份表里没有，回滚用 del_flag=1 逻辑删除而非物理删（增量原则），见 1b。
--  c) 出链而配置行仍保留的幽灵行（REPAY.Task_1 / Task_4 / Task_5）不需要回滚处理：它们本来就没被改动。
--  d) LOAN 本轮未被本 seed 触及（在途=2 未发布），无需回滚；回滚本文件也不会影响 LOAN。
-- 不删任何数据、不删备份表。

-- 1) 回滚被 UPDATE 的行
UPDATE wf_node_config c
  JOIN wf_node_config_bak_V1433_20260923 b
    ON b.model_key = c.model_key AND b.node_key = c.node_key
   SET c.node_name      = b.node_name,
       c.assign_type    = b.assign_type,
       c.assign_value   = b.assign_value,
       c.handling_mode  = b.handling_mode,
       c.empty_strategy = b.empty_strategy,
       c.timeout_hours  = b.timeout_hours,
       c.timeout_action = b.timeout_action,
       c.update_time    = NOW();

-- 1b) 逻辑删除本族新插的行（仅当 BPMN 也已回滚到 v7 结构时才需要）
UPDATE wf_node_config SET del_flag = 1, update_time = NOW() WHERE model_key = 'REPAY' AND node_key = 'Task_6';

-- 2) 复核：已回到备份态的行数（期望 restored_rows = 备份表行数 2）
SELECT (SELECT COUNT(*) FROM wf_node_config_bak_V1433_20260923) AS bak_rows,
       (SELECT COUNT(*) FROM wf_node_config c JOIN wf_node_config_bak_V1433_20260923 b
          ON b.model_key = c.model_key AND b.node_key = c.node_key
        WHERE IFNULL(c.assign_value,'') = IFNULL(b.assign_value,'')
          AND IFNULL(c.node_name,'') = IFNULL(b.node_name,'')
          AND IFNULL(c.empty_strategy,'') = IFNULL(b.empty_strategy,'')
          AND IFNULL(c.handling_mode,'') = IFNULL(b.handling_mode,'')
          AND IFNULL(c.timeout_hours,-1) = IFNULL(b.timeout_hours,-1)) AS restored_rows;
