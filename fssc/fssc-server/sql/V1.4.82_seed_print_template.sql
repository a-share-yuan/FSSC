-- =============================================================================
-- V1.4.82 打印模板预置数据（把前端硬编码模板翻译成库数据）
-- =============================================================================
-- 目的：让 usePrintTemplate.js 从「代码常量」改为「读后端」，打通模板可配置链路。
--       5 套模板（费用报销/对公付款/合同/发票/借款）逐字对齐前端 TEMPLATES 的字段集。
-- 去重：全部 NOT EXISTS 护栏；已存在则跳过。
-- 回滚：V1.4.82_seed_print_template_rollback.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 模板头
-- -----------------------------------------------------------------------------
INSERT INTO `md_print_template`
  (`tenant_id`,`template_code`,`template_name`,`biz_type`,`paper_size`,`orientation`,
   `grid_rows`,`grid_cols`,`is_system`,`status`,`remark`)
SELECT 1,'PT_EXPENSE_CLAIM','费用报销单','EXPENSE_CLAIM','A4','PORTRAIT',30,12,1,'ENABLED',
       '由前端 usePrintTemplate.js TEMPLATES.EXPENSE_CLAIM 迁移(V1.4.82)'
WHERE NOT EXISTS (SELECT 1 FROM `md_print_template` WHERE `template_code`='PT_EXPENSE_CLAIM' AND `del_flag`=0);

INSERT INTO `md_print_template`
  (`tenant_id`,`template_code`,`template_name`,`biz_type`,`paper_size`,`orientation`,
   `grid_rows`,`grid_cols`,`is_system`,`status`,`remark`)
SELECT 1,'PT_CORP_PAY','付款申请单','CORP_PAY','A4','PORTRAIT',30,12,1,'ENABLED',
       '由前端 usePrintTemplate.js TEMPLATES.CORP_PAY 迁移(V1.4.82)'
WHERE NOT EXISTS (SELECT 1 FROM `md_print_template` WHERE `template_code`='PT_CORP_PAY' AND `del_flag`=0);

INSERT INTO `md_print_template`
  (`tenant_id`,`template_code`,`template_name`,`biz_type`,`paper_size`,`orientation`,
   `grid_rows`,`grid_cols`,`is_system`,`status`,`remark`)
SELECT 1,'PT_CONTRACT','合同登记表','CONTRACT','A4','PORTRAIT',30,12,1,'ENABLED',
       '由前端 usePrintTemplate.js TEMPLATES.CONTRACT 迁移(V1.4.82)'
WHERE NOT EXISTS (SELECT 1 FROM `md_print_template` WHERE `template_code`='PT_CONTRACT' AND `del_flag`=0);

INSERT INTO `md_print_template`
  (`tenant_id`,`template_code`,`template_name`,`biz_type`,`paper_size`,`orientation`,
   `grid_rows`,`grid_cols`,`is_system`,`status`,`remark`)
SELECT 1,'PT_INVOICE','发票登记表','INVOICE','A4','PORTRAIT',30,12,1,'ENABLED',
       '由前端 usePrintTemplate.js TEMPLATES.INVOICE 迁移(V1.4.82)'
WHERE NOT EXISTS (SELECT 1 FROM `md_print_template` WHERE `template_code`='PT_INVOICE' AND `del_flag`=0);

INSERT INTO `md_print_template`
  (`tenant_id`,`template_code`,`template_name`,`biz_type`,`paper_size`,`orientation`,
   `grid_rows`,`grid_cols`,`is_system`,`status`,`remark`)
SELECT 1,'PT_LOAN','借款申请单','LOAN','A4','PORTRAIT',30,12,1,'ENABLED',
       '由前端 usePrintTemplate.js TEMPLATES.LOAN 迁移(V1.4.82)'
WHERE NOT EXISTS (SELECT 1 FROM `md_print_template` WHERE `template_code`='PT_LOAN' AND `del_flag`=0);

-- -----------------------------------------------------------------------------
-- 字段项：费用报销单（对齐前端字段集）
-- -----------------------------------------------------------------------------
INSERT INTO `md_print_template_item`
  (`tenant_id`,`template_id`,`item_key`,`label`,`data_source`,`item_type`,
   `row_index`,`col_index`,`col_span`,`font_size`,`font_weight`,`align`,
   `required`,`locked`,`format_type`,`sort_order`)
SELECT 1, t.id, x.item_key, x.label, x.data_source, x.item_type,
       x.row_index, x.col_index, x.col_span, x.font_size, x.font_weight, x.align,
       x.required, x.locked, x.format_type, x.sort_order
