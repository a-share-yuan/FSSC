-- =====================================================================
-- V1.6.25 的回滚：撤掉「预检漏拦分析」菜单与授权
-- 顺序：先删授权行（外键指向菜单），再删菜单。
-- 幂等：两条都带条件。
-- =====================================================================

DELETE rm FROM `sys_role_menu` rm
  JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
 WHERE m.`path` = '/compliance/precheck-stats';

DELETE FROM `sys_menu` WHERE `path` = '/compliance/precheck-stats';

SELECT COUNT(*) AS left_menu FROM `sys_menu` WHERE `path` = '/compliance/precheck-stats';
