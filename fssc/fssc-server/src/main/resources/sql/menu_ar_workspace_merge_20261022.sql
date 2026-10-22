-- AR 工作台合并：删除旧菜单 3915，将 3402 改名为"应收工作台"
-- 执行前备份：mysqldump -h127.0.0.1 -P3306 -uroot -p fssc sys_menu sys_role_menu > perm_backup_20261022.sql

-- ========================================
-- 第一步：查哪些角色持有旧菜单 3915
-- ========================================
SELECT '=== 持有菜单 3915 的角色 ===' AS step_info;
SELECT DISTINCT r.id, r.role_name, r.role_code 
FROM sys_role_menu rm 
JOIN sys_role r ON rm.role_id = r.id 
WHERE rm.menu_id = 3915 AND r.del_flag = 0;

-- ========================================
-- 第二步：软删 3915（原"应收工作台" → /receivable/receivable）
-- ========================================
UPDATE sys_menu SET del_flag = 1, update_time = NOW(), update_by = NULL WHERE id = 3915;

-- ========================================
-- 第三步：重命名 3402（"应收全流程工作台" → "应收工作台"）
-- ========================================
UPDATE sys_menu SET menu_name = '应收工作台', update_time = NOW(), update_by = NULL WHERE id = 3402;

-- ========================================
-- 第四步：验证
-- ========================================
SELECT '=== 验证菜单状态 ===' AS step_info;
SELECT id, menu_name, path, perms, del_flag FROM sys_menu WHERE id IN (3915, 3402);

SELECT '=== 受影响的角色（原本能访问 3915）===' AS step_info;
SELECT DISTINCT r.id, r.role_name, r.role_code
FROM sys_role_menu rm
JOIN sys_role r ON rm.role_id = r.id
WHERE rm.menu_id = 3915 AND r.del_flag = 0;

-- ========================================
-- 说明
-- ========================================
-- 前端 ArFlowWorkspace.vue 中两个新 tab 的权限码：
--   应收单     → receivable:list
--   催收待办   → receivable:list（与 routePerms.js 保持一致）
--
-- 由于这两个 tab 复用的是原 /receivable/* 路由的同一套权限码，
-- 原本能访问 3915 的角色无需额外授权，软删后仍能看到新工作台的对应 tab。
--
-- 深链保留：/receivable/receivable、/receivable/collect 等路由仍在 finance.js 中，
-- 通知/收藏夹跳转不受影响，只是不再出现在菜单树中。
