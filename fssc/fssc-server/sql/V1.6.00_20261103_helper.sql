-- =====================================================================
-- V1.6.00 通用助手：可重复执行的加列/删列（MySQL 8 无 ADD COLUMN IF NOT EXISTS）
-- 本文件只建两个临时存储过程，不碰业务数据；V1.6.01 起各脚本首行都 CALL 它。
-- 执行顺序：必须先跑本文件。
-- =====================================================================

DROP PROCEDURE IF EXISTS fssc_add_column;
DROP PROCEDURE IF EXISTS fssc_drop_column;

DELIMITER $$
CREATE PROCEDURE fssc_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
  ) THEN
    SET @s = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$

CREATE PROCEDURE fssc_drop_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
  ) THEN
    SET @s = CONCAT('ALTER TABLE `', p_table, '` DROP COLUMN `', p_column, '`');
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;
