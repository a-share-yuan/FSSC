-- 回滚 V1.6.19：撤销撤回入口菜单与两批授权，并把 CONFIG_ADMIN 的种子行原样放回
--  顺序：先撤授权，再删菜单（避免残留孤儿 role_menu）

-- ③ 撤销会计期间只读点补授
DELETE FROM sys_role_menu
WHERE tenant_id = 1 AND menu_id = 212
  AND role_id IN (SELECT id FROM sys_role
                  WHERE role_code IN ('FIN_GL','FIN_AR','FIN_AP','CASHIER','FSSC_AUDITOR'));

-- ② 撤销撤回入口的角色授权
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.path = '/expense/my' AND m.parent_id = 3550;

-- ① 删除撤回入口菜单本身
DELETE FROM sys_menu WHERE path = '/expense/my' AND parent_id = 3550;

-- ④ CONFIG_ADMIN 种子行还原
--   4.2 还原三枚冗余行（tenant_id=0）
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_time)
SELECT 0, 99006, m.menu_id, NOW()
FROM (SELECT 3244 AS menu_id UNION ALL SELECT 3246 UNION ALL SELECT 3313) m
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x
                  WHERE x.tenant_id = 0 AND x.role_id = 99006 AND x.menu_id = m.menu_id);

--   4.1 还原三枚真缺行的租户号
UPDATE sys_role_menu SET tenant_id = 0
WHERE role_id = 99006 AND menu_id IN (3307, 3308, 3315) AND tenant_id = 1;
