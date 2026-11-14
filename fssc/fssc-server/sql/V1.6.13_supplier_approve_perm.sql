-- =====================================================================
-- V1.6.13 供应商准入核准权限启用
--
-- 业务背景
--   供应商主数据是付款对象的唯一来源。新建供应商时状态写 PENDING，须经「状态位核准」
--   翻为 ACTIVE 后才可被业务单据引用 —— 这是一道准入控制：防止未经审核的供应商进入
--   付款链路（虚假供应商、关联方利益输送）。
--   会计与内控要求「经办与核准分离」：建档人不得同时核准自己建的档案。
--
-- 现存缺陷
--   核准权限点 md:supplier:approve 在库里是存在的，但：
--     ① 状态为停用（status=0），权限加载只认 status=1，等于该码不存在；
--     ② 挂在「总账核算」菜单下，与供应商主数据不在同一处，角色树里找不到。
--   结果：除管理员外无人能看到「核准」按钮，供应商只能停在 PENDING ——
--   要么业务卡死，要么管理员代劳，两种都让这道控制失效。
--
-- 本脚本
--   ① 启用该权限点，并归位到「系统主数据」菜单下；
--   ② 授予「财务经理」：该角色已有供应商查询权、但没有建档/修改权，
--      天然与建档岗（基地财务经理、数据配置管理员）分离；
--      若某环境该角色已持有建档权，脚本会跳过并输出，不制造自建自批；
--   ③ 新增互斥规则「供应商建档 ↔ 核准」，防止日后把两个权限并给同一角色。
--
-- 幂等：重复执行不重复建点、不重复授权、不重复插规则。
-- 生效：权限写入登录令牌，相关用户需重新登录。
-- 回滚：V1.6.13_supplier_approve_perm_rollback.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- ① 启用并归位权限点
-- ---------------------------------------------------------------------
SET @md_root_id := (
    SELECT id FROM sys_menu
     WHERE menu_name = '系统主数据' AND menu_type = 'M' AND del_flag = 0
     ORDER BY id LIMIT 1
);

UPDATE sys_menu
   SET status = 1,
       del_flag = 0,
       menu_type = 'F',
       parent_id = COALESCE(@md_root_id, parent_id),
       menu_name = '供应商状态核准'
 WHERE perms = 'md:supplier:approve';

-- 老库可能没有这一行，补建（显式取号，sys_menu.id 无自增）
SET @approve_menu_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_menu);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, terminal,
                      sort, visible, status, create_by, create_time, del_flag)
SELECT @approve_menu_id,
       COALESCE(@md_root_id, 3031),
       '供应商状态核准', 'F', 'md:supplier:approve', 'PC',
       56, 1, 1, 1, NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM (SELECT id FROM sys_menu WHERE perms = 'md:supplier:approve' AND del_flag = 0) z
       );

-- ---------------------------------------------------------------------
-- ② 授予「财务经理」
--    跳过已持有建档权（create/update/delete）的角色：给了就是自建自批。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT r.tenant_id, r.id, t.id, 1, NOW()
  FROM sys_role r
  JOIN sys_menu t
    ON t.perms = 'md:supplier:approve' AND t.status = 1 AND t.del_flag = 0
 WHERE r.role_code = 'FIN_MANAGER'
   AND r.status = 'NORMAL'
   AND r.del_flag = 0
   AND NOT EXISTS (
        SELECT 1 FROM (SELECT tenant_id, role_id, menu_id FROM sys_role_menu) x
         WHERE x.tenant_id = r.tenant_id AND x.role_id = r.id AND x.menu_id = t.id
       )
   AND NOT EXISTS (
        SELECT 1 FROM (
            SELECT rm2.tenant_id, rm2.role_id
              FROM sys_role_menu rm2
              JOIN sys_menu m2
                ON m2.id = rm2.menu_id
               AND m2.del_flag = 0
               AND m2.status = 1
               AND m2.perms IN ('md:supplier:create', 'md:supplier:update', 'md:supplier:delete')
        ) c
         WHERE c.tenant_id = r.tenant_id AND c.role_id = r.id
       );

-- ---------------------------------------------------------------------
-- ③ 互斥规则：供应商建档 ↔ 核准
--    供应商建档权与核准权落在同一角色，等于自己建、自己批，准入控制形同虚设。
-- ---------------------------------------------------------------------
INSERT INTO sod_perm_conflict (tenant_id, rule_code, rule_name, risk,
                               pattern_a, pattern_b, note, status, create_time)
SELECT NULL, 'SoD-24', '供应商建档↔核准', '高',
       '^md:supplier:(create|update|delete)$',
       '^md:supplier:approve$',
       '供应商是付款对象的唯一来源，建档与核准须分离；同角色兼任可自建虚假供应商并核准后付款',
       'ACTIVE', NOW()
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM (SELECT id FROM sod_perm_conflict WHERE rule_code = 'SoD-24' AND tenant_id IS NULL) z
       );

-- ---------------------------------------------------------------------
-- ④ 校验输出
-- ---------------------------------------------------------------------
SELECT '① 权限点' AS step, id, parent_id, menu_name, menu_type, perms, status, del_flag
  FROM sys_menu WHERE perms = 'md:supplier:approve';

SELECT '② 核准岗授权' AS step, r.tenant_id, r.role_code, r.role_name
  FROM sys_role r
  JOIN sys_role_menu rm ON rm.role_id = r.id
  JOIN sys_menu m ON m.id = rm.menu_id AND m.del_flag = 0 AND m.status = 1
 WHERE m.perms = 'md:supplier:approve'
 ORDER BY r.tenant_id, r.id;

SELECT '③ 互斥规则' AS step, rule_code, rule_name, risk, status
  FROM sod_perm_conflict WHERE rule_code = 'SoD-24';

-- 自建自批风险检查（应尽量为空）：同时持有建档权与核准权的角色
SELECT '④ 自建自批风险' AS step, r.role_code, r.role_name
  FROM sys_role r
 WHERE EXISTS (
        SELECT 1 FROM sys_role_menu rm JOIN sys_menu m ON m.id = rm.menu_id
         WHERE rm.role_id = r.id AND m.del_flag = 0 AND m.status = 1
           AND m.perms IN ('md:supplier:create', 'md:supplier:update', 'md:supplier:delete'))
   AND EXISTS (
        SELECT 1 FROM sys_role_menu rm JOIN sys_menu m ON m.id = rm.menu_id
         WHERE rm.role_id = r.id AND m.del_flag = 0 AND m.status = 1
           AND m.perms = 'md:supplier:approve');

SELECT 'V1.6.13 完成：供应商核准权限已启用（相关用户需重新登录生效）' AS result;