FROM `md_print_template` t
JOIN (
  SELECT 'title' item_key,'费用报销单' label,NULL data_source,'FIELD' item_type,1 row_index,1 col_index,12 col_span,22 font_size,'BOLD' font_weight,'CENTER' align,0 required,1 locked,'TEXT' format_type,0 sort_order
  UNION ALL SELECT 'billNo','单据编号','head.billNo','FIELD',3,1,4,12,'NORMAL','LEFT',1,0,'TEXT',1
  UNION ALL SELECT 'applyDate','申请日期','head.applyDate','FIELD',3,5,4,12,'NORMAL','LEFT',1,0,'DATE',2
  UNION ALL SELECT 'applyUserName','申请人','head.applyUserName','FIELD',4,1,4,12,'NORMAL','LEFT',1,1,'TEXT',3
  UNION ALL SELECT 'deptName','部门','head.deptName','FIELD',4,5,4,12,'NORMAL','LEFT',0,0,'TEXT',4
  UNION ALL SELECT 'currencyCode','币种','head.currencyCode','FIELD',5,1,4,12,'NORMAL','LEFT',0,0,'TEXT',5
  UNION ALL SELECT 'totalAmount','金额','head.totalAmount','FIELD',5,5,4,12,'BOLD','RIGHT',1,0,'NUMBER',6
  UNION ALL SELECT 'lineHeader','费用明细',NULL,'DIVIDER',7,1,12,14,'BOLD','LEFT',0,0,'TEXT',7
  UNION ALL SELECT 'lines','明细行','lines','LINE_DETAIL',8,1,12,11,'NORMAL','LEFT',0,0,'TEXT',8
  UNION ALL SELECT 'colSubject','科目','lines[].subjectName','FIELD',8,1,4,11,'NORMAL','LEFT',0,0,'TEXT',9
  UNION ALL SELECT 'colSummary','摘要','lines[].summary','FIELD',8,5,4,11,'NORMAL','LEFT',0,0,'TEXT',10
  UNION ALL SELECT 'colAmount','金额','lines[].amount','FIELD',8,9,4,11,'NORMAL','RIGHT',0,0,'NUMBER',11
  UNION ALL SELECT 'signMaker','制单','head.applyUserName','FIELD',20,1,3,11,'NORMAL','CENTER',0,0,'TEXT',12
  UNION ALL SELECT 'signApprove','审批','head.approveUserName','FIELD',20,5,3,11,'NORMAL','CENTER',0,0,'TEXT',13
  UNION ALL SELECT 'signFinance','财务',NULL,'FIELD',20,9,3,11,'NORMAL','CENTER',0,0,'TEXT',14
) x
WHERE t.`template_code`='PT_EXPENSE_CLAIM' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_print_template_item` i WHERE i.template_id=t.id AND i.item_key=x.item_key);

-- 字段项：付款申请单
INSERT INTO `md_print_template_item`
  (`tenant_id`,`template_id`,`item_key`,`label`,`data_source`,`item_type`,
   `row_index`,`col_index`,`col_span`,`font_size`,`font_weight`,`align`,
   `required`,`locked`,`format_type`,`sort_order`)
SELECT 1, t.id, x.item_key, x.label, x.data_source, x.item_type,
       x.row_index, x.col_index, x.col_span, x.font_size, x.font_weight, x.align,
       x.required, x.locked, x.format_type, x.sort_order
FROM `md_print_template` t
JOIN (
  SELECT 'title' item_key,'付款申请单' label,NULL data_source,'FIELD' item_type,1 row_index,1 col_index,12 col_span,22 font_size,'BOLD' font_weight,'CENTER' align,0 required,1 locked,'TEXT' format_type,0 sort_order
  UNION ALL SELECT 'billNo','单据编号','head.billNo','FIELD',3,1,6,12,'NORMAL','LEFT',1,0,'TEXT',1
  UNION ALL SELECT 'applyDate','日期','head.applyDate','FIELD',3,7,6,12,'NORMAL','LEFT',1,0,'DATE',2
  UNION ALL SELECT 'payeeName','收款方','head.payeeName','FIELD',4,1,6,12,'NORMAL','LEFT',1,1,'TEXT',3
  UNION ALL SELECT 'payeeBank','开户行','head.payeeBank','FIELD',4,7,6,12,'NORMAL','LEFT',0,0,'TEXT',4
  UNION ALL SELECT 'payeeAccount','收款账号','head.payeeAccount','FIELD',5,1,6,12,'NORMAL','LEFT',1,1,'TEXT',5
  UNION ALL SELECT 'amount','付款金额','head.amount','FIELD',5,7,6,12,'BOLD','RIGHT',1,0,'NUMBER',6
  UNION ALL SELECT 'reason','付款事由','head.reason','FIELD',7,1,12,12,'NORMAL','LEFT',0,0,'TEXT',7
  UNION ALL SELECT 'signOper','经办',NULL,'FIELD',20,1,4,11,'NORMAL','CENTER',0,0,'TEXT',8
  UNION ALL SELECT 'signApprove','审批','head.approveUserName','FIELD',20,5,4,11,'NORMAL','CENTER',0,0,'TEXT',9
  UNION ALL SELECT 'signFinance','财务',NULL,'FIELD',20,9,4,11,'NORMAL','CENTER',0,0,'TEXT',10
) x
WHERE t.`template_code`='PT_CORP_PAY' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_print_template_item` i WHERE i.template_id=t.id AND i.item_key=x.item_key);

