-- ================================================================
-- V1.4.70 回滚：移除「全局状态展示字典」bill_status_display
-- ----------------------------------------------------------------
-- 回滚后 billStatus.js 的 statusLabel()/statusType() 会**自动回落**
-- 到本地常量 STATUS_LABEL / STATUS_TYPE（useDict 的 fail-soft 设计），
-- 界面文案与改造前逐字一致 —— 故本回滚**零风险、无需同步改前端**。
-- ================================================================

DELETE FROM fssc.sys_dict_data
 WHERE tenant_id = 1 AND dict_type = 'bill_status_display';

DELETE FROM fssc.sys_dict_type
 WHERE tenant_id = 1 AND dict_type = 'bill_status_display';

-- 校验：两表都应归零
SELECT (SELECT COUNT(*) FROM fssc.sys_dict_data
         WHERE tenant_id=1 AND dict_type='bill_status_display') AS data_left,
       (SELECT COUNT(*) FROM fssc.sys_dict_type
         WHERE tenant_id=1 AND dict_type='bill_status_display') AS type_left;
