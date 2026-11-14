-- ===========================================================================
-- V1.4.62 sys_user.post_id 补数 rollback
-- 与 V1.4.62_post_backfill_seed.sql 成对。写入数据库前 post_id 全为 NULL，故回滚即置 NULL。
-- ===========================================================================

UPDATE fssc.sys_user SET post_id = NULL WHERE post_id IS NOT NULL;

-- 回滚后复点（期望 0）
-- SELECT COUNT(*) FROM fssc.sys_user WHERE post_id IS NOT NULL;
