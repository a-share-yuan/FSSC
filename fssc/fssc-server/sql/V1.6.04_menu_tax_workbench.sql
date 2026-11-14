-- =====================================================================
-- V1.6.04 菜单：税务管理（ 工作区；进项认证页签已于 2026-11-03 去重删除，见 V1.6.11）
-- 依赖：无（前端路由 tax/workbench 已随包发布）
-- 可重复执行：是（按 path 判重）
-- 影响表：sys_menu / sys_role_menu
-- 说明：不新建目录树，挂在既有「总账/GL」目录 3064 下；权限沿用既有 gl:tax* 码，不新增权限点。
--       3064 是现网目录 id；若目标库 id 不同，请把下文的 @gl_parent 改成实际值：
--         SELECT id, menu_name FROM sys_menu WHERE menu_name LIKE '%总账%' AND menu_type='M';
-- =====================================================================

SET @gl_parent := (SELECT id FROM sys_menu WHERE id = 3064);
SET @gl_parent := IFNULL(@gl_parent,
  (SELECT id FROM (SELECT id FROM sys_menu WHERE menu_type = 'M' AND menu_name LIKE '%总账%' LIMIT 1) t));

-- ① 主菜单（C 型页面）
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon, feature_code,
                      terminal, sort, visible, status, create_time, del_flag)
SELECT @gl_parent, '税务管理', 'C', '/tax/workbench', 'tax/workbench/index',
       'gl:taxprov:list,gl:taxdecl:list,gl:taxrisk:list',
       'Money', NULL, 'PC', 6, 1, 1, NOW(), 0
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE path = '/tax/workbench') x);

SET @tax_menu_id := (SELECT id FROM sys_menu WHERE path = '/tax/workbench' ORDER BY id LIMIT 1);

-- ② 页签按钮（F 型）：计提规则配置复用 gl:taxprov:provision，申报/认证/风险复用既有码，
--    这里只补「计提规则」这一颗入口（其余按钮行 3098/3442~3455 已在，不重复造）
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon, feature_code,
                      terminal, sort, visible, status, create_time, del_flag)
SELECT @tax_menu_id, '计提规则配置', 'F', NULL, NULL, 'gl:taxprov:provision', NULL, NULL,
       'PC', 1, 1, 1, NOW(), 0
 WHERE @tax_menu_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu
                                   WHERE parent_id = @tax_menu_id AND menu_name = '计提规则配置') y);

-- ③ 授权：凡是已授予「GL专项流程(3074)」或任一涉税按钮的角色，一并授予新菜单，
--    否则新页面只有管理员看得见（"有页面无入口"的另一种形态）
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT DISTINCT rm.tenant_id, rm.role_id, @tax_menu_id, 1, NOW()
  FROM sys_role_menu rm
 WHERE @tax_menu_id IS NOT NULL
   AND rm.menu_id IN (3074, 3098, 3442, 3448, 3454)
   AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_menu
                                   WHERE role_id = rm.role_id AND menu_id = @tax_menu_id) z);

-- ④ 校验
SELECT 'V1.6.04' AS script, @tax_menu_id AS tax_menu_id,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = @tax_menu_id) AS granted_roles;
-- 期望：tax_menu_id 非空；granted_roles ≥ 1
