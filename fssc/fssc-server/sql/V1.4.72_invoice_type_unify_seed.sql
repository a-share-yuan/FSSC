-- ================================================================
-- V1.4.72  发票类型字典统一：修复「下拉一套词、表格另一套词」的双名称表分裂
-- ----------------------------------------------------------------
-- 背景（本批发现的真实缺陷，非理论风险）：
--   前端存在两份互不引用的发票类型名称表——
--     ① fssc-web/src/constants/invoiceType.js  INVOICE_TYPES（13 项）→ 供 el-select 下拉选项
--     ② fssc-web/src/constants/enums.js       INVOICE_TYPE （ 8 项）→ 供 enumLabel() 取表格/详情中文
--   V1.4.71 已把 ② 灌成字典 enum_invoice_type（8 行），但 ① 仍是硬编码，于是：
--     · 下拉里选「增值税专用发票」，保存后列表列经 invoiceTypeLabel() 显示成「增值税专用」——同一条数据两处两样；
--     · 高铁/动车、火车票、航空运输电子客票、出租车、网约车、定额发票、停车费、医疗收费票据
--       这 8 类在 ② 里根本没有，enumLabel() 默认处理直接把英文码 RAIL_HIGH / RIDE_HALLING … 显示给用户。
--
-- 权威源裁定：**以 invoiceType.js 为准**。理由——后端
--   OcrLocalParsers T_VAT_SPECIAL…T_OTHER 恰为 13 型，且源码注释明写
--   「编码与前端 constants/invoiceType.js 对齐」；enums.js 那份是没跟上 OCR 扩展的过时残本。
--
-- 标签规则：13 个正式码逐字取自 invoiceType.js（未自造中文）；
--   SPECIAL/NORMAL 与 VAT/VATN/增专/增普 为历史兼容码，仅保留供回显旧数据，不参与下拉。
--   VAT 一处真冲突：enums.js 作「增值税普通」、invoiceType.js LEGACY 作「增值税专票」，
--      本脚本采纳后者（VAT 与 VAT_SPECIAL 同族、VATN 对应 NORMAL，与后端归一逻辑一致）。
--
-- 回滚脚本：V1.4.72_invoice_type_unify_rollback.sql
-- ================================================================

DELETE FROM fssc.sys_dict_data WHERE tenant_id = 1 AND dict_type = 'enum_invoice_type';

INSERT INTO fssc.sys_dict_type
 (tenant_id,dict_type,dict_name,is_builtin,status,create_by,del_flag,remark) VALUES
 (1,'enum_invoice_type','【枚举】发票类型（已统一 13 型）',1,'NORMAL',NULL,0,
  '权威源 invoiceType.js + 后端 OcrLocalParsers T_* 13 型；历史兼容码 6 个仅回显不下拉')
 ON DUPLICATE KEY UPDATE
  dict_name = VALUES(dict_name),
  is_builtin = VALUES(is_builtin),
  remark = VALUES(remark);

INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag,remark) VALUES
 -- ---- 13 个正式型（与 OcrLocalParsers T_* 一一对应，逐字取自 invoiceType.js）----
 (1,'enum_invoice_type','增值税专用发票',    'VAT_SPECIAL',  NULL, 1, 0,'NORMAL',NULL,0,'正式型'),
 (1,'enum_invoice_type','增值税普通发票',    'VAT_NORMAL',   NULL, 2, 0,'NORMAL',NULL,0,'正式型'),
 (1,'enum_invoice_type','电子发票',          'ELECTRONIC',   NULL, 3, 0,'NORMAL',NULL,0,'正式型'),
 (1,'enum_invoice_type','高铁 / 动车',       'RAIL_HIGH',    NULL, 4, 0,'NORMAL',NULL,0,'正式型'),
 (1,'enum_invoice_type','火车票',            'TRAIN',        NULL, 5, 0,'NORMAL',NULL,0,'正式型'),
 (1,'enum_invoice_type','航空运输电子客票',  'AIR',          NULL, 6, 0,'NORMAL',NULL,0,'正式型'),
 (1,'enum_invoice_type','出租车',            'TAXI',         NULL, 7, 0,'NORMAL',NULL,0,'正式型'),
 (1,'enum_invoice_type','网约车',            'RIDE_HALLING', NULL, 8, 0,'NORMAL',NULL,0,'正式型'),
 (1,'enum_invoice_type','定额发票',          'QUOTA',        NULL, 9, 0,'NORMAL',NULL,0,'正式型'),
 (1,'enum_invoice_type','通行费 / ETC',      'TOLL',         NULL,10, 0,'NORMAL',NULL,0,'正式型'),
 (1,'enum_invoice_type','停车费',            'PARKING',      NULL,11, 0,'NORMAL',NULL,0,'正式型'),
 (1,'enum_invoice_type','医疗收费票据',      'MEDICAL',      NULL,12, 0,'NORMAL',NULL,0,'正式型'),
 (1,'enum_invoice_type','其他',              'OTHER',        NULL,13, 0,'NORMAL',NULL,0,'正式型'),
 -- ---- 历史兼容码（仅回显旧数据，不进下拉选项；新增数据统一写正式型）----
 (1,'enum_invoice_type','增值税专用发票',    'SPECIAL',      NULL,14, 0,'NORMAL',NULL,0,'历史码(原 enums.js)'),
 (1,'enum_invoice_type','增值税普通发票',    'NORMAL',       NULL,15, 0,'NORMAL',NULL,0,'历史码(原 enums.js)'),
 (1,'enum_invoice_type','增值税专票',        'VAT',          NULL,16, 0,'NORMAL',NULL,0,'历史码(V1.4.72 裁定采纳 invoiceType.js)'),
 (1,'enum_invoice_type','增值税普票',        'VATN',         NULL,17, 0,'NORMAL',NULL,0,'历史码'),
 (1,'enum_invoice_type','增值税专用发票',    '增专',         NULL,18, 0,'NORMAL',NULL,0,'历史中文值(ScpReconService 归一化入参)'),
 (1,'enum_invoice_type','增值税普通发票',    '增普',         NULL,19, 0,'NORMAL',NULL,0,'历史中文值(ScpReconService 归一化入参)');
