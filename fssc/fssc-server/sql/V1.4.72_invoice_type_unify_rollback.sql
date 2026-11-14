-- ================================================================
-- V1.4.72 回滚脚本：把 enum_invoice_type 还原成 V1.4.71 状态（9 行，源 enums.js 残本）
-- ----------------------------------------------------------------
-- 回滚会恢复「下拉一套词、表格另一套词」的缺陷与 8 类票据显示英文码的问题，
--    仅在 V1.4.72 引入回归时使用。
-- ================================================================

DELETE FROM fssc.sys_dict_data WHERE tenant_id = 1 AND dict_type = 'enum_invoice_type';

INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag,remark) VALUES
 (1,'enum_invoice_type','增值税专用','VAT_SPECIAL',NULL,1,0,'NORMAL',NULL,0,'INVOICE_TYPE'),
 (1,'enum_invoice_type','增值税普通','VAT_NORMAL', NULL,2,0,'NORMAL',NULL,0,'INVOICE_TYPE'),
 (1,'enum_invoice_type','增值税专用','SPECIAL',    NULL,3,0,'NORMAL',NULL,0,'INVOICE_TYPE'),
 (1,'enum_invoice_type','增值税普通','NORMAL',     NULL,4,0,'NORMAL',NULL,0,'INVOICE_TYPE'),
 (1,'enum_invoice_type','电子发票',  'ELECTRONIC', NULL,5,0,'NORMAL',NULL,0,'INVOICE_TYPE'),
 (1,'enum_invoice_type','通行费',    'TOLL',       NULL,6,0,'NORMAL',NULL,0,'INVOICE_TYPE'),
 (1,'enum_invoice_type','增值税普通','VATN',       NULL,7,0,'NORMAL',NULL,0,'INVOICE_TYPE'),
 (1,'enum_invoice_type','增值税普通','VAT',        NULL,8,0,'NORMAL',NULL,0,'INVOICE_TYPE'),
 (1,'enum_invoice_type','其他',      'OTHER',      NULL,9,0,'NORMAL',NULL,0,'INVOICE_TYPE');