-- 字段项：合同登记表
INSERT INTO `md_print_template_item`
  (`tenant_id`,`template_id`,`item_key`,`label`,`data_source`,`item_type`,
   `row_index`,`col_index`,`col_span`,`font_size`,`font_weight`,`align`,
   `required`,`locked`,`format_type`,`sort_order`)
SELECT 1, t.id, x.item_key, x.label, x.data_source, x.item_type,
       x.row_index, x.col_index, x.col_span, x.font_size, x.font_weight, x.align,
       x.required, x.locked, x.format_type, x.sort_order
FROM `md_print_template` t
JOIN (
  SELECT 'title' item_key,'合同登记表' label,NULL data_source,'FIELD' item_type,1 row_index,1 col_index,12 col_span,22 font_size,'BOLD' font_weight,'CENTER' align,0 required,1 locked,'TEXT' format_type,0 sort_order
  UNION ALL SELECT 'contractNo','合同编号','head.contractNo','FIELD',3,1,4,12,'NORMAL','LEFT',1,0,'TEXT',1
  UNION ALL SELECT 'signDate','签订日期','head.signDate','FIELD',3,5,4,12,'NORMAL','LEFT',1,0,'DATE',2
  UNION ALL SELECT 'contractName','合同名称','head.contractName','FIELD',4,1,6,12,'NORMAL','LEFT',1,1,'TEXT',3
  UNION ALL SELECT 'supplierName','供应商','head.supplierName','FIELD',4,7,6,12,'NORMAL','LEFT',0,0,'TEXT',4
  UNION ALL SELECT 'contractAmount','合同金额','head.contractAmount','FIELD',5,1,4,12,'BOLD','RIGHT',0,0,'NUMBER',5
  UNION ALL SELECT 'currencyCode','币种','head.currencyCode','FIELD',5,5,4,12,'NORMAL','LEFT',0,0,'TEXT',6
  UNION ALL SELECT 'summary','合同内容摘要','head.summary','FIELD',7,1,12,12,'NORMAL','LEFT',0,0,'TEXT',7
) x
WHERE t.`template_code`='PT_CONTRACT' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_print_template_item` i WHERE i.template_id=t.id AND i.item_key=x.item_key);

-- 字段项：发票登记表
INSERT INTO `md_print_template_item`
  (`tenant_id`,`template_id`,`item_key`,`label`,`data_source`,`item_type`,
   `row_index`,`col_index`,`col_span`,`font_size`,`font_weight`,`align`,
   `required`,`locked`,`format_type`,`sort_order`)
SELECT 1, t.id, x.item_key, x.label, x.data_source, x.item_type,
       x.row_index, x.col_index, x.col_span, x.font_size, x.font_weight, x.align,
       x.required, x.locked, x.format_type, x.sort_order
FROM `md_print_template` t
JOIN (
  SELECT 'title' item_key,'发票登记表' label,NULL data_source,'FIELD' item_type,1 row_index,1 col_index,12 col_span,22 font_size,'BOLD' font_weight,'CENTER' align,0 required,1 locked,'TEXT' format_type,0 sort_order
  UNION ALL SELECT 'invoiceCode','发票代码','head.invoiceCode','FIELD',3,1,6,12,'NORMAL','LEFT',1,1,'TEXT',1
  UNION ALL SELECT 'invoiceNo','发票号码','head.invoiceNo','FIELD',3,7,6,12,'NORMAL','LEFT',1,1,'TEXT',2
  UNION ALL SELECT 'invoiceDate','开票日期','head.invoiceDate','FIELD',4,1,6,12,'NORMAL','LEFT',1,0,'DATE',3
  UNION ALL SELECT 'amount','金额','head.amount','FIELD',4,7,6,12,'BOLD','RIGHT',1,0,'NUMBER',4
  UNION ALL SELECT 'sellerName','销方名称','head.sellerName','FIELD',5,1,6,12,'NORMAL','LEFT',0,0,'TEXT',5
  UNION ALL SELECT 'buyerName','购方名称','head.buyerName','FIELD',5,7,6,12,'NORMAL','LEFT',0,0,'TEXT',6
) x
WHERE t.`template_code`='PT_INVOICE' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_print_template_item` i WHERE i.template_id=t.id AND i.item_key=x.item_key);

