-- =====================================================================
-- V1.6.19  撤回入口菜单载体 + 会计期间只读点补授 + CONFIG_ADMIN 种子行归位
-- 覆盖三条：
--   D-V5-1104-17｜真带【撤回】的页面 /expense/my 在 sys_menu 无载体 ⇒ 功能在、入口没给
--   D-V5-1104-24｜md:period:list 只授档案两岗 ⇒ 总账/出纳/应收/应付/共享会计的期间校验退化
--   D-V5-1104-18｜role 99006 的 6 行 sys_role_menu.tenant_id=0，与取码口径（rm.tenant_id=ur.tenant_id）
--                 不匹配 ⇒ 死授权；其中 3 枚已有 tenant_id=1 的同权行（冗余），3 枚没有（真缺）
-- 纪律：不改任何业务单据；不删他人数据（99006 只删「同 role 同 menu 已有 tenant=1 版本」的冗余 0 行）；
--       菜单/授权均可按 rollback 脚本原样撤销。
-- 回滚：V1.6.19_menu_withdraw_period_perm_rollback.sql
-- =====================================================================

-- ① 撤回入口：挂到「流程中心」下，排在「我的单据」之后
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon, terminal,
                      sort, visible, status, create_time)
SELECT 3550, '我的费用单据', 'C', '/expense/my', NULL, 'exp:bill:list', NULL, 'PC',
       4, 1, 1, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/expense/my' AND parent_id = 3550 AND del_flag = 0);

-- ② 把入口授给「已经持有 exp:bill:list」的角色（镜像既有授权面，不新开权限）
DROP TEMPORARY TABLE IF EXISTS tmp_roles_exp_bill;
CREATE TEMPORARY TABLE tmp_roles_exp_bill (role_id BIGINT PRIMARY KEY);
INSERT INTO tmp_roles_exp_bill
SELECT DISTINCT role_id FROM sys_role_menu WHERE menu_id = 3623 AND tenant_id = 1;

INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_time)
SELECT 1, t.role_id, m.id, NOW()
FROM tmp_roles_exp_bill t
JOIN sys_menu m ON m.path = '/expense/my' AND m.parent_id = 3550 AND m.del_flag = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x
                  WHERE x.tenant_id = 1 AND x.role_id = t.role_id AND x.menu_id = m.id);

DROP TEMPORARY TABLE IF EXISTS tmp_roles_exp_bill;

-- ③ 会计期间只读点：授给按期间写单/结账的五个岗位（FIN_GL／FIN_AR／FIN_AP／CASHIER／FSSC_AUDITOR）
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_time)
SELECT 1, r.id, 212, NOW()
FROM sys_role r
WHERE r.role_code IN ('FIN_GL','FIN_AR','FIN_AP','CASHIER','FSSC_AUDITOR')
  AND r.del_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x
                  WHERE x.tenant_id = 1 AND x.role_id = r.id AND x.menu_id = 212);

-- ④ CONFIG_ADMIN（99006）种子行归位
--   4.1 三枚「只有 tenant_id=0」的真缺行：归位到租户 1（＝让既有授权真正生效）
UPDATE sys_role_menu SET tenant_id = 1
WHERE role_id = 99006 AND menu_id IN (3307, 3308, 3315) AND tenant_id = 0;

--   4.2 三枚「tenant_id=0 与 1 并存」的冗余行：删 0 留 1（不减少任何权限）
DELETE FROM sys_role_menu
WHERE role_id = 99006 AND menu_id IN (3244, 3246, 3313) AND tenant_id = 0;
