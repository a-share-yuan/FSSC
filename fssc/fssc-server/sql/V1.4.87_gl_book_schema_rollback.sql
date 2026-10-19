-- =============================================================================
-- V1.4.87 回滚：多账簿/多准则/多本位币体系
-- =============================================================================
-- 严格依赖倒序：分录 → 凭证 → 余额 → 科目映射 → 账簿 → 币种 → 准则
DROP TABLE IF EXISTS `gl_book_voucher_line`;
DROP TABLE IF EXISTS `gl_book_voucher`;
DROP TABLE IF EXISTS `gl_book_balance`;
DROP TABLE IF EXISTS `gl_book_subject_map`;
DROP TABLE IF EXISTS `gl_accounting_book`;
DROP TABLE IF EXISTS `gl_currency`;
DROP TABLE IF EXISTS `gl_standard`;
