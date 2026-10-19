-- V1.4.79 LinkU 开放接口幂等键 —— 回滚
-- 对应 V1.4.79_linku_open_idempotency.sql
-- 说明：回滚仅撤销库级幂等防线，应用层（LinkuOpenGuard）会自动降级为「无幂等保护」+ 审计照常落库，
--       故回滚是安全的；但回滚后重放保护完全依赖 BCP 侧调用方自行保证。

ALTER TABLE linku_sync_log DROP KEY uk_linku_sync_log_idem;
ALTER TABLE linku_sync_log DROP COLUMN active_idem_key;
ALTER TABLE linku_sync_log DROP COLUMN idempotency_key;
