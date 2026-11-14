-- =====================================================================
-- V1.6.12 进项认证导出权限补齐
--
-- 背景
--   进项认证的入口已从「税务工作台」收敛到「发票管理」工作台（同一张 inv_invoice，
--   唯一写入口是 /api/tenant/invoice/certify/**）。
--   发票认证页的「导出」按钮按 inv:export 门控，但库里从未定义过这个权限码，
--   v-permission 指令判定失败即把元素从 DOM 移除 —— 结果是非管理员永远看不到导出按钮。
--   （同类码其它域是有的：report:export / returns:pur:export / compliance:export，
--     本项目惯例是导出独立授权，故补码，而不是把按钮改成复用查询权。）
--
-- 本脚本做两件事
--   ① 补建 inv:export 权限点，挂发票管理下，与「进项发票认证-认证」并列。
--   ② 给「已经持有 inv:certify 的角色」补授 inv:export。
--      只补导出、不动认证权：能进认证页的人才有导出按钮，给没有认证权的人授导出是空授权；
--      而认证权属岗位分工（总账会计与税务岗不同），脚本不替业务扩权。
--
-- 幂等：重复执行不重复建点、不重复授权。
-- 生效：权限写入登录令牌，相关用户需重新登录。
-- 回滚：V1.6.12_inv_certify_perm_grant_rollback.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- ① 补建 inv:export 权限点
--    sys_menu.id 无自增，须显式取号；parent 取发票管理（perms=invoice:list 的 M 节点）。
-- ---------------------------------------------------------------------
SET @invoice_root_id := (
    SELECT id FROM sys_menu
     WHERE perms = 'invoice:list' AND menu_type = 'M' AND del_flag = 0
     ORDER BY id LIMIT 1
);

SET @export_menu_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_menu);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, terminal,
                      sort, visible, status, create_by, create_time, del_flag)
SELECT @export_menu_id,
       COALESCE(@invoice_root_id, 215),
       '进项发票认证-导出', 'F', 'inv:export', 'PC',
       9011, 1, 1, 1, NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM (SELECT id FROM sys_menu WHERE perms = 'inv:export' AND del_flag = 0) z
       );

-- ---------------------------------------------------------------------
-- ② 给「已持有 inv:certify 的角色」补授 inv:export
--    认证权载体按 del_flag=0 取（早期的独立菜单节点已软删，授给它取不到码）。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT DISTINCT rm.tenant_id, rm.role_id, t.id, 1, NOW()
  FROM sys_role_menu rm
  JOIN sys_menu src
    ON src.id = rm.menu_id
   AND src.del_flag = 0
   AND src.status = 1
   AND src.perms LIKE '%gl:taxprov:list%'
  JOIN sys_menu t
    ON t.id = (
        SELECT MIN(m2.id) FROM sys_menu m2
         WHERE m2.perms = 'inv:export' AND m2.del_flag = 0 AND m2.status = 1
       )
 WHERE EXISTS (
        SELECT 1 FROM (
            SELECT rm2.tenant_id, rm2.role_id
              FROM sys_role_menu rm2
              JOIN sys_menu m3
                ON m3.id = rm2.menu_id
               AND m3.del_flag = 0
               AND m3.status = 1
               AND m3.perms = 'inv:certify'
        ) c
         WHERE c.tenant_id = rm.tenant_id
           AND c.role_id  = rm.role_id
       )
   AND NOT EXISTS (
        SELECT 1 FROM (SELECT tenant_id, role_id, menu_id FROM sys_role_menu) x
         WHERE x.tenant_id = rm.tenant_id
           AND x.role_id  = rm.role_id
           AND x.menu_id  = t.id
       );

-- ---------------------------------------------------------------------
-- ③ 校验输出
-- ---------------------------------------------------------------------
SELECT '① inv:export 权限点' AS step, id, parent_id, menu_name, perms, status, del_flag
  FROM sys_menu WHERE perms = 'inv:export';

SELECT '② 税务工作区角色的发票权限覆盖' AS step,
       r.role_code, r.role_name,
       MAX(CASE WHEN m.perms = 'inv:list'    THEN 1 ELSE 0 END) AS has_list,
       MAX(CASE WHEN m.perms = 'inv:certify' THEN 1 ELSE 0 END) AS has_certify,
       MAX(CASE WHEN m.perms = 'inv:export'  THEN 1 ELSE 0 END) AS has_export
  FROM sys_role r
  JOIN sys_role_menu rm ON rm.role_id = r.id
  JOIN sys_menu m ON m.id = rm.menu_id AND m.del_flag = 0 AND m.status = 1
 WHERE r.id IN (
        SELECT DISTINCT rm2.role_id
          FROM sys_role_menu rm2
          JOIN sys_menu s2 ON s2.id = rm2.menu_id
         WHERE s2.perms LIKE '%gl:taxprov:list%' AND s2.del_flag = 0 AND s2.status = 1
       )
 GROUP BY r.id, r.role_code, r.role_name
 ORDER BY has_certify DESC, r.id;

-- 提示：has_certify = 0 的角色本次不会获得导出权（无认证页可进）。
--       若业务确认该岗位也需要做认证，请在【角色管理】勾选「进项发票认证-认证」，
--       脚本不代替业务做这个扩权决定。
SELECT '③ 未获导出权的角色（缺认证权，需业务确认）' AS step, r.role_code, r.role_name
  FROM sys_role r
 WHERE r.id IN (
        SELECT DISTINCT rm2.role_id
          FROM sys_role_menu rm2
          JOIN sys_menu s2 ON s2.id = rm2.menu_id
         WHERE s2.perms LIKE '%gl:taxprov:list%' AND s2.del_flag = 0 AND s2.status = 1
       )
   AND NOT EXISTS (
        SELECT 1 FROM sys_role_menu rm3
          JOIN sys_menu m3 ON m3.id = rm3.menu_id AND m3.del_flag = 0 AND m3.status = 1
         WHERE rm3.role_id = r.id AND m3.perms = 'inv:certify'
       );

SELECT 'V1.6.12 完成：进项认证导出权限已补齐（相关用户需重新登录生效）' AS result;
