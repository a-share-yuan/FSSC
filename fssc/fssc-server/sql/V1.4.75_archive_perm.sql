-- =============================================================================
-- V1.4.75  八类差距收尾⑥：冷热归档运维端点权限码
-- 日期：2026-10-17
-- 背景：FSSC未实现项归类复核_8类框架_20261014.md §4-⑥
--   归档链（10 张 cold 表）从未跑过：archive.enabled 默认关，且**没有任何手动触发入口**
--   （原实现只有定时任务 ColdArchiveJob），运维无法在不等待调度周期的前提下验证。
-- 本件为新增的 ColdArchiveController（/expense/cold-archive/dry-run|run）补权限码。
-- 回滚：见同名 _rollback.sql
-- =============================================================================

-- 权限码挂 parent_id=105「共享运营」：归档属共享运营运维面，与既有 gl:sla:* 同父。
-- id 取 3779 起（执行时 sys_menu MAX(id)=3778）。
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, feature_code, terminal, sort, visible, status, del_flag)
VALUES
  (3779, 105, '归档预演',   'F', 'system:archive:dry-run', 'FSSC_SHARED', 'PC', 3461, 1, 1, 0),
  (3780, 105, '归档执行',   'F', 'system:archive:run',     'FSSC_SHARED', 'PC', 3462, 1, 1, 0);

-- 授权给「有全局参数维护权」的角色（sys_config 的维护者）——归档开关本身就是全局参数，
-- 能在 sys_config 改 archive.enabled 的人理应能触发归档，避免出现"能开关不能跑"的割裂。
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_time)
SELECT 1, rm.role_id, m.id, NOW()
FROM sys_role_menu rm
JOIN sys_menu cfg ON cfg.id = rm.menu_id AND cfg.perms = 'system:config:update'
JOIN sys_menu m   ON m.id IN (3779, 3780)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_menu d
  WHERE d.role_id = rm.role_id AND d.menu_id = m.id
);
