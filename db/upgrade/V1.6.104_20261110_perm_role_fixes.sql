-- ============================================================================
-- V1.6.104 · 2026-11-10 · 权限/角色整改
--
-- 背景：
--   1) 职责分离整改：从 FIN_AR 拆出「票据出纳」NOTE_CASHIER，
--      票据实物流转（移交出纳/背书/贴现/托收）与账务登记分属不同角色；
--   2) CONFIG_ADMIN 误持 system:config:create，与「全局参数新增由 admin 执行」
--      的设计不符，本脚本摘除该授权。
--
-- 幂等：全部按 role_code / perms 定位，不写死自增 id，可重复执行。
-- 依赖：sys_role / sys_role_menu / sys_user_role / sys_menu 均在 fssc 主库。
-- 注意：本文件不含人员指派，人员面属演示环境的数据调整，不在迁移脚本范围内。
--
-- ── 兼容 MySQL Workbench 安全更新模式（Error 1175）────────────────────────────
--   Workbench 默认 Safe Updates=ON，会拒绝「WHERE 未用到索引列」的 UPDATE/DELETE。
--   sys_role_menu 的主键是 id，role_id/menu_id 只出现在唯一键
--   uk_tenant_role_menu(tenant_id,role_id,menu_id) 的第 2/3 列，单独作条件用不上索引，
--   因此 `DELETE rm FROM ... WHERE r.role_code=?` 这类多表 DELETE 必报 1175。
--   本文件已做两重适配：
--     ① 所有 DELETE 改为 `WHERE id IN (SELECT ... 派生表)`，按主键定位，安全模式下也能单条执行；
--     ② 首尾用 SET SQL_SAFE_UPDATES 临时关闭并还原，便于整脚本执行。
--   若在 Workbench 里只选中单条语句执行（Ctrl+Enter），该条也已能通过，无需手工关安全模式。
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================================

SET @OLD_SQL_SAFE_UPDATES = @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

-- ① 票据出纳角色（票据实物岗：移交出纳/背书/贴现/托收）
INSERT INTO sys_role (tenant_id, role_code, role_name, data_scope, org_scope, sort,
                      is_builtin, status, remark)
SELECT 1, 'NOTE_CASHIER', '票据出纳', 5, 'COMPANY', 0, 0, 'NORMAL',
       '票据实物岗（arflow:note:cashier）：票据移交出纳、背书、贴现、托收；不含登记、审单、作废（归应收会计），避免与银行对账由同一人办理'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'NOTE_CASHIER' AND del_flag = 0);

-- ② 该角色只授 4 个菜单：应收管理 3070 / 票据管理 3421 / 票据-查询 3498 /
--    票据-移交出纳背书贴现托收 3500；不授 3499（登记/审单/作废，归应收会计）
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 1, r.id, m.id
  FROM sys_role r
  JOIN sys_menu m ON m.id IN (3070, 3421, 3498, 3500)
 WHERE r.role_code = 'NOTE_CASHIER' AND r.del_flag = 0
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                    WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ③ 应收会计（FIN_AR）摘掉「票据-移交出纳/背书/贴现/托收」：票据实物职责归票据出纳，
--    避免 FIN_AR 同时持 arflow:note:cashier 与 bank:reconcile:list 触发 SoD-08
--    写法说明：按主键 id 定位（派生表 materialize，规避 ERROR 1093 同表子查询限制）
DELETE FROM sys_role_menu
 WHERE id IN (
        SELECT rm_id FROM (
          SELECT rm.id AS rm_id
            FROM sys_role_menu rm
            JOIN sys_role r ON r.id = rm.role_id
            JOIN sys_menu m ON m.id = rm.menu_id
           WHERE r.role_code = 'FIN_AR'
             AND m.perms = 'arflow:note:cashier'
        ) t
       );

-- ④ 票据管理菜单曾被软删（del_flag=1），导致票据出纳持权也打不开页面 → 恢复可见
UPDATE sys_menu SET del_flag = 0 WHERE id = 3421 AND del_flag <> 0;

-- ⑤ 数据配置管理员不再持有「系统参数新增」（手册 §6.2：新增归 admin，维护仍归配置管理员）
DELETE FROM sys_role_menu
 WHERE id IN (
        SELECT rm_id FROM (
          SELECT rm.id AS rm_id
            FROM sys_role_menu rm
            JOIN sys_role r ON r.id = rm.role_id
            JOIN sys_menu m ON m.id = rm.menu_id
           WHERE r.role_code = 'CONFIG_ADMIN'
             AND m.perms = 'system:config:create'
        ) t
       );

-- 还原会话的安全更新开关（保持调用方原设置）
SET SQL_SAFE_UPDATES = @OLD_SQL_SAFE_UPDATES;

-- ============================================================================
-- 校验（应得到：1 个 NOTE_CASHIER 角色 / 4 条授权 / FIN_AR 无 note:cashier /
--       menu 3421 del_flag=0 / CONFIG_ADMIN 无 config:create）
-- ============================================================================
SELECT 'role' AS item, COUNT(*) AS n FROM sys_role
 WHERE role_code = 'NOTE_CASHIER' AND del_flag = 0
UNION ALL
SELECT 'note_cashier_menus', COUNT(*) FROM sys_role_menu rm
  JOIN sys_role r ON r.id = rm.role_id
 WHERE r.role_code = 'NOTE_CASHIER'
UNION ALL
SELECT 'fin_ar_note_cashier_left', COUNT(*) FROM sys_role_menu rm
  JOIN sys_role r ON r.id = rm.role_id
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE r.role_code = 'FIN_AR' AND m.perms = 'arflow:note:cashier'
UNION ALL
SELECT 'menu_3421_enabled', COUNT(*) FROM sys_menu WHERE id = 3421 AND del_flag = 0
UNION ALL
SELECT 'config_admin_config_create_left', COUNT(*) FROM sys_role_menu rm
  JOIN sys_role r ON r.id = rm.role_id
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE r.role_code = 'CONFIG_ADMIN' AND m.perms = 'system:config:create';
