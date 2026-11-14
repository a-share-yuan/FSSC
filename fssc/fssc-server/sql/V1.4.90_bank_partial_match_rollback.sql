-- =============================================================================
-- V1.4.90 回滚：银企对账部分勾对金额列
-- =============================================================================
-- 用途：撤销 V1.4.90_bank_partial_match.sql。
-- 注意：删列会丢失「部分勾对金额」信息，若已产生新的部分勾对数据，
--      回滚后调节表会退回到「按 bank_matched 布尔位」的规则（虚增/漏计问题复现）。
--      因此本回滚只建议在 V1.4.90 刚上线、尚无部分勾对数据时执行。
-- =============================================================================

-- 先把已勾对金额回写进布尔位（尽量保留信息），再删列
UPDATE fssc.rc_receipt
SET bank_matched = CASE
      WHEN bank_matched_amount IS NOT NULL AND amount IS NOT NULL
       AND bank_matched_amount >= amount THEN 1
      ELSE bank_matched
    END;

SET @COL_EXISTS := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'fssc' AND TABLE_NAME = 'rc_receipt' AND COLUMN_NAME = 'bank_matched_amount'
);
SET @DDL := IF(@COL_EXISTS = 1,
  'ALTER TABLE fssc.rc_receipt DROP COLUMN bank_matched_amount',
  'SELECT 1');
PREPARE stmt FROM @DDL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT '回滚自检：列应已删除' AS check_item,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='fssc' AND TABLE_NAME='rc_receipt'
          AND COLUMN_NAME='bank_matched_amount') AS col_cnt;
