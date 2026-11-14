-- =====================================================================
-- 回滚 V1.6.01（/03/07 的结构变更）
-- 注意：删列会丢数据。仅在后端包同步回退时执行。
-- =====================================================================
CALL fssc_drop_column('exp_bill', 'tax_period');
CALL fssc_drop_column('exp_bill', 'tax_type');
CALL fssc_drop_column('tax_declaration', 'related_id');
CALL fssc_drop_column('tax_declaration', 'declaration_kind');
DROP TABLE IF EXISTS `tax_provision_rule`;
SELECT 'V1.6.01 rollback done' AS result;
