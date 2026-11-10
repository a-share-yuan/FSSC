-- =====================================================================
-- V1.6.102  权限缺口补齐（有菜单但零角色持有的权限）
--
-- 背景：三个页面的操作权限只登记在菜单上、没有任何角色持有，导致对应按钮
--   对所有账号都不可用：
--     · 资产调拨：FT_1 节点配「财务扫描岗」，该角色无 `asset:update`，
--       页面点【调拨】返回 403，只能由资产管理员代发起。
--     · 印鉴变更：`md:bankaccount:update` 只授 CONFIG_ADMIN，
--       而建档 `md:bankaccount:create` 只授 BASE_FIN_MANAGER，
--       一个人办不完「建档 → 印鉴变更」这条链。
--     · 预检漏拦分析：`compliance:policy:list` 有菜单但零角色持有，
--       费用政策页对所有账号不可用。
--
-- 三条实现纪律：
--   ① `sys_role_menu.tenant_id` 无默认值，INSERT 必须显式给 1，
--      否则该行与取码口径（rm.tenant_id = ur.tenant_id）不匹配，成为死授权。
--   ② 同一 `perms` 码可能挂在多行菜单上：`compliance:policy:list` 实际挂在 2 行
--      （3137 查询制度 / 3778 费用标准），只授一行另一个页面仍打不开。
--      故本脚本一律 JOIN sys_menu 全量授权，不写死 menu_id。
--   ③ 全部 `WHERE NOT EXISTS` 幂等，可重复执行。
--
-- 回滚：V1.6.102_20261110_perm_gap_backfill_rollback.sql
-- =====================================================================

-- ① 财务扫描岗 ← asset:update（调拨 / 处置等资产操作的发起前置）
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'asset:update' AND m.del_flag = 0
WHERE r.role_code = 'SCAN_STAFF'
  AND r.del_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
  );

-- ② 基地财务经理 ← md:bankaccount:update（使其能独立完成「建档 → 印鉴变更」）
--    注意：建档 create 权限本就在该角色，此处只补 update，不重授 create
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'md:bankaccount:update' AND m.del_flag = 0
WHERE r.role_code = 'BASE_FIN_MANAGER'
  AND r.del_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
  );

-- ③ 合规管理员 ← compliance:policy:list（费用政策/费用标准页的读入口）
--    该码挂 2 行菜单，本 JOIN 会一次补齐两行
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'compliance:policy:list' AND m.del_flag = 0
WHERE r.role_code = 'COMPLIANCE_ADMIN'
  AND r.del_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
  );

-- =====================================================================
-- 执行后自检：三枚权限码各自「持有角色数」应 ≥ 1
--   SELECT m.perms, m.id menu_id, m.menu_name,
--          (SELECT COUNT(DISTINCT rm.role_id) FROM sys_role_menu rm WHERE rm.menu_id = m.id) 持有角色数
--   FROM sys_menu m
--   WHERE m.perms IN ('asset:update','md:bankaccount:update','compliance:policy:list')
--     AND m.del_flag = 0;
--
-- 授权后**必须重启服务**（权限缓存从库重建），否则接口仍按旧集合拦。
-- =====================================================================
