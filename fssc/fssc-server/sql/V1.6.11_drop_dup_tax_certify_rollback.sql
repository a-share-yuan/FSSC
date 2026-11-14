-- =====================================================================
-- 回滚 V1.6.11（恢复税务侧进项认证权限点）
--
-- 恢复内容：6 个功能点，沿用原 id 3442~3447 与原属性（feature_code=FSSC_TAX、PC、sort 61~66）。
-- 授权恢复为「近似还原」：按当前持有 gl:taxprov:list 的角色批量授予
--   —— 删除前实测这两组角色完全一致（[56, 57, 99032]），故近似即等价；
--   若此后角色体系已调整，请到【角色管理】按岗位重新勾选，脚本不替业务猜。
--
-- 仅恢复权限点，不恢复代码：页面与接口已在代码层删除，
--   要真正复活该功能必须同时回滚代码（恢复 TaxCertify.vue / TaxCertifyService / 控制器端点）。
-- =====================================================================

-- 逐行插入：列名由 INSERT 列表给出，SELECT 侧一律位置常量 + FROM DUAL，
-- 避免「SELECT * FROM (子查询)」在 MySQL 里按字面值自动命名列导致 Duplicate column。
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, feature_code, terminal,
                      sort, visible, status, create_by, create_time, del_flag)
SELECT 3442, 3074, '进项认证查询', 'F', 'gl:taxcertify:list', 'FSSC_TAX', 'PC', 61, 1, 1, 1, NOW(), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 3442) z);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, feature_code, terminal,
                      sort, visible, status, create_by, create_time, del_flag)
SELECT 3443, 3074, '进项认证创建', 'F', 'gl:taxcertify:create', 'FSSC_TAX', 'PC', 62, 1, 1, 1, NOW(), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 3443) z);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, feature_code, terminal,
                      sort, visible, status, create_by, create_time, del_flag)
SELECT 3444, 3074, '进项认证执行', 'F', 'gl:taxcertify:certify', 'FSSC_TAX', 'PC', 63, 1, 1, 1, NOW(), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 3444) z);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, feature_code, terminal,
                      sort, visible, status, create_by, create_time, del_flag)
SELECT 3445, 3074, '进项认证撤销', 'F', 'gl:taxcertify:revoke', 'FSSC_TAX', 'PC', 64, 1, 1, 1, NOW(), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 3445) z);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, feature_code, terminal,
                      sort, visible, status, create_by, create_time, del_flag)
SELECT 3446, 3074, '进项抵扣勾选', 'F', 'gl:taxcertify:deduct', 'FSSC_TAX', 'PC', 65, 1, 1, 1, NOW(), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 3446) z);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, feature_code, terminal,
                      sort, visible, status, create_by, create_time, del_flag)
SELECT 3447, 3074, '进项税额转出', 'F', 'gl:taxcertify:transfer', 'FSSC_TAX', 'PC', 66, 1, 1, 1, NOW(), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 3447) z);

-- 授权：按持有 gl:taxprov:list 的角色批量授予（删除前实测与 certify 角色集一致）
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT DISTINCT rm.tenant_id, rm.role_id, m2.id, 1, NOW()
  FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id AND m.perms = 'gl:taxprov:list'
  JOIN sys_menu m2 ON m2.id BETWEEN 3442 AND 3447
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_menu
                                   WHERE role_id = rm.role_id AND menu_id = m2.id) z);

SELECT '恢复后的权限点数' AS check_id, COUNT(*) AS cnt
  FROM sys_menu WHERE perms LIKE 'gl:taxcertify:%';

SELECT 'V1.6.11 rollback done（代码需另行回滚）' AS result;
