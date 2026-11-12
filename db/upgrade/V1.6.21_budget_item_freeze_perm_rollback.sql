-- =====================================================================
-- V1.6.21 的回滚：撤回预算行冻结/解冻权限点
--
-- 顺序不能反：先删授权行（外键指向菜单），再删菜单本身。
-- 幂等：两条都带条件，重复执行影响 0 行。
-- 回滚后前端按钮按 @RequiresPerm 的旧码 budget:item:create 继续显示，功能不中断。
-- =====================================================================

DELETE rm FROM `sys_role_menu` rm
  JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
 WHERE m.`perms` = 'budget:item:freeze';

DELETE FROM `sys_menu` WHERE `perms` = 'budget:item:freeze';

SELECT COUNT(*) AS left_menus FROM `sys_menu` WHERE `perms` = 'budget:item:freeze';
