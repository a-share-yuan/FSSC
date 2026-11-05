-- =====================================================================
-- V1.6.24 的回滚
--
-- 删列（质检记录的违规原因码）→ 删表（违规原因字典）。
-- 注意：删列会丢掉已记录的违规原因码；扣分本身落在 employee_credit 的分数里，不受影响。
-- 幂等：删列先查 information_schema。
-- =====================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE()
                       AND table_name = 'op_quality'
                       AND column_name = 'violation_reason_code');
SET @ddl := IF(@col_exists > 0,
    'ALTER TABLE `op_quality` DROP COLUMN `violation_reason_code`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS `md_violation_reason`;

SELECT COUNT(*) AS table_gone FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'md_violation_reason';
