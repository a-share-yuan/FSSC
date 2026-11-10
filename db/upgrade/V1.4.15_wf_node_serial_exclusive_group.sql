-- 节点级互斥组（2026-11-10）
--
-- 背景：approve.deny_serial_same_handler 线上只对 GL_MONTH_CLOSE 生效（写成 1/ALL 会因
-- FIN_GL 净可用人不足而把多节点同角色的链卡死）。结果是 TR_PAY「付款指令编制↔网银执行」、
-- GL_MANUAL_VOUCHER「底稿复核↔制证确认」两组真正互斥的节点无人约束，同一个人可连续办完两个节点。
--
-- 方案：新增节点级互斥组列，只约束互斥的节点对，不锁整链。
-- 启用前先核对两个节点各自角色的净可用人力，避免角色完全不重叠时仍配置互斥：
--   TR_PAY      Task_2 FUND_STAFF / Task_9 FUND_PAY_STAFF
--   GL_MANUAL_VOUCHER GK_1_R FSSC_AUDITOR / GK_4 FIN_GL

ALTER TABLE wf_node_config
  ADD COLUMN serial_exclusive_group VARCHAR(64) NULL
  COMMENT '节点级互斥组：同实例内同组不同节点必须换人；NULL=不启用';

-- 付款：编制 ≠ 执行
UPDATE wf_node_config
   SET serial_exclusive_group = 'PAY_MAKE_EXEC'
 WHERE model_key = 'TR_PAY'
   AND node_key IN ('Task_2', 'Task_9')
   AND del_flag = 0;

-- 凭证：复核 ≠ 制证
UPDATE wf_node_config
   SET serial_exclusive_group = 'VOUCHER_REVIEW_POST'
 WHERE model_key = 'GL_MANUAL_VOUCHER'
   AND node_key IN ('GK_1_R', 'GK_4')
   AND del_flag = 0;
