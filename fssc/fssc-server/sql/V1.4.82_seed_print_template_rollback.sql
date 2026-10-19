-- =============================================================================
-- V1.4.82 回滚：打印模板预置数据
-- =============================================================================
-- 倒序删除：套成员 → 套 → 字段项 → 模板头
-- 注意：只删 is_system=1 的系统预置模板；租户自建模板（is_system=0）不受影响。
-- =============================================================================

DELETE i FROM `md_print_template_set_item` i
JOIN `md_print_template_set` s ON s.id = i.set_id
WHERE s.`set_code` = 'PS_EXPENSE_CLAIM';

DELETE FROM `md_print_template_set` WHERE `set_code` = 'PS_EXPENSE_CLAIM';

DELETE i FROM `md_print_template_item` i
JOIN `md_print_template` t ON t.id = i.template_id
WHERE t.`template_code` IN ('PT_EXPENSE_CLAIM','PT_CORP_PAY','PT_CONTRACT',
                            'PT_INVOICE','PT_LOAN');

DELETE FROM `md_print_template`
WHERE `template_code` IN ('PT_EXPENSE_CLAIM','PT_CORP_PAY','PT_CONTRACT',
                          'PT_INVOICE','PT_LOAN')
  AND `is_system` = 1;
