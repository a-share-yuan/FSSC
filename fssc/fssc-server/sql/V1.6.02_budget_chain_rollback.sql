-- =====================================================================
-- 回滚 V1.6.02（预算链结构变更）
-- 注意：删列会丢数据（冻结标志、映射档位、单据类型配置）。
-- =====================================================================
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bg_item' AND INDEX_NAME = 'idx_tenant_period_id');
SET @s := IF(@idx > 0, 'ALTER TABLE `bg_item` DROP KEY `idx_tenant_period_id`', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CALL fssc_drop_column('md_gl_config', 'doc_type');
CALL fssc_drop_column('md_item_budget_map', 'control_type');
CALL fssc_drop_column('bg_item', 'freeze');
CALL fssc_drop_column('bg_item', 'budget_period_id');
CALL fssc_drop_column('bg_item', 'budget_type_id');
SELECT 'V1.6.02 rollback done' AS result;
