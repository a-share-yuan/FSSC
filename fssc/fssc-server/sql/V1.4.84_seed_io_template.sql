-- =============================================================================
-- V1.4.84 导入导出模板预置（把手写 ExcelService 的硬编码列头翻译成模板数据）
-- =============================================================================
-- 本文件由 scripts/gen_io_template_seed.py 生成，请勿手工编辑字段块。
--
-- 列头来源：源码实查 2026-10-18，与现有导入文件格式 100% 兼容，一个字没改。
--   ApPlanExcelService      发票编号/事业部/供应商名称/付款方法/银行账户/付款日期/
--                           折扣额/汇票到期日期/付款额/PO编号/备注
--   ArReceiptExcelService   收款单号/收款日期/客户编码/客户名称/OU/币种/收款金额/发票号/核销金额/备注
--   ArStatementExcelService 客户代码/客户名称/物料名称/型号规格/数量/单价/金额/税额/
--                           价税合计/PO号或交货单号/是否提前开票/备注
--   ArOffsetExcelService    序号/借方科目/借方金额/贷方科目/贷方金额/摘要/往来对象编码
--   BudgetItemExcelService  预算年度/期间/公司编码/公司名称/部门编码/部门名称/费用项编码/
--                           费用项名称/预算���额/币种/NC科目/IFS科目/备注
--   RcBadDebtExcelService   客户编码/客户名称/发票号或应收单号/应收余额/已计提坏账准备/
--                           本次核销金额/币种/核销原因/坏账认定依据编号
--
-- 【为什么这算闭环】新增一种导入模板 = 插两行数据，不需要再写一支 Service。
-- 幂等：NOT EXISTS 护栏。回滚：V1.4.84_seed_io_template_rollback.sql
-- =============================================================================


INSERT INTO `md_io_template`
  (`tenant_id`,`template_code`,`template_name`,`biz_type`,`direction`,
   `target_table`,`header_row`,`is_system`,`status`,`remark`)
SELECT 1,'IO_AR_STATEMENT' template_code,'客户对账单导入模板' template_name,'AR_STATEMENT' biz_type,'IMPORT' direction,
       'ar_statement' target_table,1 header_row,1 is_system,'ENABLED' status,'源: ArStatementExcelService HEADERS(V1.4.84)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_io_template` WHERE `template_code`='IO_AR_STATEMENT' AND `del_flag`=0);

INSERT INTO `md_io_template`
  (`tenant_id`,`template_code`,`template_name`,`biz_type`,`direction`,
   `target_table`,`header_row`,`is_system`,`status`,`remark`)
SELECT 1,'IO_AR_RECEIPT' template_code,'收款核销导入模板' template_name,'AR_RECEIPT' biz_type,'IMPORT' direction,
       'rc_receipt' target_table,1 header_row,1 is_system,'ENABLED' status,'源: ArReceiptExcelService HEADERS(V1.4.84)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_io_template` WHERE `template_code`='IO_AR_RECEIPT' AND `del_flag`=0);

INSERT INTO `md_io_template`
  (`tenant_id`,`template_code`,`template_name`,`biz_type`,`direction`,
   `target_table`,`header_row`,`is_system`,`status`,`remark`)
SELECT 1,'IO_AR_OFFSET' template_code,'应收对冲凭证导入模板' template_name,'AR_OFFSET' biz_type,'IMPORT' direction,
       NULL target_table,1 header_row,1 is_system,'ENABLED' status,'源: ArOffsetExcelService HEADERS(V1.4.84)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_io_template` WHERE `template_code`='IO_AR_OFFSET' AND `del_flag`=0);

INSERT INTO `md_io_template`
  (`tenant_id`,`template_code`,`template_name`,`biz_type`,`direction`,
   `target_table`,`header_row`,`is_system`,`status`,`remark`)
SELECT 1,'IO_AP_PLAN' template_code,'付款计划导入模板' template_name,'AP_PLAN' biz_type,'IMPORT' direction,
       'pay_plan' target_table,1 header_row,1 is_system,'ENABLED' status,'源: ApPlanExcelService HEADERS(V1.4.84)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_io_template` WHERE `template_code`='IO_AP_PLAN' AND `del_flag`=0);

