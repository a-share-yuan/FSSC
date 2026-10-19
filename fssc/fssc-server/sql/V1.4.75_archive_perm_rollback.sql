-- =============================================================================
-- V1.4.75 回滚  八类差距收尾⑥：冷热归档运维端点权限码
-- 日期：2026-10-17
-- =============================================================================
DELETE FROM sys_role_menu WHERE menu_id IN (3779, 3780);
DELETE FROM sys_menu WHERE id IN (3779, 3780)
  AND perms IN ('system:archive:dry-run', 'system:archive:run');
