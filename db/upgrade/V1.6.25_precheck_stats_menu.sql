-- =====================================================================
-- V1.6.25  2026-11-05  预检漏拦分析看板：菜单 + 授权
--
-- 背景：预检拦了多少、哪条规则在拦、有多少问题是被人工质检事后才发现的，此前没有任何入口。
--       这一页度量的是**系统规则的缺口**，不是员工的问题，所以独立成页而不是塞进信用体系。
--
-- 权限沿用已有的 compliance:precheck:list（与预检规则页同一套码，不新增权限点）。
-- 幂等：菜单按 path 判在册，授权按 (tenant,role,menu) 判在册。
-- 回滚：V1.6.25_precheck_stats_menu_rollback.sql
-- =====================================================================

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`,
                        `terminal`, `sort`, `visible`, `status`, `create_time`)
SELECT 105, '预检漏拦分析', 'C', '/compliance/precheck-stats', 'compliance/PrecheckStats', 'compliance:precheck:list',
       'PC', 98, 1, 1, NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `path` = '/compliance/precheck-stats' AND `del_flag` = 0);

-- 授权：持有「预检查询」或「审单规则查询」的角色，一并授予本页。
-- 实测：compliance:precheck:* 这组码在库里一条授权都没有（规则页只有菜单、没有角色持有），
-- 单按 precheck 授权本页会「建了没人看得见」，故对合规岗（持有 compliance:review:list）一并开口。
INSERT INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`, `create_time`)
SELECT DISTINCT rm.`tenant_id`, rm.`role_id`, m.`id`, NOW()
  FROM `sys_role_menu` rm
  JOIN `sys_menu` src ON src.`id` = rm.`menu_id`
                     AND src.`perms` IN ('compliance:precheck:list', 'compliance:review:list')
                     AND src.`del_flag` = 0
  JOIN `sys_menu` m ON m.`path` = '/compliance/precheck-stats' AND m.`del_flag` = 0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_role_menu` x
                    WHERE x.`tenant_id` = rm.`tenant_id`
                      AND x.`role_id` = rm.`role_id`
                      AND x.`menu_id` = m.`id`);


-- 连带补齐：合规岗此前根本没有 compliance:precheck:list，
-- 只给菜单不给码的话，页面能进、接口 403。这里把码本身也补上。
INSERT INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`, `create_time`)
SELECT DISTINCT rm.`tenant_id`, rm.`role_id`, m.`id`, NOW()
  FROM `sys_role_menu` rm
  JOIN `sys_menu` src ON src.`id` = rm.`menu_id`
                     AND src.`perms` = 'compliance:review:list'
                     AND src.`del_flag` = 0
  JOIN `sys_menu` m ON m.`perms` = 'compliance:precheck:list' AND m.`del_flag` = 0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_role_menu` x
                    WHERE x.`tenant_id` = rm.`tenant_id`
                      AND x.`role_id` = rm.`role_id`
                      AND x.`menu_id` = m.`id`);

-- 自检
SELECT `id`, `menu_name`, `path`, `perms`, `parent_id` FROM `sys_menu` WHERE `path` = '/compliance/precheck-stats';
SELECT COUNT(*) AS granted_roles FROM `sys_role_menu` rm
  JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
 WHERE m.`path` = '/compliance/precheck-stats';