INSERT INTO `md_io_template`
  (`tenant_id`,`template_code`,`template_name`,`biz_type`,`direction`,
   `target_table`,`header_row`,`is_system`,`status`,`remark`)
SELECT 1,'IO_BUDGET_ITEM' template_code,'预算项目导入模板' template_name,'BUDGET_ITEM' biz_type,'IMPORT' direction,
       'bg_item' target_table,1 header_row,1 is_system,'ENABLED' status,'源: BudgetItemExcelService HEADERS(V1.4.84)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_io_template` WHERE `template_code`='IO_BUDGET_ITEM' AND `del_flag`=0);

INSERT INTO `md_io_template`
  (`tenant_id`,`template_code`,`template_name`,`biz_type`,`direction`,
   `target_table`,`header_row`,`is_system`,`status`,`remark`)
SELECT 1,'IO_RC_BAD_DEBT' template_code,'坏账核销导入模板' template_name,'RC_BAD_DEBT' biz_type,'IMPORT' direction,
       'rc_bad_debt' target_table,1 header_row,1 is_system,'ENABLED' status,'源: RcBadDebtExcelService HEADERS(V1.4.84)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_io_template` WHERE `template_code`='IO_RC_BAD_DEBT' AND `del_flag`=0);

INSERT INTO `md_io_template`
  (`tenant_id`,`template_code`,`template_name`,`biz_type`,`direction`,
   `target_table`,`header_row`,`is_system`,`status`,`remark`)
SELECT 1,'IO_AR_STATEMENT_OUT' template_code,'客户对账单导出模板' template_name,'AR_STATEMENT' biz_type,'EXPORT' direction,
       'ar_statement' target_table,1 header_row,1 is_system,'ENABLED' status,'导入模板的反向导出(V1.4.84)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_io_template` WHERE `template_code`='IO_AR_STATEMENT_OUT' AND `del_flag`=0);


-- 字段定义：IO_AR_STATEMENT（12 列）

INSERT INTO `md_io_template_field`
  (`tenant_id`,`template_id`,`field_key`,`column_name`,`column_type`,`required`,
   `enum_values`,`ref_table`,`ref_code_col`,`ref_name_col`,`max_length`,
   `decimal_scale`,`sort_order`,`remark`)
SELECT 1, t.id, x.field_key, x.column_name, x.column_type, x.required,
       x.enum_values, x.ref_table, x.ref_code_col, x.ref_name_col, x.max_length,
       x.decimal_scale, x.sort_order, x.remark
