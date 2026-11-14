-- V1.4.28 回滚：仅回滚 wf_node_config 配置侧（批次2 第三族 39 个 UPDATE 节点）
-- 注意：
--  a) BPMN 侧不在本文件范围内 —— 必须另外用 bpmn-repo/models/<KEY>/ 下发布前快照（或 publish/ 目录的反向 diff）
--     逐个 /api/tenant/wf/designer/publish 重发布，才能把流程定义退回旧结构。
--  b) 本族新插的 3 行（GL_SALARY_PAY.Task_13、GL_MONTH_CLOSE.Task_17_0/17_1）备份表里没有，
--     回滚用 del_flag=1 逻辑删除而非物理删（增量原则），见下方 1b。
-- 不删任何数据、不删备份表。

-- 1) 回滚被 UPDATE 的行
UPDATE wf_node_config c
  JOIN wf_node_config_bak_V1428_20260922 b
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
UPDATE wf_node_config SET del_flag = 1, update_time = NOW()
 WHERE model_key = 'GL_SALARY_PAY'    AND node_key = 'Task_13'
    OR model_key = 'GL_MONTH_CLOSE'    AND node_key IN ('Task_17_0', 'Task_17_1');

-- 2) 复核：已回到备份态的行数（期望 = 备份表行数）
SELECT (SELECT COUNT(*) FROM wf_node_config_bak_V1428_20260922) AS bak_rows,
       (SELECT COUNT(*) FROM wf_node_config c JOIN wf_node_config_bak_V1428_20260922 b
          ON b.model_key = c.model_key AND b.node_key = c.node_key
        WHERE IFNULL(c.assign_value,'') = IFNULL(b.assign_value,'')
          AND IFNULL(c.node_name,'') = IFNULL(b.node_name,'')
          AND IFNULL(c.empty_strategy,'') = IFNULL(b.empty_strategy,'')
          AND IFNULL(c.handling_mode,'') = IFNULL(b.handling_mode,'')
          AND IFNULL(c.timeout_hours,-1) = IFNULL(b.timeout_hours,-1)) AS restored_rows;
