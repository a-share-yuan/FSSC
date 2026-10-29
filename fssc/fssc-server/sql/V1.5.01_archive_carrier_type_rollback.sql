-- =============================================================================
-- V1.5.01 回滚  ARC-M4 载体类型列
-- 前提：仅在"没有任何卷以 ELECTRONIC 登记"时执行；已有 ELECTRONIC 行＝该信息只存在这一列，
--       DROP 即永久丢失（须先导出这些行到备份件）。
--  本件写入过 0 次
-- =============================================================================

-- 步骤 0：回滚前复点（A 期望 1＝列在位；B 期望 0＝无 ELECTRONIC 行）
SELECT 'A carrier_col_exists(expect 1)' AS chk,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
           AND COLUMN_NAME = 'carrier_type') AS v
UNION ALL
SELECT 'B electronic_rows(expect 0)',
       (SELECT COUNT(*) FROM op_archive WHERE del_flag = 0 AND carrier_type = 'ELECTRONIC');

-- 步骤 1：DROP 列（information_schema 护栏）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
      AND COLUMN_NAME = 'carrier_type') = 1,
  'ALTER TABLE `op_archive` DROP COLUMN `carrier_type`',
  'SET @noop := 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 步骤 2：回滚自检（C 期望 0＝列已不存在）
SELECT 'C carrier_col_exists(expect 0)' AS chk,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
           AND COLUMN_NAME = 'carrier_type') AS v;