FROM `md_io_template` t
JOIN (
SELECT 'customerCode' field_key,'客户代码' column_name,'STRING' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,32 max_length,NULL decimal_scale,1 sort_order,'十位客户编码，必填' remark
UNION ALL SELECT 'customerName' field_key,'客户名称' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,128 max_length,NULL decimal_scale,2 sort_order,NULL remark
UNION ALL SELECT 'materialName' field_key,'物料名称' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,200 max_length,NULL decimal_scale,3 sort_order,NULL remark
UNION ALL SELECT 'spec' field_key,'型号规格' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,128 max_length,NULL decimal_scale,4 sort_order,NULL remark
UNION ALL SELECT 'quantity' field_key,'数量' column_name,'NUMBER' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,4 decimal_scale,5 sort_order,NULL remark
UNION ALL SELECT 'unitPrice' field_key,'单价' column_name,'NUMBER' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,6 decimal_scale,6 sort_order,'含税单价' remark
UNION ALL SELECT 'amount' field_key,'金额' column_name,'NUMBER' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,7 sort_order,'不含税金额' remark
UNION ALL SELECT 'taxAmount' field_key,'税额' column_name,'NUMBER' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,8 sort_order,NULL remark
UNION ALL SELECT 'totalAmount' field_key,'价税合计' column_name,'NUMBER' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,9 sort_order,NULL remark
UNION ALL SELECT 'poOrDeliveryNo' field_key,'PO号或交货单号' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,64 max_length,NULL decimal_scale,10 sort_order,NULL remark
UNION ALL SELECT 'preInvoiceFlag' field_key,'是否提前开票' column_name,'ENUM' column_type,0 required,'是,否' enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,NULL decimal_scale,11 sort_order,'1=提前开票' remark
UNION ALL SELECT 'remark' field_key,'备注' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,500 max_length,NULL decimal_scale,12 sort_order,NULL remark
) x
WHERE t.`template_code`='IO_AR_STATEMENT' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_io_template_field` f
                  WHERE f.template_id=t.id AND f.field_key=x.field_key);


-- 字段定义：IO_AR_RECEIPT（10 列）

INSERT INTO `md_io_template_field`
  (`tenant_id`,`template_id`,`field_key`,`column_name`,`column_type`,`required`,
   `enum_values`,`ref_table`,`ref_code_col`,`ref_name_col`,`max_length`,
   `decimal_scale`,`sort_order`,`remark`)
SELECT 1, t.id, x.field_key, x.column_name, x.column_type, x.required,
       x.enum_values, x.ref_table, x.ref_code_col, x.ref_name_col, x.max_length,
       x.decimal_scale, x.sort_order, x.remark
FROM `md_io_template` t
JOIN (
SELECT 'billNo' field_key,'收款单号' column_name,'STRING' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,64 max_length,NULL decimal_scale,1 sort_order,'收款单号，必填' remark
UNION ALL SELECT 'receiptDate' field_key,'收款日期' column_name,'DATE' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,NULL decimal_scale,2 sort_order,NULL remark
UNION ALL SELECT 'customerCode' field_key,'客户编码' column_name,'STRING' column_type,0 required,NULL enum_values,'crm_customer' ref_table,'customer_code' ref_code_col,'customer_name' ref_name_col,32 max_length,NULL decimal_scale,3 sort_order,'按编码反查客户ID' remark
UNION ALL SELECT 'customerName' field_key,'客户名称' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,128 max_length,NULL decimal_scale,4 sort_order,NULL remark
UNION ALL SELECT 'companyCode' field_key,'OU' column_name,'STRING' column_type,0 required,NULL enum_values,'sys_company' ref_table,'company_code' ref_code_col,'company_name' ref_name_col,32 max_length,NULL decimal_scale,5 sort_order,'核算组织代码' remark
UNION ALL SELECT 'currencyCode' field_key,'币种' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,8 max_length,NULL decimal_scale,6 sort_order,'默认CNY' remark
UNION ALL SELECT 'amount' field_key,'收款金额' column_name,'NUMBER' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,7 sort_order,NULL remark
UNION ALL SELECT 'invoiceNo' field_key,'发票号' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,64 max_length,NULL decimal_scale,8 sort_order,NULL remark
UNION ALL SELECT 'offsetAmount' field_key,'核销金额' column_name,'NUMBER' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,9 sort_order,NULL remark
UNION ALL SELECT 'remark' field_key,'备注' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,500 max_length,NULL decimal_scale,10 sort_order,NULL remark
) x
WHERE t.`template_code`='IO_AR_RECEIPT' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_io_template_field` f
                  WHERE f.template_id=t.id AND f.field_key=x.field_key);


-- 字段定义：IO_AR_OFFSET（7 列）

INSERT INTO `md_io_template_field`
  (`tenant_id`,`template_id`,`field_key`,`column_name`,`column_type`,`required`,
   `enum_values`,`ref_table`,`ref_code_col`,`ref_name_col`,`max_length`,
   `decimal_scale`,`sort_order`,`remark`)
SELECT 1, t.id, x.field_key, x.column_name, x.column_type, x.required,
       x.enum_values, x.ref_table, x.ref_code_col, x.ref_name_col, x.max_length,
       x.decimal_scale, x.sort_order, x.remark
