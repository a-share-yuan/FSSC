-- ============================================================================
-- V1.6.104 回滚 · 撤回 2026-11-10 权限/角色整改
--
-- 回滚后状态：
--   · NOTE_CASHIER 角色与其 4 条菜单授权、成员绑定全部删除
--   · FIN_AR 重新持有 arflow:note:cashier（票据-移交出纳/背书/贴现/托收）
--   · CONFIG_ADMIN 重新持有 system:config:create（注意：这与手册 §6.2 口径相反，
--     仅用于"整改被否决"的场合，非排障不要执行）
--   · sys_menu 3421（票据管理）**不回滚为软删**：该菜单软删是缺陷，恢复后无副作用
--
-- ── 兼容 MySQL Workbench 安全更新模式（Error 1175）────────────────────────────
--   同正向文件：所有 DELETE 改为按主键 id 定位的 `DELETE ... WHERE id IN (派生表)`，
--   并在首尾临时关闭/还原 SQL_SAFE_UPDATES；单条执行也不会再报 1175。
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================================

SET @OLD_SQL_SAFE_UPDATES = @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

-- ① 解绑成员（避免外键/脏授权）
DELETE FROM sys_user_role
 WHERE id IN (
        SELECT uid FROM (
          SELECT ur.id AS uid
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
           WHERE r.role_code = 'NOTE_CASHIER'
        ) t
       );

-- ② 撤角色菜单授权
DELETE FROM sys_role_menu
 WHERE id IN (
        SELECT rm_id FROM (
          SELECT rm.id AS rm_id
            FROM sys_role_menu rm
            JOIN sys_role r ON r.id = rm.role_id
           WHERE r.role_code = 'NOTE_CASHIER'
        ) t
       );

-- ③ 删角色本体（先物理删，避免 role_code 唯一约束挡住下次重建）
DELETE FROM sys_role
 WHERE id IN (
        SELECT rid FROM (
          SELECT id AS rid FROM sys_role WHERE role_code = 'NOTE_CASHIER'
        ) t
       );

-- ④ FIN_AR 恢复「票据-移交出纳/背书/贴现/托收」
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, r.id, m.id
  FROM sys_role r
  JOIN sys_menu m ON m.perms = 'arflow:note:cashier'
 WHERE r.role_code = 'FIN_AR' AND r.del_flag = 0
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                    WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ⑤ CONFIG_ADMIN 恢复「系统参数新增」（menu 3307）
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, r.id, m.id
  FROM sys_role r
  JOIN sys_menu m ON m.perms = 'system:config:create'
 WHERE r.role_code = 'CONFIG_ADMIN' AND r.del_flag = 0
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                    WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 还原会话的安全更新开关（保持调用方原设置）
SET SQL_SAFE_UPDATES = @OLD_SQL_SAFE_UPDATES;

-- ============================================================================
-- 校验（回滚后应得到：0 个 NOTE_CASHIER / FIN_AR 有 note:cashier /
--       CONFIG_ADMIN 有 config:create）
-- ============================================================================
SELECT 'note_cashier_role' AS item, COUNT(*) AS n FROM sys_role
 WHERE role_code = 'NOTE_CASHIER'
UNION ALL
SELECT 'fin_ar_note_cashier', COUNT(*) FROM sys_role_menu rm
  JOIN sys_role r ON r.id = rm.role_id
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE r.role_code = 'FIN_AR' AND m.perms = 'arflow:note:cashier'
UNION ALL
SELECT 'config_admin_config_create', COUNT(*) FROM sys_role_menu rm
  JOIN sys_role r ON r.id = rm.role_id
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE r.role_code = 'CONFIG_ADMIN' AND m.perms = 'system:config:create';
