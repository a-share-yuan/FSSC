-- =====================================================================
-- V1.6.21  2026-11-05  预算行冻结/解冻权限点
--
-- 背景：`POST /tenant/budget/item/{id}/freeze` 一直存在，但前端没有任何入口，
--       权限也只是借用 `budget:item:create`（能录预算就能冻预算，粒度不对）。
--       11-03 的演练正是从接口冻了一行、事后没人知道原因，界面上也无法解冻。
--
-- 本增量：
--   ① 新增按钮级权限 `budget:item:freeze`（菜单类型 F，挂在预算管理权限树下）；
--   ② 把它授给所有已持有 `budget:item:create` 的角色（等价迁移，不动现有授权面）。
--
-- 兼容：后端注解为 @RequiresPerm({"budget:item:freeze", "budget:item:create"})，
--       本脚本跑与不跑都能用；跑完前端按钮才按新码精细控制。
-- 幂等：菜单按 perms 判在册，授权按 (tenant,role,menu) 判在册。
-- 回滚：V1.6.21_budget_item_freeze_perm_rollback.sql
-- =====================================================================

-- ① 权限点
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `perms`, `terminal`,
                        `sort`, `visible`, `status`, `create_time`)
SELECT 3624, '预算明细冻结解冻', 'F', 'budget:item:freeze', 'PC', 31, 1, 1, NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perms` = 'budget:item:freeze' AND `del_flag` = 0);

-- ② 等价迁移：持有 budget:item:create 的角色一并授予新码
INSERT INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`, `create_time`)
SELECT DISTINCT rm.`tenant_id`, rm.`role_id`, m.`id`, NOW()
  FROM `sys_role_menu` rm
  JOIN `sys_menu` oldm ON oldm.`id` = rm.`menu_id`
                      AND oldm.`perms` = 'budget:item:create'
                      AND oldm.`del_flag` = 0
  JOIN `sys_menu` m ON m.`perms` = 'budget:item:freeze' AND m.`del_flag` = 0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_role_menu` x
                    WHERE x.`tenant_id` = rm.`tenant_id`
                      AND x.`role_id` = rm.`role_id`
                      AND x.`menu_id` = m.`id`);

-- 自检：权限点应 1 行；授权行数应等于持有 create 的 (租户,角色) 组合数
SELECT `id`, `menu_name`, `perms`, `sort` FROM `sys_menu`
 WHERE `perms` = 'budget:item:freeze' AND `del_flag` = 0;

SELECT COUNT(*) AS granted_roles FROM `sys_role_menu` rm
  JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
 WHERE m.`perms` = 'budget:item:freeze';
