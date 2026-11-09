-- 还原「流程设置」页面及其按钮的能力码，并移除独立能力码
UPDATE sys_menu SET feature_code = 'FSSC_WF_CENTER'
WHERE (id = 104 OR parent_id = 104)
  AND feature_code = 'FC_WF_CONFIG';

DELETE FROM pf_package_feature WHERE feature_code = 'FC_WF_CONFIG';
DELETE FROM pf_feature WHERE feature_code = 'FC_WF_CONFIG';
