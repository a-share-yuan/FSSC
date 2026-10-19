-- =============================================================================
-- V1.4.60 字典类型 sys_dict_type 灌 rollback
--
-- 与 V1.4.60_dict_type_seed.sql 成对。本件新增 8 行，故回滚整体删除。
-- 不触碰 sys_dict_data —— 本件从未改动该表。
-- =============================================================================

DELETE FROM fssc.sys_dict_type
 WHERE tenant_id = 1
   AND dict_type IN ('expense_type','wf_node_multi_type','wf_node_empty_strategy',
                     'wf_addsign_position','wf_addsign_status','wf_approval_item_category',
                     'sys_status','dict_yes_no');

-- 回滚后复点
-- SELECT COUNT(*) AS should_be_zero FROM fssc.sys_dict_type WHERE tenant_id = 1 AND del_flag = 0;
-- SELECT COUNT(*) AS untouched     FROM fssc.sys_dict_data  WHERE tenant_id = 1 AND del_flag = 0;  -- 期望 3