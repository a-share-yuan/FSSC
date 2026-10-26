-- =============================================================================
-- V1.4.69 回滚：清理本件新增的 5 个字典容器与数据
--
-- 规则精确：只删本件所建的 dict_type；V1.4.67 / V1.4.68 的 23 个容器及其数据**不动**。
-- 执行后校验：期望回到 23 个容器 / 89 行
--   SELECT COUNT(*) FROM fssc.sys_dict_type WHERE del_flag=0;
--   SELECT COUNT(*) FROM fssc.sys_dict_data WHERE del_flag=0;
-- =============================================================================

DELETE FROM fssc.sys_dict_data
 WHERE tenant_id = 1
   AND dict_type IN ('wf_assign_type','wf_handling_mode','wf_reject_scope',
                     'wf_timeout_action','wf_history_action');

DELETE FROM fssc.sys_dict_type
 WHERE tenant_id = 1
   AND dict_type IN ('wf_assign_type','wf_handling_mode','wf_reject_scope',
                     'wf_timeout_action','wf_history_action');