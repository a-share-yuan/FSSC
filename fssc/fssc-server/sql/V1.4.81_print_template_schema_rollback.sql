-- =============================================================================
-- V1.4.81 回滚：打印/套打引擎表结构
-- =============================================================================
-- 严格按依赖倒序删除：套成员 → 套 → 字段项 → 模板头
-- =============================================================================

DROP TABLE IF EXISTS `md_print_template_set_item`;
DROP TABLE IF EXISTS `md_print_template_set`;
DROP TABLE IF EXISTS `md_print_template_item`;
DROP TABLE IF EXISTS `md_print_template`;
