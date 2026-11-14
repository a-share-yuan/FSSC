-- =====================================================================
-- V1.6.11 删除「税务侧重复的进项认证」（2026-11-03 功能去重）
--
-- 背景：进项认证的对象是发票池里的进项发票（inv_invoice），唯一写入口是
--       InvoiceService.certify / cancelCertify / transferOut（/api/tenant/invoice/certify/**，
--       页面在「发票管理 → 进项发票认证」，菜单 3075）。
--       税务工作区此前另做了一套 /api/tenant/tax/certify/** + 独立页面，
--       同一动作两个入口、两套权限码、两处状态口径 —— 重复且容易互相覆盖。
--       现已合并：税务侧页面 / 接口 / 服务全部删除，只保留发票池一套。
--
-- 本脚本只清理「已经没有人用的」权限码，不动任何业务数据：
--   · sys_menu 里 gl:taxcertify:* 六个功能点（3442~3447）
--   · 对应的 sys_role_menu 授权（现盘 18 行）
--
-- tax_input_certify 表保留不删：它是改造前的历史台账（现盘 0 行），
--   删表属不可逆动作，交清库窗单独确认；本脚本不碰它。
--
-- 可重复执行：是（按 perms 前缀删，删过即空）
-- =====================================================================

-- ① 先撤授权（外键意义上的顺序：先子表再主表）
DELETE rm FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE m.perms LIKE 'gl:taxcertify:%';

-- ② 再删权限点
DELETE FROM sys_menu WHERE perms LIKE 'gl:taxcertify:%';

-- ③ 菜单级 perms 串里也残留着这个码（如「税务管理」菜单的
--    'gl:taxprov:list,gl:taxdecl:list,gl:taxcertify:list,gl:taxrisk:list'），一并摘掉，
--    否则新环境从零装时菜单会引用一个不存在的权限码。
UPDATE sys_menu
   SET perms = TRIM(BOTH ',' FROM REPLACE(REPLACE(perms, 'gl:taxcertify:list,', ''), 'gl:taxcertify:list', ''))
 WHERE perms LIKE '%gl:taxcertify%';

-- ④ 校验：应当一行都不剩
SELECT '残余 gl:taxcertify 权限点' AS check_id, COUNT(*) AS cnt
  FROM sys_menu WHERE perms LIKE 'gl:taxcertify:%';

SELECT 'V1.6.11 完成：税务侧重复的进项认证权限点已清理（含菜单 perms 串）' AS result;