FROM `md_io_template` t
JOIN (
SELECT 'seqNo' field_key,'序号' column_name,'NUMBER' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,0 decimal_scale,1 sort_order,'行序号，仅展示' remark
UNION ALL SELECT 'debitSubject' field_key,'借方科目' column_name,'STRING' column_type,1 required,NULL enum_values,'md_subject' ref_table,'subject_code' ref_code_col,'subject_name' ref_name_col,64 max_length,NULL decimal_scale,2 sort_order,'按科目编码反查ID' remark
UNION ALL SELECT 'debitAmount' field_key,'借方金额' column_name,'NUMBER' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,3 sort_order,NULL remark
UNION ALL SELECT 'creditSubject' field_key,'贷方科目' column_name,'STRING' column_type,1 required,NULL enum_values,'md_subject' ref_table,'subject_code' ref_code_col,'subject_name' ref_name_col,64 max_length,NULL decimal_scale,4 sort_order,NULL remark
UNION ALL SELECT 'creditAmount' field_key,'贷方金额' column_name,'NUMBER' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,5 sort_order,NULL remark
UNION ALL SELECT 'summary' field_key,'摘要' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,500 max_length,NULL decimal_scale,6 sort_order,NULL remark
UNION ALL SELECT 'partnerCode' field_key,'往来对象编码' column_name,'STRING' column_type,0 required,NULL enum_values,'crm_customer' ref_table,'customer_code' ref_code_col,'customer_name' ref_name_col,48 max_length,NULL decimal_scale,7 sort_order,NULL remark
) x
WHERE t.`template_code`='IO_AR_OFFSET' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_io_template_field` f
                  WHERE f.template_id=t.id AND f.field_key=x.field_key);


-- 字段定义：IO_AP_PLAN（11 列）

INSERT INTO `md_io_template_field`
  (`tenant_id`,`template_id`,`field_key`,`column_name`,`column_type`,`required`,
   `enum_values`,`ref_table`,`ref_code_col`,`ref_name_col`,`max_length`,
   `decimal_scale`,`sort_order`,`remark`)
SELECT 1, t.id, x.field_key, x.column_name, x.column_type, x.required,
       x.enum_values, x.ref_table, x.ref_code_col, x.ref_name_col, x.max_length,
       x.decimal_scale, x.sort_order, x.remark
FROM `md_io_template` t
JOIN (
SELECT 'invoiceNo' field_key,'发票编号' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,255 max_length,NULL decimal_scale,1 sort_order,'XXXXXX01-10/逗号非连续' remark
UNION ALL SELECT 'sbu' field_key,'事业部' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,64 max_length,NULL decimal_scale,2 sort_order,NULL remark
UNION ALL SELECT 'supplierName' field_key,'供应商名称' column_name,'STRING' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,200 max_length,NULL decimal_scale,3 sort_order,'必填' remark
UNION ALL SELECT 'paymentWay' field_key,'付款方法' column_name,'ENUM' column_type,0 required,'支票,银行转账,应付票据' enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,24 max_length,NULL decimal_scale,4 sort_order,NULL remark
UNION ALL SELECT 'bankAccount' field_key,'银行账户' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,64 max_length,NULL decimal_scale,5 sort_order,NULL remark
UNION ALL SELECT 'paymentDate' field_key,'付款日期' column_name,'DATE' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,NULL decimal_scale,6 sort_order,NULL remark
UNION ALL SELECT 'rebateAmount' field_key,'折扣额' column_name,'NUMBER' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,7 sort_order,'默认0' remark
UNION ALL SELECT 'expireDate' field_key,'汇票到期日期' column_name,'DATE' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,NULL decimal_scale,8 sort_order,'应付票据必填' remark
UNION ALL SELECT 'paymentAmount' field_key,'付款额' column_name,'NUMBER' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,9 sort_order,'必填' remark
UNION ALL SELECT 'poNo' field_key,'PO编号' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,64 max_length,NULL decimal_scale,10 sort_order,'预付款必填' remark
UNION ALL SELECT 'remark' field_key,'备注' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,500 max_length,NULL decimal_scale,11 sort_order,NULL remark
) x
WHERE t.`template_code`='IO_AP_PLAN' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_io_template_field` f
                  WHERE f.template_id=t.id AND f.field_key=x.field_key);


