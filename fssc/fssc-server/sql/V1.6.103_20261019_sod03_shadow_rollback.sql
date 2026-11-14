-- SPDX-License-Identifier: AGPL-3.0-or-later
-- =====================================================================
-- V1.6.103 回滚：SoD-03 从影子规则恢复为 ACTIVE
--
-- 回滚后 SoD 报告会**立刻回到 51 条**（PERM 28 条噪声回来）——
--   这是降级前的真实状态，不是回滚引入的问题。
--
-- 回滚只翻 status，并恢复降级前的 note 口径（note 里原来记的是回滚 SQL，
-- 回滚后应换回「规则说明」而不是留着降级理由）。
-- =====================================================================

UPDATE sod_perm_conflict
SET status = 'ACTIVE',
    note   = NULL
WHERE rule_code = 'SoD-03'
  AND status = 'SHADOW';

-- =====================================================================
-- 回滚后自检
--   SELECT rule_code, status FROM sod_perm_conflict WHERE rule_code = 'SoD-03';   -- 期望 ACTIVE
--   SELECT status, COUNT(*) FROM sod_perm_conflict GROUP BY status;               -- 期望 ACTIVE 23
--   POST /api/tenant/compliance/sod/check/run → total 应回到 51
-- =====================================================================
