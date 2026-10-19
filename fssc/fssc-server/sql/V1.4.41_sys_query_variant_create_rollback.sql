-- =============================================================
-- V1.4.41 回滚：删除 sys_query_variant
--
--  本表只有"用户自建方案"这一类可再生数据，删除即清空，
--    不留影子表；如需保留现场，先手动备份：
--    CREATE TABLE bak_sys_query_variant_20260924 AS SELECT * FROM sys_query_variant;
-- =============================================================

DROP TABLE IF EXISTS `sys_query_variant`;