-- 字段定义：IO_BUDGET_ITEM（13 列）

INSERT INTO `md_io_template_field`
  (`tenant_id`,`template_id`,`field_key`,`column_name`,`column_type`,`required`,
   `enum_values`,`ref_table`,`ref_code_col`,`ref_name_col`,`max_length`,
   `decimal_scale`,`sort_order`,`remark`)
SELECT 1, t.id, x.field_key, x.column_name, x.column_type, x.required,
       x.enum_values, x.ref_table, x.ref_code_col, x.ref_name_col, x.max_length,
       x.decimal_scale, x.sort_order, x.remark
FROM `md_io_template` t
JOIN (
SELECT 'budgetYear' field_key,'预算年度' column_name,'NUMBER' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,0 decimal_scale,1 sort_order,'yyyy' remark
UNION ALL SELECT 'period' field_key,'期间' column_name,'STRING' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,NULL decimal_scale,2 sort_order,'yyyy-MM 或 yyyyMM' remark
UNION ALL SELECT 'companyCode' field_key,'公司编码' column_name,'STRING' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,32 max_length,NULL decimal_scale,3 sort_order,NULL remark
UNION ALL SELECT 'companyName' field_key,'公司名称' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,128 max_length,NULL decimal_scale,4 sort_order,NULL remark
UNION ALL SELECT 'deptCode' field_key,'部门编码' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,32 max_length,NULL decimal_scale,5 sort_order,NULL remark
UNION ALL SELECT 'deptName' field_key,'部门名称' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,128 max_length,NULL decimal_scale,6 sort_order,NULL remark
UNION ALL SELECT 'itemCode' field_key,'费用项编码' column_name,'STRING' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,32 max_length,NULL decimal_scale,7 sort_order,NULL remark
UNION ALL SELECT 'itemName' field_key,'费用项名称' column_name,'STRING' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,128 max_length,NULL decimal_scale,8 sort_order,NULL remark
UNION ALL SELECT 'budgetAmount' field_key,'预算总额' column_name,'NUMBER' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,9 sort_order,NULL remark
UNION ALL SELECT 'currencyCode' field_key,'币种' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,8 max_length,NULL decimal_scale,10 sort_order,NULL remark
UNION ALL SELECT 'ncSubject' field_key,'NC科目' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,64 max_length,NULL decimal_scale,11 sort_order,NULL remark
UNION ALL SELECT 'ifsSubject' field_key,'IFS科目' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,64 max_length,NULL decimal_scale,12 sort_order,NULL remark
UNION ALL SELECT 'remark' field_key,'备注' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,500 max_length,NULL decimal_scale,13 sort_order,NULL remark
) x
WHERE t.`template_code`='IO_BUDGET_ITEM' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_io_template_field` f
                  WHERE f.template_id=t.id AND f.field_key=x.field_key);


-- 字段定义：IO_RC_BAD_DEBT（9 列）

INSERT INTO `md_io_template_field`
  (`tenant_id`,`template_id`,`field_key`,`column_name`,`column_type`,`required`,
   `enum_values`,`ref_table`,`ref_code_col`,`ref_name_col`,`max_length`,
   `decimal_scale`,`sort_order`,`remark`)
SELECT 1, t.id, x.field_key, x.column_name, x.column_type, x.required,
       x.enum_values, x.ref_table, x.ref_code_col, x.ref_name_col, x.max_length,
       x.decimal_scale, x.sort_order, x.remark
FROM `md_io_template` t
JOIN (
SELECT 'customerCode' field_key,'客户编码' column_name,'STRING' column_type,1 required,NULL enum_values,'crm_customer' ref_table,'customer_code' ref_code_col,'customer_name' ref_name_col,32 max_length,NULL decimal_scale,1 sort_order,'必填，按编码反查客户' remark
UNION ALL SELECT 'customerName' field_key,'客户名称' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,128 max_length,NULL decimal_scale,2 sort_order,NULL remark
UNION ALL SELECT 'invoiceOrReceivableNo' field_key,'发票号或应收单号' column_name,'STRING' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,64 max_length,NULL decimal_scale,3 sort_order,NULL remark
UNION ALL SELECT 'remainAmount' field_key,'应收余额' column_name,'NUMBER' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,4 sort_order,NULL remark
UNION ALL SELECT 'provisionedAmount' field_key,'已计提坏账准备' column_name,'NUMBER' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,5 sort_order,NULL remark
UNION ALL SELECT 'offsetAmount' field_key,'本次核销金额' column_name,'NUMBER' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,6 sort_order,NULL remark
UNION ALL SELECT 'currencyCode' field_key,'币种' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,8 max_length,NULL decimal_scale,7 sort_order,NULL remark
UNION ALL SELECT 'offsetReason' field_key,'核销原因' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,500 max_length,NULL decimal_scale,8 sort_order,NULL remark
UNION ALL SELECT 'evidenceNo' field_key,'坏账认定依据编号' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,64 max_length,NULL decimal_scale,9 sort_order,NULL remark
) x
WHERE t.`template_code`='IO_RC_BAD_DEBT' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_io_template_field` f
                  WHERE f.template_id=t.id AND f.field_key=x.field_key);