-- 字段项：借款申请单
INSERT INTO `md_print_template_item`
  (`tenant_id`,`template_id`,`item_key`,`label`,`data_source`,`item_type`,
   `row_index`,`col_index`,`col_span`,`font_size`,`font_weight`,`align`,
   `required`,`locked`,`format_type`,`sort_order`)
SELECT 1, t.id, x.item_key, x.label, x.data_source, x.item_type,
       x.row_index, x.col_index, x.col_span, x.font_size, x.font_weight, x.align,
       x.required, x.locked, x.format_type, x.sort_order
FROM `md_print_template` t
JOIN (
  SELECT 'title' item_key,'借款申请单' label,NULL data_source,'FIELD' item_type,1 row_index,1 col_index,12 col_span,22 font_size,'BOLD' font_weight,'CENTER' align,0 required,1 locked,'TEXT' format_type,0 sort_order
  UNION ALL SELECT 'billNo','单据编号','head.billNo','FIELD',3,1,6,12,'NORMAL','LEFT',1,0,'TEXT',1
  UNION ALL SELECT 'applyDate','日期','head.applyDate','FIELD',3,7,6,12,'NORMAL','LEFT',1,0,'DATE',2
  UNION ALL SELECT 'applyUserName','借款人','head.applyUserName','FIELD',4,1,6,12,'NORMAL','LEFT',1,0,'TEXT',3
  UNION ALL SELECT 'deptName','部门','head.deptName','FIELD',4,7,6,12,'NORMAL','LEFT',0,0,'TEXT',4
  UNION ALL SELECT 'loanAmount','借款金额','head.loanAmount','FIELD',5,1,6,12,'BOLD','RIGHT',1,0,'NUMBER',5
  UNION ALL SELECT 'repayDate','预计还款日','head.repayDate','FIELD',5,7,6,12,'NORMAL','LEFT',0,0,'DATE',6
  UNION ALL SELECT 'reason','借款事由','head.reason','FIELD',7,1,12,12,'NORMAL','LEFT',0,0,'TEXT',7
  UNION ALL SELECT 'signBorrower','借款人','head.applyUserName','FIELD',20,1,4,11,'NORMAL','CENTER',0,0,'TEXT',8
  UNION ALL SELECT 'signApprove','审批',NULL,'FIELD',20,5,4,11,'NORMAL','CENTER',0,0,'TEXT',9
  UNION ALL SELECT 'signFinance','财务',NULL,'FIELD',20,9,4,11,'NORMAL','CENTER',0,0,'TEXT',10
) x
WHERE t.`template_code`='PT_LOAN' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_print_template_item` i WHERE i.template_id=t.id AND i.item_key=x.item_key);

-- -----------------------------------------------------------------------------
-- 模板套：费用报销打印套（主单 + 另附明细页）
-- -----------------------------------------------------------------------------
INSERT INTO `md_print_template_set`
  (`tenant_id`,`set_code`,`set_name`,`biz_type`,`sort_order`,`status`,`remark`)
SELECT 1,'PS_EXPENSE_CLAIM','费用报销打印套','EXPENSE_CLAIM',1,'ENABLED',
       '一次打印：报销单主表(V1.4.82)'
WHERE NOT EXISTS (SELECT 1 FROM `md_print_template_set` WHERE `set_code`='PS_EXPENSE_CLAIM' AND `del_flag`=0);

INSERT INTO `md_print_template_set_item`
  (`tenant_id`,`set_id`,`template_id`,`page_order`,`copies`)
SELECT 1, s.id, t.id, 1, 1
FROM `md_print_template_set` s, `md_print_template` t
WHERE s.`set_code`='PS_EXPENSE_CLAIM' AND s.`del_flag`=0
  AND t.`template_code`='PT_EXPENSE_CLAIM' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_print_template_set_item` i WHERE i.set_id=s.id AND i.template_id=t.id);
