-- 流程模型、节点配置与流程绑定的维护能力，从共享运营能力码中拆出
-- 仅调整「流程设置」页面及其按钮；其余页面能力码保持不变。
INSERT INTO pf_feature (feature_code, feature_name, module, sort, remark, del_flag)
SELECT 'FC_WF_CONFIG', '流程设置', 'WORKFLOW', 0,
       '流程模型、节点配置与流程绑定的维护能力', 0
WHERE NOT EXISTS (SELECT 1 FROM pf_feature WHERE feature_code = 'FC_WF_CONFIG');

INSERT INTO pf_package_feature (package_id, feature_code, enabled, del_flag)
SELECT p.id, 'FC_WF_CONFIG', 1, 0
FROM pf_package p
WHERE p.package_code IN ('DEMO', 'FC_BASIC', 'FC_STANDARD', 'FC_PRO')
  AND NOT EXISTS (SELECT 1 FROM pf_package_feature f
                  WHERE f.package_id = p.id
                    AND f.feature_code = 'FC_WF_CONFIG'
                    AND f.del_flag = 0);

UPDATE sys_menu SET feature_code = 'FC_WF_CONFIG'
WHERE (id = 104 OR parent_id = 104)
  AND feature_code = 'FSSC_WF_CENTER';
