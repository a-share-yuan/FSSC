-- =====================================================================
-- 回滚 V1.6.13（撤销供应商核准权限启用）
--
-- 做法：把权限点恢复为「停用」，而不是删行。
--   该权限点在老库里本就存在（只是 status=0），删行不是还原原状；
--   停用等于回到变更前：码不参与权限加载，按钮不可见。
--
-- 范围：只撤本次动的三样 —— 核准授权、互斥规则、权限点启用状态。
--      不动供应商建档/修改/删除等既有授权。
-- =====================================================================

-- ① 撤销核准授权
DELETE rm FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE m.perms = 'md:supplier:approve';

-- ② 删除互斥规则
DELETE FROM sod_perm_conflict
 WHERE rule_code = 'SoD-24' AND tenant_id IS NULL;

-- ③ 权限点恢复停用
UPDATE sys_menu SET status = 0 WHERE perms = 'md:supplier:approve';

-- ④ 校验输出
SELECT '① 核准权限点（应为 status=0）' AS step, id, menu_name, perms, status, del_flag
  FROM sys_menu WHERE perms = 'md:supplier:approve';

SELECT '② 核准授权残留（应为 0 行）' AS step, rm.tenant_id, rm.role_id
  FROM sys_role_menu rm JOIN sys_menu m ON m.id = rm.menu_id
 WHERE m.perms = 'md:supplier:approve';

SELECT '③ 互斥规则残留（应为 0 行）' AS step, rule_code
  FROM sod_perm_conflict WHERE rule_code = 'SoD-24';

SELECT 'V1.6.13 rollback 完成：相关用户需重新登录' AS result;
