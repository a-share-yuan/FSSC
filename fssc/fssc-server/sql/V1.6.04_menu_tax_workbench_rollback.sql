-- =====================================================================
-- 回滚 V1.6.04（税务管理菜单）
-- =====================================================================
SET @tax_menu_id := (SELECT id FROM sys_menu WHERE path = '/tax/workbench' ORDER BY id LIMIT 1);

DELETE FROM sys_role_menu WHERE @tax_menu_id IS NOT NULL AND menu_id = @tax_menu_id;
DELETE FROM sys_menu WHERE @tax_menu_id IS NOT NULL AND parent_id = @tax_menu_id AND menu_type = 'F';
DELETE FROM sys_menu WHERE @tax_menu_id IS NOT NULL AND id = @tax_menu_id;
SELECT 'V1.6.04 rollback done' AS result;
