-- 回滚 V1.6.07（budget:scheme:issue 权限点）
SET @issue_perm_id := (SELECT id FROM sys_menu WHERE perms = 'budget:scheme:issue' AND menu_type = 'F' ORDER BY id LIMIT 1);
DELETE FROM sys_role_menu WHERE @issue_perm_id IS NOT NULL AND menu_id = @issue_perm_id;
DELETE FROM sys_menu WHERE @issue_perm_id IS NOT NULL AND id = @issue_perm_id;
SELECT 'V1.6.07 rollback done' AS result;
