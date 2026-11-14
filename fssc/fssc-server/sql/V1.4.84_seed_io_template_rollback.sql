-- =============================================================================
-- V1.4.84 回滚：导入导出模板预置
-- =============================================================================
DELETE f FROM `md_io_template_field` f
JOIN `md_io_template` t ON t.id = f.template_id
WHERE t.`template_code` IN ('IO_AR_STATEMENT','IO_AR_STATEMENT_OUT','IO_AR_RECEIPT',
                            'IO_AR_OFFSET','IO_AP_PLAN','IO_BUDGET_ITEM','IO_RC_BAD_DEBT');

DELETE FROM `md_io_template`
WHERE `template_code` IN ('IO_AR_STATEMENT','IO_AR_STATEMENT_OUT','IO_AR_RECEIPT',
                          'IO_AR_OFFSET','IO_AP_PLAN','IO_BUDGET_ITEM','IO_RC_BAD_DEBT')
  AND `is_system` = 1;
