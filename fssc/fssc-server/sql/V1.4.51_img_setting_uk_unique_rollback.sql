-- =====================================================================================
-- V1.4.51 回滚：把 fssc_image.img_setting 的 uk_setting 退回非唯一 —— 本文件【未执行】
-- 与 V1.4.51_img_setting_uk_unique_seed.sql 成对。
--
-- 回滚后果：索引名仍叫 uk_setting 但 NON_UNIQUE=1（即 R-16 原缺陷复现，
--   读侧"按唯一假设取数"会再次出现同名多行取哪条不确定的问题）。
--   回滚只在一种情况下才该做：加约束后暴露出真实的存量重复（本文件 §1 会先做只读探测，
--   若有重复则 ALTER 本来就会失败，不会造成半改状态）。
-- =====================================================================================

SET NAMES utf8mb4;

-- §0 前置只读：确认当前确为唯一（回滚前留值）
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA='fssc_image' AND TABLE_NAME='img_setting' AND INDEX_NAME='uk_setting'
 GROUP BY INDEX_NAME, NON_UNIQUE;

-- §1 退回非唯一（列组合不变）
ALTER TABLE fssc_image.img_setting DROP INDEX uk_setting, ADD INDEX uk_setting (tenant_id, setting_type, setting_code);

-- §2 复点（期望 NON_UNIQUE=1）
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA='fssc_image' AND TABLE_NAME='img_setting' AND INDEX_NAME='uk_setting'
 GROUP BY INDEX_NAME, NON_UNIQUE;
