-- =============================================================================
-- V1.6.98 可配置表单平台 —— 可选样例配置
-- =============================================================================
-- 本件只做配置数据，不含任何代码：一张「通用申请单」+ 一条流程路由 + 五个字段。
-- 用于说明「配置出来的表单如何渲染、保存并启动流程」这条链路。
--
-- 客户落地时不需要这张表单 —— 把它换成自己的 form_code 与字段即可，
-- 或者直接删掉本件的数据（见文件末尾的清理语句）。
--
-- 流程模型沿用已部署的 MD_MASTER（主数据核准流程），因此不需要重新画 BPMN。
-- 换成自己的流程时，只需保证 model_key 已在设计器里发布。
-- =============================================================================

-- ① 表单头：注册为一种单据类型
INSERT INTO md_form_config
  (tenant_id, form_code, bill_type, bill_sub_type, category, form_name,
   model_key, biz_type, store_mode, menu_path, menu_name, menu_parent_id,
   title_expr, allow_draft, status, remark, create_time, del_flag)
SELECT 1, 'DEMO_FORM', 'FORM', NULL, 'FORM', '通用申请单',
       'MD_MASTER', 'DEMO_FORM', 'JSON', '/form/DEMO_FORM', '通用申请单', 3550,
       NULL, 1, 'NORMAL', '可配置表单平台的配置样例，客户可删除', NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM md_form_config WHERE form_code = 'DEMO_FORM' AND tenant_id = 1);

-- ② 流程路由：biz_type → 流程模型
INSERT INTO wf_process_config
  (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, del_flag)
SELECT 1, 'DEMO_FORM', NULL, '通用申请单审批', 'MD_MASTER', 10, 'NORMAL',
       '可配置表单平台的配置样例', NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM wf_process_config
                    WHERE biz_type = 'DEMO_FORM' AND del_flag = 0);

-- ③ 字段：控件类型、栅格、必录、选项全部在这里定义
INSERT INTO md_form_field
  (tenant_id, form_code, field_key, field_label, section, widget_type, control_width, span,
   sort_order, placeholder, required, locked, options_json, system_flag, status, del_flag)
SELECT 1, 'DEMO_FORM', 'apply_reason', '申请事由', '基本信息', 'TEXTAREA', 'FULL', 24,
       10, '请说明申请背景与诉求', 1, 0, NULL, 1, 'ENABLED', 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM md_form_field WHERE form_code = 'DEMO_FORM' AND field_key = 'apply_reason');

INSERT INTO md_form_field
  (tenant_id, form_code, field_key, field_label, section, widget_type, control_width, span,
   sort_order, placeholder, required, locked, options_json, system_flag, status, del_flag)
SELECT 1, 'DEMO_FORM', 'urgency', '紧急程度', '基本信息', 'SELECT', 'HALF', 12,
       20, NULL, 1, 0,
       '{"options":[{"label":"普通","value":"NORMAL"},{"label":"加急","value":"URGENT"},{"label":"特急","value":"CRITICAL"}]}',
       0, 'ENABLED', 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM md_form_field WHERE form_code = 'DEMO_FORM' AND field_key = 'urgency');

INSERT INTO md_form_field
  (tenant_id, form_code, field_key, field_label, section, widget_type, control_width, span,
   sort_order, placeholder, required, locked, options_json, system_flag, status, del_flag)
SELECT 1, 'DEMO_FORM', 'apply_days', '申请天数', '基本信息', 'INPUT_NUMBER', 'HALF', 12,
       30, NULL, 0, 0, NULL, 0, 'ENABLED', 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM md_form_field WHERE form_code = 'DEMO_FORM' AND field_key = 'apply_days');

INSERT INTO md_form_field
  (tenant_id, form_code, field_key, field_label, section, widget_type, control_width, span,
   sort_order, placeholder, required, locked, options_json, system_flag, status, del_flag)
SELECT 1, 'DEMO_FORM', 'expect_date', '期望完成日期', '基本信息', 'DATE', 'HALF', 12,
       40, NULL, 0, 0, NULL, 0, 'ENABLED', 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM md_form_field WHERE form_code = 'DEMO_FORM' AND field_key = 'expect_date');

INSERT INTO md_form_field
  (tenant_id, form_code, field_key, field_label, section, widget_type, control_width, span,
   sort_order, placeholder, required, locked, options_json, system_flag, status, del_flag)
SELECT 1, 'DEMO_FORM', 'contact', '联系人', '基本信息', 'USER_SELECT', 'HALF', 12,
       50, NULL, 0, 0, NULL, 0, 'ENABLED', 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM md_form_field WHERE form_code = 'DEMO_FORM' AND field_key = 'contact');

-- ④ 校验规则：天数为负直接拦在服务端
INSERT INTO md_form_validation
  (tenant_id, form_code, field_key, rule_type, rule_value, error_message,
   severity, scope, sort_order, status, del_flag)
SELECT 1, 'DEMO_FORM', 'apply_days', 'MIN_VALUE', '0', '申请天数不能为负数',
       'ERROR', 'BOTH', 10, 'ENABLED', 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM md_form_validation
                    WHERE form_code = 'DEMO_FORM' AND field_key = 'apply_days' AND rule_type = 'MIN_VALUE');

-- ⑤ 菜单入口：路径必须与 md_form_config.menu_path 一致，前端据此命中通用页
INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, component, perms, icon,
   feature_code, terminal, sort, visible, status, create_time, del_flag)
SELECT 3550, '通用申请单', 'C', '/form/DEMO_FORM', 'form/FormBill', NULL,
       'Document', 'FC_FORM_PLATFORM', 'PC', 91, 1, 1, NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/form/DEMO_FORM');

-- -----------------------------------------------------------------------------
-- 清理（需要移除样例时执行）
-- -----------------------------------------------------------------------------
-- DELETE FROM sys_menu WHERE path = '/form/DEMO_FORM';
-- DELETE FROM md_form_validation WHERE form_code = 'DEMO_FORM';
-- DELETE FROM md_form_field      WHERE form_code = 'DEMO_FORM';
-- DELETE FROM md_form_config     WHERE form_code = 'DEMO_FORM';
-- DELETE FROM wf_process_config  WHERE biz_type  = 'DEMO_FORM';
