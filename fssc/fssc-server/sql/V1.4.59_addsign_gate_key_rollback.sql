-- =============================================================================
-- V1.4.59 加签校验两枚 sys_config 键 rollback
--
-- 与 V1.4.59_addsign_gate_key_seed.sql 成对。
-- 两键写入数据库前在 sys_config 中不存在，故回滚只需 DELETE，无"还原旧值"分支。
--
-- 执行时机：灰度（CLAIM/LOAN）出现加签误拦、或流程推进受阻时立即执行。
--
-- 回滚后行为：isConfigGatedOn 恒 false ⇒ allow_addsign 退回"运行时零读取"，
--             即回到 2026-10-15 之前的现行为（任何节点都能加签）。
-- =============================================================================

DELETE FROM fssc.sys_config
 WHERE config_key IN ('approve.addsign_gate.enabled','approve.addsign_gate.models');

-- 回滚后复点（期望 0）
-- SELECT COUNT(*) AS should_be_zero
--   FROM fssc.sys_config
--  WHERE config_key IN ('approve.addsign_gate.enabled','approve.addsign_gate.models');

-- 行为复点：灰度模型内 allow_addsign=0 的节点应重新变为"可加签"（即校验不再拦）
-- 验证方式：任取一条 CLAIM/LOAN 且 allow_addsign=0 的在办任务，走 addSign 接口，
--           回滚前应 403（未开放加签），回滚后应进入加签登记流程。