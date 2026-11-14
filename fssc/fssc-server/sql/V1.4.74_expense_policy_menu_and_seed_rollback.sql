-- =============================================================================
-- V1.4.74 回滚  八类差距收尾⑤：费用标准菜单 + 三层种子数据
-- 日期：2026-10-17
-- 删除顺序与插入相反：版本 → 标准明细 → 政策 → 菜单授权 → 菜单
-- 全部按业务键（policy_code / menu perms）定位，不依赖自增 id
-- =============================================================================

-- 1) 版本快照（按 policy_code 关联的政策）
DELETE v FROM expense_policy_version v
JOIN expense_policy p ON p.id = v.policy_id
WHERE p.tenant_id = 1 AND p.policy_code IN ('TRV_CN','TRV_INTL','OFFICE_STD','ENT_STD');

-- 2) 标准明细
DELETE s FROM expense_policy_standard s
JOIN expense_policy p ON p.id = s.policy_id
WHERE p.tenant_id = 1 AND p.policy_code IN ('TRV_CN','TRV_INTL','OFFICE_STD','ENT_STD');

-- 3) 政策
DELETE FROM expense_policy
WHERE tenant_id = 1 AND policy_code IN ('TRV_CN','TRV_INTL','OFFICE_STD','ENT_STD');

-- 4) 菜单授权 + 菜单本体
DELETE FROM sys_role_menu WHERE menu_id = 3778;
DELETE FROM sys_menu WHERE id = 3778 AND path = '/compliance/expense-policy';
