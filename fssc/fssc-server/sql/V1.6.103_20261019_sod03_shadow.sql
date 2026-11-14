-- SPDX-License-Identifier: AGPL-3.0-or-later
-- =====================================================================
-- V1.6.103  SoD-03 降级为影子规则（SHADOW）
--
-- 本脚本为 2026-10-19 的库内变更补档：当时直接在库上执行，未落迁移脚本。
--
-- 背景：SoD 报告 51 条里有 28 条来自同一规则 SoD-03（自报自批），属报表噪声。
--   SoD-03 判据 =「同时持 exp:bill:submit + exp:bill:audit 两个权限」。
--   实际数据中没有任何角色同时带这两个码：submit 只来自 `COMMON_EMP`（155 人）、
--   audit 只来自 14 个审核岗角色，因此凡审核岗必然命中，28 人全是审核岗、无一例外。
--
--   拆 `COMMON_EMP` 权限的做法不可行：会让 155 人失去报销入口
--   （127 纯员工 + 28 审核岗，submit 无第二来源），而噪声一条不减。
--   真正的自报自批拦截在 `approve.deny_self` 闸门，已由 SoD 的 DUTY 维度巡检覆盖。
--
-- 效果：全量 51 → 23（PERM 28 → 0，DUTY 23 保持不变，真实问题一条没丢）。
-- 机制：`SoDService.loadActivePermRules()` 用 `.eq(status, "ACTIVE")` 等值匹配，
--   置 SHADOW 即被自动排除，无需改 Java 代码。
--
-- 副作用：SHADOW 规则会从所有 PERM 巡检中消失（`permConflictRows()` 与
--   `permConflictMatches()` 共用同一份规则集），详情页也看不到 SoD-03 命中明细。
--   若日后要恢复展示，应另建影子规则视图，不要改回 ACTIVE（28 条噪声会立刻回来）。
--
-- 备份：本脚本会建 `sod_perm_conflict_bak_20261019`（已存在则跳过）。
--   降级只改 `status` 与 `note` 两列，回滚把 `status` 翻回 ACTIVE 即可，
--   业务状态可完整还原。
--
-- 回滚：V1.6.103_20261019_sod03_shadow_rollback.sql
-- =====================================================================

-- ① 备份（若已被清库删除，此处会重建；注意此时备份到的是**已降级后**的状态）
CREATE TABLE IF NOT EXISTS sod_perm_conflict_bak_20261019 AS
SELECT * FROM sod_perm_conflict;

-- ② 降级：只动 SoD-03，且仅当它仍是 ACTIVE（幂等，重跑不改已降级的行）
UPDATE sod_perm_conflict
SET status = 'SHADOW',
    note   = '2026-10-19 降级：粒度问题——判据为「同时持 submit+audit 两个权限」，凡审核岗必然命中（实际 28 人全为审核岗，无一例外），属报表噪声非真实风险。真正的自报自批拦截在 approve.deny_self（见 SoD DUTY 维度，23 条真实自审轨迹）。本规则降级为影子规则，不计入 total；恢复执行：UPDATE sod_perm_conflict SET status=''ACTIVE'' WHERE rule_code=''SoD-03'';'
WHERE rule_code = 'SoD-03'
  AND status = 'ACTIVE';

-- =====================================================================
-- 执行后自检
--   ① 只 SoD-03 变 SHADOW，其余仍 ACTIVE
--      SELECT status, COUNT(*) FROM sod_perm_conflict GROUP BY status;
--      -- 期望：ACTIVE 22 / SHADOW 1
--   ② 接口侧（需服务在跑）
--      POST /api/tenant/compliance/sod/check/run            → total 应为 23（改前 51）
--      POST /api/tenant/compliance/sod/check/run?dimension=PERM → 0
--      POST /api/tenant/compliance/sod/check/run?dimension=DUTY → 23（★真问题未丢）
-- =====================================================================