-- 字段定义：IO_AR_STATEMENT_OUT（12 列）

INSERT INTO `md_io_template_field`
  (`tenant_id`,`template_id`,`field_key`,`column_name`,`column_type`,`required`,
   `enum_values`,`ref_table`,`ref_code_col`,`ref_name_col`,`max_length`,
   `decimal_scale`,`sort_order`,`remark`)
SELECT 1, t.id, x.field_key, x.column_name, x.column_type, x.required,
       x.enum_values, x.ref_table, x.ref_code_col, x.ref_name_col, x.max_length,
       x.decimal_scale, x.sort_order, x.remark
FROM `md_io_template` t
JOIN (
SELECT 'customerCode' field_key,'客户代码' column_name,'STRING' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,32 max_length,NULL decimal_scale,1 sort_order,'十位客户编码，必填' remark
UNION ALL SELECT 'customerName' field_key,'客户名称' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,128 max_length,NULL decimal_scale,2 sort_order,NULL remark
UNION ALL SELECT 'materialName' field_key,'物料名称' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,200 max_length,NULL decimal_scale,3 sort_order,NULL remark
UNION ALL SELECT 'spec' field_key,'型号规格' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,128 max_length,NULL decimal_scale,4 sort_order,NULL remark
UNION ALL SELECT 'quantity' field_key,'数量' column_name,'NUMBER' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,4 decimal_scale,5 sort_order,NULL remark
UNION ALL SELECT 'unitPrice' field_key,'单价' column_name,'NUMBER' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,6 decimal_scale,6 sort_order,'含税单价' remark
UNION ALL SELECT 'amount' field_key,'金额' column_name,'NUMBER' column_type,1 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,7 sort_order,'不含税金额' remark
UNION ALL SELECT 'taxAmount' field_key,'税额' column_name,'NUMBER' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,8 sort_order,NULL remark
UNION ALL SELECT 'totalAmount' field_key,'价税合计' column_name,'NUMBER' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,2 decimal_scale,9 sort_order,NULL remark
UNION ALL SELECT 'poOrDeliveryNo' field_key,'PO号或交货单号' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,64 max_length,NULL decimal_scale,10 sort_order,NULL remark
UNION ALL SELECT 'preInvoiceFlag' field_key,'是否提前开票' column_name,'ENUM' column_type,0 required,'是,否' enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,NULL max_length,NULL decimal_scale,11 sort_order,'1=提前开票' remark
UNION ALL SELECT 'remark' field_key,'备注' column_name,'STRING' column_type,0 required,NULL enum_values,NULL ref_table,NULL ref_code_col,NULL ref_name_col,500 max_length,NULL decimal_scale,12 sort_order,NULL remark
) x
WHERE t.`template_code`='IO_AR_STATEMENT_OUT' AND t.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_io_template_field` f
                  WHERE f.template_id=t.id AND f.field_key=x.field_key);
