-- 回滚：移除节点级互斥组（2026-11-10）
-- 只回滚本迁移新增的组与列；不影响其他节点配置。

UPDATE wf_node_config
   SET serial_exclusive_group = NULL
 WHERE serial_exclusive_group IN ('PAY_MAKE_EXEC', 'VOUCHER_REVIEW_POST');

ALTER TABLE wf_node_config
  DROP COLUMN serial_exclusive_group;
