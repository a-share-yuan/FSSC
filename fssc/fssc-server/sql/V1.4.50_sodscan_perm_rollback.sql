-- =====================================================================================
-- V1.4.50 回滚：撤销「system:sod:scan 权限码播种与 3 岗授权」—— 本文件【未执行】，仅在需要回退时执行
-- 与 V1.4.50_sodscan_perm_seed.sql 成对；按 perms + role_code 精确配对删除，不按 create_time 扫删
-- （沿用 V1.4.48_fin_audit_inv_view_rollback.sql 的配对规则）。
--
-- 回滚后果：
--   1. SoDCheckController.java:55 的注解含 OR 默认处理（system:sod:list），回滚后"执行检查"仍可用，
--      即回到"假校验"状态——功能不断，但权限面上再次无法单独回收执行动作。
--   2. 权限点烤进 JWT，删除授权后相关用户须重新登录才失效。
--   3. 菜单行用 del_flag=1 软删（与 sys_menu 的 @TableLogic 规则一致），不物理删行，留可追溯痕迹。
-- =====================================================================================

SET NAMES utf8mb4;

-- §0 前置只读：确认要撤的行就是 seed 播出去的行
SELECT m.id, m.menu_name, m.perms, m.del_flag, r.role_code
  FROM fssc.sys_menu m
  LEFT JOIN fssc.sys_role_menu rm ON rm.menu_id = m.id
  LEFT JOIN fssc.sys_role r ON r.id = rm.role_id
 WHERE m.perms = 'system:sod:scan';

-- §1 撤授权（只撤本 seed 授出去的 3 岗，其它角色若被人工授过同一 menu_id，此处一并撤；
--    如需保留人工授予，执行前先跑 §0 比对再逐 role_code 收窄 WHERE）
DELETE rm FROM fssc.sys_role_menu rm
  JOIN fssc.sys_menu m ON m.perms = 'system:sod:scan'
  JOIN fssc.sys_role r ON r.id = rm.role_id
 WHERE r.role_code IN ('INTERNAL_AUDITOR', 'COMPLIANCE_ADMIN', 'IT_ADMIN');

-- §2 软删菜单行
UPDATE fssc.sys_menu SET del_flag = 1, update_time = NOW() WHERE perms = 'system:sod:scan' AND del_flag = 0;

-- §3 复点（期望 0）
SELECT COUNT(*) AS live_scan_perm_rows FROM fssc.sys_menu WHERE perms = 'system:sod:scan' AND del_flag = 0;
