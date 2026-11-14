-- =====================================================================
-- 回滚 V1.6.12（撤销进项认证导出权限）
--
-- 范围：只撤本次补的 inv:export（权限点 + 授权）。
--      不动 inv:list / inv:certify —— 这两项是岗位既有的，与本次变更无关，
--      撤掉会让税务岗立刻做不了认证，属误伤。
--
-- 若只想收回某些角色的导出权而保留权限点，只执行 ① 并按 role_id 加条件即可。
-- =====================================================================

-- ---------------------------------------------------------------------
-- ① 撤销 inv:export 授权
-- ---------------------------------------------------------------------
DELETE rm FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE m.perms = 'inv:export';

-- ---------------------------------------------------------------------
-- ② 删除 inv:export 权限点
-- ---------------------------------------------------------------------
DELETE FROM sys_menu WHERE perms = 'inv:export';

-- ---------------------------------------------------------------------
-- ③ 校验输出（均应为 0 行）
-- ---------------------------------------------------------------------
SELECT '① inv:export 权限点残留' AS step, id, menu_name, perms
  FROM sys_menu WHERE perms = 'inv:export';

SELECT '② inv:export 授权残留' AS step, rm.tenant_id, rm.role_id, m.perms
  FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE m.perms = 'inv:export';

-- 认证与查询权应保持完好
SELECT '③ 税务岗认证权（应保留）' AS step, r.role_code, m.perms
  FROM sys_role r
  JOIN sys_role_menu rm ON rm.role_id = r.id
  JOIN sys_menu m ON m.id = rm.menu_id AND m.del_flag = 0 AND m.status = 1
 WHERE r.id IN (
        SELECT DISTINCT rm2.role_id FROM sys_role_menu rm2
          JOIN sys_menu s2 ON s2.id = rm2.menu_id
         WHERE s2.perms LIKE '%gl:taxprov:list%' AND s2.del_flag = 0
       )
   AND m.perms IN ('inv:list', 'inv:certify')
 ORDER BY r.role_code, m.perms;

SELECT 'V1.6.12 rollback 完成：仅撤销导出权，认证与查询权保留；相关用户需重新登录' AS result;
