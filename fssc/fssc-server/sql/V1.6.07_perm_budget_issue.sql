-- =====================================================================
-- V1.6.07 权限点补登记：budget:scheme:issue
-- 依赖：V1.6.00（无表结构变更）
-- 可重复执行：是
-- 影响表：sys_menu / sys_role_menu
-- 说明：免审直下达（DRAFT → ISSUED）原与「提交审批」共用 budget:scheme:update，
--       有编辑权的人可以完全绕过审批链。本脚本把该动作独立成 budget:scheme:issue。
--       刻意「不自动授权给任何人」：这是一条旁路权限，默认无人持有才叫收紧；
--         初始化导入场景由管理员在【角色管理】按岗位勾选，脚本不替业务做这个决定。
--         超级管理员（is_admin=1）按既有通配规则仍可用，用于运维兜底。
-- =====================================================================

SET @budget_menu := (SELECT id FROM sys_menu WHERE path = '/budget/workbench' ORDER BY id LIMIT 1);
SET @budget_menu := IFNULL(@budget_menu,
  (SELECT id FROM (SELECT id FROM sys_menu WHERE menu_name = '预算管理工作台' AND menu_type = 'C' LIMIT 1) t));

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon, feature_code,
                      terminal, sort, visible, status, create_time, del_flag)
SELECT @budget_menu, '预算方案免审下达', 'F', NULL, NULL, 'budget:scheme:issue', NULL, NULL,
       'PC', 9, 1, 1, NOW(), 0
 WHERE @budget_menu IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu
                                   WHERE perms = 'budget:scheme:issue' AND menu_type = 'F') x);

SET @issue_perm_id := (SELECT id FROM sys_menu WHERE perms = 'budget:scheme:issue' AND menu_type = 'F' ORDER BY id LIMIT 1);

-- 授权：默认不授权（见上方说明）。如需给初始化导入岗开放，取消下一段注释并指定 role_id：
-- INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
-- SELECT 1, <role_id>, @issue_perm_id, 1, NOW()
--  WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_menu
--                    WHERE role_id = <role_id> AND menu_id = @issue_perm_id) z);

SELECT 'V1.6.07' AS script, @issue_perm_id AS issue_perm_menu_id,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = @issue_perm_id) AS granted_roles;
-- 期望：issue_perm_menu_id 非空；granted_roles ≥ 1
