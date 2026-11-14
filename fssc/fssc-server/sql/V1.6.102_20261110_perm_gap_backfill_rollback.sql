-- SPDX-License-Identifier: AGPL-3.0-or-later
-- =====================================================================
-- V1.6.102 回滚：权限缺口补齐
--
-- 本回滚会把三枚权限从对应角色**整组摘除**（按 role_code + perms 匹配，不按 id）。
--   若变更后有人又对同一角色授过同一个码，这里会一并删掉 —— 执行前先跑下面的核对 SQL：
--
--   SELECT rm.id, r.role_code, m.perms, rm.create_time
--   FROM sys_role_menu rm
--   JOIN sys_role r ON r.id = rm.role_id
--   JOIN sys_menu m ON m.id = rm.menu_id
--   WHERE (r.role_code = 'SCAN_STAFF'         AND m.perms = 'asset:update')
--      OR (r.role_code = 'BASE_FIN_MANAGER'   AND m.perms = 'md:bankaccount:update')
--      OR (r.role_code = 'COMPLIANCE_ADMIN'   AND m.perms = 'compliance:policy:list');
--   -- 核对 create_time 是否都是本次变更时间点，再决定要不要删
--
-- 回滚后同样**必须重启服务**，否则权限缓存仍生效。
-- =====================================================================

-- ① 撤回 SCAN_STAFF ← asset:update
DELETE rm
FROM sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id
JOIN sys_menu m ON m.id = rm.menu_id
WHERE r.role_code = 'SCAN_STAFF'
  AND m.perms = 'asset:update';

-- ② 撤回 BASE_FIN_MANAGER ← md:bankaccount:update
--    只回滚 update；该角色的 create 权限本就有，不在此处
DELETE rm
FROM sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id
JOIN sys_menu m ON m.id = rm.menu_id
WHERE r.role_code = 'BASE_FIN_MANAGER'
  AND m.perms = 'md:bankaccount:update';

-- ③ 撤回 COMPLIANCE_ADMIN ← compliance:policy:list（两行菜单一并撤回）
DELETE rm
FROM sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id
JOIN sys_menu m ON m.id = rm.menu_id
WHERE r.role_code = 'COMPLIANCE_ADMIN'
  AND m.perms = 'compliance:policy:list';

-- =====================================================================
-- 回滚后自检：三枚码应回到「零角色持有」状态（即缺陷复现）
--   复查语句同正向脚本末尾，持有角色数应变为 0
-- =====================================================================
