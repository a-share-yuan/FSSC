-- =============================================================================
-- V1.4.86 表单字段元数据 seed（由表结构反射生成）
-- =============================================================================
-- 本文件由 scripts/gen_form_meta_seed.py 生成，请勿手工编辑。
-- 生成口径：从 information_schema 读真实列（含注释/类型/长度/精度），
--           机械推导控件类型与必录属性 ⇒ 元数据与物理表逐字对得上。
-- 闭环差距①：新增一种单据的字段 = 插数据，不改代码。
-- 幂等：NOT EXISTS 护栏。回滚：V1.4.86_seed_form_meta_rollback.sql
-- =============================================================================


-- 字段元数据：12 个表单 / 612 个字段

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'billNo' field_key,'单据编号' field_label,'bill_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,1 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='billNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'deliveryStatus' field_key,'实物签收状态' field_label,'delivery_status' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,2 sort_order,'实物签收状态' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='deliveryStatus');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'rejectType' field_key,'驳回类型' field_label,'reject_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,3 sort_order,'驳回类型' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='rejectType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'billType' field_key,'单据类型' field_label,'bill_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,4 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='billType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'billSubType' field_key,'单据子类' field_label,'bill_sub_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,5 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='billSubType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'companyId' field_key,'company_id' field_label,'company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,6 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='companyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'settleCompanyId' field_key,'结算' field_label,'settle_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,7 sort_order,'结算(报销)单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='settleCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'payCompanyId' field_key,'支付单位' field_label,'pay_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,8 sort_order,'支付单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='payCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'loanCompanyId' field_key,'借款单位' field_label,'loan_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,9 sort_order,'借款单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='loanCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'deptId' field_key,'dept_id' field_label,'dept_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,10 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='deptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'costCenterId' field_key,'成本中心ID' field_label,'cost_center_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,11 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='costCenterId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'projectId' field_key,'项目ID' field_label,'project_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,12 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='projectId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'applyUserId' field_key,'申请人用户ID' field_label,'apply_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,13 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='applyUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'applyUserName' field_key,'申请人' field_label,'apply_user_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,14 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='applyUserName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'agentUserId' field_key,'代填人用户ID' field_label,'agent_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,15 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='agentUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'receiverUserId' field_key,'收款人' field_label,'receiver_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,16 sort_order,'收款人' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='receiverUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'applyDate' field_key,'申请日期' field_label,'apply_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,17 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='applyDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'periodYear' field_key,'归属会计年度' field_label,'period_year' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,18 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='periodYear');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'periodMonth' field_key,'归属会计月份' field_label,'period_month' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,19 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='periodMonth');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'currencyCode' field_key,'单据币种' field_label,'currency_code' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,20 sort_order,NULL placeholder,
       NULL help_text,'CNY' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='currencyCode');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'exchangeRate' field_key,'汇率' field_label,'exchange_rate' db_column,NULL section,
       'PERCENT' widget_type,'FULL' control_width,12 span,21 sort_order,NULL placeholder,
       NULL help_text,'1.0000000000' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='exchangeRate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'priceType' field_key,'录入口径' field_label,'price_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,22 sort_order,'录入口径' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='priceType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'totalAmount' field_key,'申请金额' field_label,'total_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,23 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='totalAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'amountCn' field_key,'金额大写' field_label,'amount_cn' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,24 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='amountCn');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'taxAmount' field_key,'税额合计' field_label,'tax_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,25 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='taxAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'netAmount' field_key,'不含税金额合计' field_label,'net_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,26 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='netAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'approvedAmount' field_key,'审批核定金额' field_label,'approved_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,27 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='approvedAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'approvedPayAmount' field_key,'核定付款金额' field_label,'approved_pay_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,28 sort_order,'核定付款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='approvedPayAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'approvedRefundAmount' field_key,'核定退款金额' field_label,'approved_refund_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,29 sort_order,'核定退款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='approvedRefundAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'offsetAmount' field_key,'冲抵金额' field_label,'offset_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,30 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='offsetAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'payableAmount' field_key,'应付金额 = 核定 - 冲抵' field_label,'payable_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,31 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='payableAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'paidAmount' field_key,'已付金额' field_label,'paid_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,32 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='paidAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'loanBalance' field_key,'借款未还余额' field_label,'loan_balance' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,33 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='loanBalance');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'expectRepayDate' field_key,'预计还款日期' field_label,'expect_repay_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,34 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='expectRepayDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'status' field_key,'单据状态' field_label,'status' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,35 sort_order,NULL placeholder,
       NULL help_text,'DRAFT' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='status');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'submitTime' field_key,'提交时间' field_label,'submit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,36 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='submitTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'approveTime' field_key,'审批通过时间' field_label,'approve_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,37 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='approveTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'payTime' field_key,'支付完成时间' field_label,'pay_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,38 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='payTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'auditUserId' field_key,'共享审核人用户ID' field_label,'audit_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,39 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='auditUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'auditTime' field_key,'共享审核时间' field_label,'audit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,40 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='auditTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'auditRemark' field_key,'审核意见' field_label,'audit_remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,41 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='auditRemark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'voucherNo' field_key,'会计凭证号' field_label,'voucher_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,42 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='voucherNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'sourceBillId' field_key,'来源单据ID' field_label,'source_bill_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,43 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='sourceBillId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'remark' field_key,'备注' field_label,'remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,44 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='remark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'contractId' field_key,'关联合同ID' field_label,'contract_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,45 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='contractId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'poReceiptId' field_key,'关联收货单ID' field_label,'po_receipt_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,46 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='poReceiptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'poId' field_key,'关联采购订单ID' field_label,'po_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,47 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='poId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'customerId' field_key,'关联往来单位ID' field_label,'customer_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,48 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='customerId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'payeeName' field_key,'收款方名称' field_label,'payee_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,49 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='payeeName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'payTarget' field_key,'收款对象' field_label,'pay_target' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,50 sort_order,'收款对象' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='payTarget');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'payeeAccountNo' field_key,'收款账号' field_label,'payee_account_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,51 sort_order,'收款账号' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='payeeAccountNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'billNo' field_key,'单据编号' field_label,'bill_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,1 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='billNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'deliveryStatus' field_key,'实物签收状态' field_label,'delivery_status' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,2 sort_order,'实物签收状态' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='deliveryStatus');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'rejectType' field_key,'驳回类型' field_label,'reject_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,3 sort_order,'驳回类型' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='rejectType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'billType' field_key,'单据类型' field_label,'bill_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,4 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='billType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'billSubType' field_key,'单据子类' field_label,'bill_sub_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,5 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='billSubType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'companyId' field_key,'company_id' field_label,'company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,6 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='companyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'settleCompanyId' field_key,'结算' field_label,'settle_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,7 sort_order,'结算(报销)单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='settleCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'payCompanyId' field_key,'支付单位' field_label,'pay_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,8 sort_order,'支付单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='payCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'loanCompanyId' field_key,'借款单位' field_label,'loan_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,9 sort_order,'借款单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='loanCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'deptId' field_key,'dept_id' field_label,'dept_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,10 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='deptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'costCenterId' field_key,'成本中心ID' field_label,'cost_center_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,11 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='costCenterId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'projectId' field_key,'项目ID' field_label,'project_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,12 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='projectId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'applyUserId' field_key,'申请人用户ID' field_label,'apply_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,13 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='applyUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'applyUserName' field_key,'申请人' field_label,'apply_user_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,14 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='applyUserName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'agentUserId' field_key,'代填人用户ID' field_label,'agent_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,15 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='agentUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'receiverUserId' field_key,'收款人' field_label,'receiver_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,16 sort_order,'收款人' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='receiverUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'applyDate' field_key,'申请日期' field_label,'apply_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,17 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='applyDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'periodYear' field_key,'归属会计年度' field_label,'period_year' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,18 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='periodYear');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'periodMonth' field_key,'归属会计月份' field_label,'period_month' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,19 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='periodMonth');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'currencyCode' field_key,'单据币种' field_label,'currency_code' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,20 sort_order,NULL placeholder,
       NULL help_text,'CNY' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='currencyCode');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'exchangeRate' field_key,'汇率' field_label,'exchange_rate' db_column,NULL section,
       'PERCENT' widget_type,'FULL' control_width,12 span,21 sort_order,NULL placeholder,
       NULL help_text,'1.0000000000' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='exchangeRate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'priceType' field_key,'录入口径' field_label,'price_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,22 sort_order,'录入口径' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='priceType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'totalAmount' field_key,'预计金额' field_label,'total_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,23 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='totalAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'amountCn' field_key,'金额大写' field_label,'amount_cn' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,24 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='amountCn');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'taxAmount' field_key,'税额合计' field_label,'tax_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,25 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='taxAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'netAmount' field_key,'不含税金额合计' field_label,'net_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,26 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='netAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'approvedAmount' field_key,'审批核定金额' field_label,'approved_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,27 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='approvedAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'approvedPayAmount' field_key,'核定付款金额' field_label,'approved_pay_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,28 sort_order,'核定付款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='approvedPayAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'approvedRefundAmount' field_key,'核定退款金额' field_label,'approved_refund_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,29 sort_order,'核定退款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='approvedRefundAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'offsetAmount' field_key,'冲抵金额' field_label,'offset_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,30 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='offsetAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'payableAmount' field_key,'应付金额 = 核定 - 冲抵' field_label,'payable_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,31 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='payableAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'paidAmount' field_key,'已付金额' field_label,'paid_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,32 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='paidAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'loanBalance' field_key,'借款未还余额' field_label,'loan_balance' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,33 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='loanBalance');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'expectRepayDate' field_key,'预计还款日期' field_label,'expect_repay_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,34 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='expectRepayDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'status' field_key,'单据状态' field_label,'status' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,35 sort_order,NULL placeholder,
       NULL help_text,'DRAFT' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='status');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'submitTime' field_key,'提交时间' field_label,'submit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,36 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='submitTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'approveTime' field_key,'审批通过时间' field_label,'approve_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,37 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='approveTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'payTime' field_key,'支付完成时间' field_label,'pay_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,38 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='payTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'auditUserId' field_key,'共享审核人用户ID' field_label,'audit_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,39 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='auditUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'auditTime' field_key,'共享审核时间' field_label,'audit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,40 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='auditTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'auditRemark' field_key,'审核意见' field_label,'audit_remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,41 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='auditRemark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'voucherNo' field_key,'会计凭证号' field_label,'voucher_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,42 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='voucherNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'sourceBillId' field_key,'来源单据ID' field_label,'source_bill_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,43 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='sourceBillId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'remark' field_key,'备注' field_label,'remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,44 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='remark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'contractId' field_key,'关联合同ID' field_label,'contract_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,45 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='contractId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'poReceiptId' field_key,'关联收货单ID' field_label,'po_receipt_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,46 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='poReceiptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'poId' field_key,'关联采购订单ID' field_label,'po_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,47 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='poId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'customerId' field_key,'关联往来单位ID' field_label,'customer_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,48 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='customerId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'payeeName' field_key,'收款方名称' field_label,'payee_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,49 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='payeeName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'payTarget' field_key,'收款对象' field_label,'pay_target' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,50 sort_order,'收款对象' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='payTarget');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'payeeAccountNo' field_key,'收款账号' field_label,'payee_account_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,51 sort_order,'收款账号' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='payeeAccountNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'billNo' field_key,'单据编号' field_label,'bill_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,1 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='billNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'deliveryStatus' field_key,'实物签收状态' field_label,'delivery_status' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,2 sort_order,'实物签收状态' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='deliveryStatus');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'rejectType' field_key,'驳回类型' field_label,'reject_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,3 sort_order,'驳回类型' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='rejectType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'billType' field_key,'单据类型' field_label,'bill_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,4 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='billType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'billSubType' field_key,'单据子类' field_label,'bill_sub_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,5 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='billSubType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'companyId' field_key,'company_id' field_label,'company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,6 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='companyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'settleCompanyId' field_key,'结算' field_label,'settle_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,7 sort_order,'结算(报销)单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='settleCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'payCompanyId' field_key,'支付单位' field_label,'pay_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,8 sort_order,'支付单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='payCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'loanCompanyId' field_key,'借款单位' field_label,'loan_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,9 sort_order,'借款单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='loanCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'deptId' field_key,'dept_id' field_label,'dept_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,10 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='deptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'costCenterId' field_key,'成本中心ID' field_label,'cost_center_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,11 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='costCenterId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'projectId' field_key,'项目ID' field_label,'project_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,12 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='projectId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'applyUserId' field_key,'申请人用户ID' field_label,'apply_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,13 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='applyUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'applyUserName' field_key,'申请人姓名' field_label,'apply_user_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,14 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='applyUserName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'agentUserId' field_key,'代填人用户ID' field_label,'agent_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,15 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='agentUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'receiverUserId' field_key,'收款人' field_label,'receiver_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,16 sort_order,'收款人' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='receiverUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'applyDate' field_key,'申请日期' field_label,'apply_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,17 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='applyDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'periodYear' field_key,'归属会计年度' field_label,'period_year' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,18 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='periodYear');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'periodMonth' field_key,'归属会计月份' field_label,'period_month' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,19 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='periodMonth');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'currencyCode' field_key,'单据币种' field_label,'currency_code' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,20 sort_order,NULL placeholder,
       NULL help_text,'CNY' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='currencyCode');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'exchangeRate' field_key,'汇率' field_label,'exchange_rate' db_column,NULL section,
       'PERCENT' widget_type,'FULL' control_width,12 span,21 sort_order,NULL placeholder,
       NULL help_text,'1.0000000000' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='exchangeRate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'priceType' field_key,'录入口径' field_label,'price_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,22 sort_order,'录入口径' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='priceType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'totalAmount' field_key,'预计金额' field_label,'total_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,23 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='totalAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'amountCn' field_key,'金额大写' field_label,'amount_cn' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,24 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='amountCn');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'taxAmount' field_key,'税额合计' field_label,'tax_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,25 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='taxAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'netAmount' field_key,'不含税金额合计' field_label,'net_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,26 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='netAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'approvedAmount' field_key,'审批核定金额' field_label,'approved_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,27 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='approvedAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'approvedPayAmount' field_key,'核定付款金额' field_label,'approved_pay_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,28 sort_order,'核定付款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='approvedPayAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'approvedRefundAmount' field_key,'核定退款金额' field_label,'approved_refund_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,29 sort_order,'核定退款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='approvedRefundAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'offsetAmount' field_key,'冲抵金额' field_label,'offset_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,30 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='offsetAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'payableAmount' field_key,'应付金额 = 核定 - 冲抵' field_label,'payable_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,31 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='payableAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'paidAmount' field_key,'已付金额' field_label,'paid_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,32 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='paidAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'loanBalance' field_key,'借款未还余额' field_label,'loan_balance' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,33 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='loanBalance');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'expectRepayDate' field_key,'预计还款日期' field_label,'expect_repay_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,34 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='expectRepayDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'status' field_key,'单据状态' field_label,'status' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,35 sort_order,NULL placeholder,
       NULL help_text,'DRAFT' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='status');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'submitTime' field_key,'提交时间' field_label,'submit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,36 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='submitTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'approveTime' field_key,'审批通过时间' field_label,'approve_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,37 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='approveTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'payTime' field_key,'支付完成时间' field_label,'pay_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,38 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='payTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'auditUserId' field_key,'共享审核人用户ID' field_label,'audit_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,39 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='auditUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'auditTime' field_key,'共享审核时间' field_label,'audit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,40 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='auditTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'auditRemark' field_key,'审核意见' field_label,'audit_remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,41 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='auditRemark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'voucherNo' field_key,'会计凭证号' field_label,'voucher_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,42 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='voucherNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'sourceBillId' field_key,'来源单据ID' field_label,'source_bill_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,43 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='sourceBillId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'remark' field_key,'备注' field_label,'remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,44 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='remark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'contractId' field_key,'关联合同ID' field_label,'contract_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,45 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='contractId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'poReceiptId' field_key,'关联收货单ID' field_label,'po_receipt_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,46 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='poReceiptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'poId' field_key,'关联采购订单ID' field_label,'po_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,47 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='poId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'customerId' field_key,'关联往来单位ID' field_label,'customer_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,48 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='customerId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'payeeName' field_key,'收款方名称' field_label,'payee_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,49 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='payeeName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'payTarget' field_key,'收款对象' field_label,'pay_target' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,50 sort_order,'收款对象' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='payTarget');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'payeeAccountNo' field_key,'收款账号' field_label,'payee_account_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,51 sort_order,'收款账号' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='payeeAccountNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'billNo' field_key,'单据编号' field_label,'bill_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,1 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='billNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'deliveryStatus' field_key,'实物签收状态' field_label,'delivery_status' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,2 sort_order,'实物签收状态' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='deliveryStatus');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'rejectType' field_key,'驳回类型' field_label,'reject_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,3 sort_order,'驳回类型' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='rejectType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'billType' field_key,'单据类型' field_label,'bill_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,4 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='billType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'billSubType' field_key,'单据子类' field_label,'bill_sub_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,5 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='billSubType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'companyId' field_key,'company_id' field_label,'company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,6 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='companyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'settleCompanyId' field_key,'结算' field_label,'settle_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,7 sort_order,'结算(报销)单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='settleCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'payCompanyId' field_key,'支付单位' field_label,'pay_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,8 sort_order,'支付单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='payCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'loanCompanyId' field_key,'借款单位' field_label,'loan_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,9 sort_order,'借款单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='loanCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'deptId' field_key,'所属部门' field_label,'dept_id' db_column,NULL section,
       'DEPT_SELECT' widget_type,'FULL' control_width,12 span,10 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='deptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'costCenterId' field_key,'成本中心ID' field_label,'cost_center_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,11 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='costCenterId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'projectId' field_key,'项目ID' field_label,'project_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,12 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='projectId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'applyUserId' field_key,'申请人用户ID' field_label,'apply_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,13 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='applyUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'applyUserName' field_key,'报销人' field_label,'apply_user_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,14 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='applyUserName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'agentUserId' field_key,'代填人用户ID' field_label,'agent_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,15 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='agentUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'receiverUserId' field_key,'收款人' field_label,'receiver_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,16 sort_order,'收款人' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='receiverUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'applyDate' field_key,'申请日期' field_label,'apply_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,17 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='applyDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'periodYear' field_key,'归属会计年度' field_label,'period_year' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,18 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='periodYear');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'periodMonth' field_key,'归属会计月份' field_label,'period_month' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,19 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='periodMonth');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'currencyCode' field_key,'单据币种' field_label,'currency_code' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,20 sort_order,NULL placeholder,
       NULL help_text,'CNY' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='currencyCode');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'exchangeRate' field_key,'汇率' field_label,'exchange_rate' db_column,NULL section,
       'PERCENT' widget_type,'FULL' control_width,12 span,21 sort_order,NULL placeholder,
       NULL help_text,'1.0000000000' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='exchangeRate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'priceType' field_key,'录入口径' field_label,'price_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,22 sort_order,'录入口径' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='priceType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'totalAmount' field_key,'报销金额' field_label,'total_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,23 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='totalAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'amountCn' field_key,'金额大写' field_label,'amount_cn' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,24 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='amountCn');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'taxAmount' field_key,'税额合计' field_label,'tax_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,25 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='taxAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'netAmount' field_key,'不含税金额合计' field_label,'net_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,26 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='netAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'approvedAmount' field_key,'审批核定金额' field_label,'approved_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,27 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='approvedAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'approvedPayAmount' field_key,'核定付款金额' field_label,'approved_pay_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,28 sort_order,'核定付款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='approvedPayAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'approvedRefundAmount' field_key,'核定退款金额' field_label,'approved_refund_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,29 sort_order,'核定退款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='approvedRefundAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'offsetAmount' field_key,'冲抵金额' field_label,'offset_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,30 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='offsetAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'payableAmount' field_key,'应付金额 = 核定 - 冲抵' field_label,'payable_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,31 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='payableAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'paidAmount' field_key,'已付金额' field_label,'paid_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,32 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='paidAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'loanBalance' field_key,'借款未还余额' field_label,'loan_balance' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,33 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='loanBalance');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'expectRepayDate' field_key,'预计还款日期' field_label,'expect_repay_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,34 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='expectRepayDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'status' field_key,'单据状态' field_label,'status' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,35 sort_order,NULL placeholder,
       NULL help_text,'DRAFT' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='status');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'submitTime' field_key,'提交时间' field_label,'submit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,36 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='submitTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'approveTime' field_key,'审批通过时间' field_label,'approve_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,37 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='approveTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'payTime' field_key,'支付完成时间' field_label,'pay_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,38 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='payTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'auditUserId' field_key,'共享审核人用户ID' field_label,'audit_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,39 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='auditUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'auditTime' field_key,'共享审核时间' field_label,'audit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,40 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='auditTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'auditRemark' field_key,'审核意见' field_label,'audit_remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,41 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='auditRemark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'voucherNo' field_key,'会计凭证号' field_label,'voucher_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,42 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='voucherNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'sourceBillId' field_key,'来源单据ID' field_label,'source_bill_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,43 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='sourceBillId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'remark' field_key,'备注' field_label,'remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,44 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='remark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'contractId' field_key,'关联合同ID' field_label,'contract_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,45 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='contractId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'poReceiptId' field_key,'关联收货单ID' field_label,'po_receipt_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,46 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='poReceiptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'poId' field_key,'关联采购订单ID' field_label,'po_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,47 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='poId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'customerId' field_key,'关联往来单位ID' field_label,'customer_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,48 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='customerId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'payeeName' field_key,'收款方' field_label,'payee_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,49 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='payeeName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'payTarget' field_key,'收款对象' field_label,'pay_target' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,50 sort_order,'收款对象' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='payTarget');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'payeeAccountNo' field_key,'收款账号' field_label,'payee_account_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,51 sort_order,'收款账号' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='payeeAccountNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'billNo' field_key,'单据编号' field_label,'bill_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,1 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='billNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'deliveryStatus' field_key,'实物签收状态' field_label,'delivery_status' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,2 sort_order,'实物签收状态' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='deliveryStatus');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'rejectType' field_key,'驳回类型' field_label,'reject_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,3 sort_order,'驳回类型' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='rejectType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'billType' field_key,'单据类型' field_label,'bill_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,4 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='billType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'billSubType' field_key,'单据子类' field_label,'bill_sub_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,5 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='billSubType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'companyId' field_key,'company_id' field_label,'company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,6 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='companyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'settleCompanyId' field_key,'结算' field_label,'settle_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,7 sort_order,'结算(报销)单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='settleCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'payCompanyId' field_key,'支付单位' field_label,'pay_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,8 sort_order,'支付单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='payCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'loanCompanyId' field_key,'借款单位' field_label,'loan_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,9 sort_order,'借款单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='loanCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'deptId' field_key,'dept_id' field_label,'dept_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,10 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='deptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'costCenterId' field_key,'成本中心ID' field_label,'cost_center_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,11 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='costCenterId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'projectId' field_key,'项目ID' field_label,'project_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,12 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='projectId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'applyUserId' field_key,'申请人用户ID' field_label,'apply_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,13 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='applyUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'applyUserName' field_key,'申请人姓名' field_label,'apply_user_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,14 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='applyUserName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'agentUserId' field_key,'代填人用户ID' field_label,'agent_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,15 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='agentUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'receiverUserId' field_key,'收款人' field_label,'receiver_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,16 sort_order,'收款人' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='receiverUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'applyDate' field_key,'申请日期' field_label,'apply_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,17 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='applyDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'periodYear' field_key,'归属会计年度' field_label,'period_year' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,18 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='periodYear');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'periodMonth' field_key,'归属会计月份' field_label,'period_month' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,19 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='periodMonth');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'currencyCode' field_key,'单据币种' field_label,'currency_code' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,20 sort_order,NULL placeholder,
       NULL help_text,'CNY' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='currencyCode');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'exchangeRate' field_key,'汇率' field_label,'exchange_rate' db_column,NULL section,
       'PERCENT' widget_type,'FULL' control_width,12 span,21 sort_order,NULL placeholder,
       NULL help_text,'1.0000000000' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='exchangeRate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'priceType' field_key,'录入口径' field_label,'price_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,22 sort_order,'录入口径' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='priceType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'totalAmount' field_key,'报销金额' field_label,'total_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,23 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='totalAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'amountCn' field_key,'金额大写' field_label,'amount_cn' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,24 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='amountCn');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'taxAmount' field_key,'税额合计' field_label,'tax_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,25 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='taxAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'netAmount' field_key,'不含税金额合计' field_label,'net_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,26 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='netAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'approvedAmount' field_key,'审批核定金额' field_label,'approved_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,27 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='approvedAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'approvedPayAmount' field_key,'核定付款金额' field_label,'approved_pay_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,28 sort_order,'核定付款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='approvedPayAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'approvedRefundAmount' field_key,'核定退款金额' field_label,'approved_refund_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,29 sort_order,'核定退款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='approvedRefundAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'offsetAmount' field_key,'冲抵金额' field_label,'offset_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,30 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='offsetAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'payableAmount' field_key,'应付金额 = 核定 - 冲抵' field_label,'payable_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,31 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='payableAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'paidAmount' field_key,'已付金额' field_label,'paid_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,32 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='paidAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'loanBalance' field_key,'借款未还余额' field_label,'loan_balance' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,33 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='loanBalance');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'expectRepayDate' field_key,'预计还款日期' field_label,'expect_repay_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,34 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='expectRepayDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'status' field_key,'单据状态' field_label,'status' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,35 sort_order,NULL placeholder,
       NULL help_text,'DRAFT' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='status');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'submitTime' field_key,'提交时间' field_label,'submit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,36 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='submitTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'approveTime' field_key,'审批通过时间' field_label,'approve_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,37 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='approveTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'payTime' field_key,'支付完成时间' field_label,'pay_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,38 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='payTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'auditUserId' field_key,'共享审核人用户ID' field_label,'audit_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,39 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='auditUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'auditTime' field_key,'共享审核时间' field_label,'audit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,40 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='auditTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'auditRemark' field_key,'审核意见' field_label,'audit_remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,41 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='auditRemark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'voucherNo' field_key,'会计凭证号' field_label,'voucher_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,42 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='voucherNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'sourceBillId' field_key,'来源单据ID' field_label,'source_bill_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,43 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='sourceBillId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'remark' field_key,'备注' field_label,'remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,44 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='remark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'contractId' field_key,'关联合同ID' field_label,'contract_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,45 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='contractId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'poReceiptId' field_key,'关联收货单ID' field_label,'po_receipt_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,46 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='poReceiptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'poId' field_key,'关联采购订单ID' field_label,'po_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,47 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='poId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'customerId' field_key,'关联往来单位ID' field_label,'customer_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,48 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='customerId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'payeeName' field_key,'收款方' field_label,'payee_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,49 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='payeeName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'payTarget' field_key,'收款对象' field_label,'pay_target' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,50 sort_order,'收款对象' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='payTarget');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'payeeAccountNo' field_key,'收款账号' field_label,'payee_account_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,51 sort_order,'收款账号' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='payeeAccountNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'billNo' field_key,'单据编号' field_label,'bill_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,1 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='billNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'deliveryStatus' field_key,'实物签收状态' field_label,'delivery_status' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,2 sort_order,'实物签收状态' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='deliveryStatus');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'rejectType' field_key,'驳回类型' field_label,'reject_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,3 sort_order,'驳回类型' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='rejectType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'billType' field_key,'单据类型' field_label,'bill_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,4 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='billType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'billSubType' field_key,'单据子类' field_label,'bill_sub_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,5 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='billSubType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'companyId' field_key,'company_id' field_label,'company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,6 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='companyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'settleCompanyId' field_key,'结算' field_label,'settle_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,7 sort_order,'结算(报销)单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='settleCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'payCompanyId' field_key,'支付单位' field_label,'pay_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,8 sort_order,'支付单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='payCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'loanCompanyId' field_key,'借款单位' field_label,'loan_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,9 sort_order,'借款单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='loanCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'deptId' field_key,'dept_id' field_label,'dept_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,10 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='deptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'costCenterId' field_key,'成本中心ID' field_label,'cost_center_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,11 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='costCenterId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'projectId' field_key,'项目ID' field_label,'project_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,12 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='projectId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'applyUserId' field_key,'申请人用户ID' field_label,'apply_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,13 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='applyUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'applyUserName' field_key,'申请人姓名' field_label,'apply_user_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,14 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='applyUserName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'agentUserId' field_key,'代填人用户ID' field_label,'agent_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,15 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='agentUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'receiverUserId' field_key,'收款人' field_label,'receiver_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,16 sort_order,'收款人' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='receiverUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'applyDate' field_key,'申请日期' field_label,'apply_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,17 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='applyDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'periodYear' field_key,'归属会计年度' field_label,'period_year' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,18 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='periodYear');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'periodMonth' field_key,'归属会计月份' field_label,'period_month' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,19 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='periodMonth');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'currencyCode' field_key,'单据币种' field_label,'currency_code' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,20 sort_order,NULL placeholder,
       NULL help_text,'CNY' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='currencyCode');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'exchangeRate' field_key,'汇率' field_label,'exchange_rate' db_column,NULL section,
       'PERCENT' widget_type,'FULL' control_width,12 span,21 sort_order,NULL placeholder,
       NULL help_text,'1.0000000000' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='exchangeRate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'priceType' field_key,'录入口径' field_label,'price_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,22 sort_order,'录入口径' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='priceType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'totalAmount' field_key,'代付金额' field_label,'total_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,23 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='totalAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'amountCn' field_key,'金额大写' field_label,'amount_cn' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,24 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='amountCn');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'taxAmount' field_key,'税额合计' field_label,'tax_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,25 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='taxAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'netAmount' field_key,'不含税金额合计' field_label,'net_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,26 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='netAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'approvedAmount' field_key,'审批核定金额' field_label,'approved_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,27 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='approvedAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'approvedPayAmount' field_key,'核定付款金额' field_label,'approved_pay_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,28 sort_order,'核定付款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='approvedPayAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'approvedRefundAmount' field_key,'核定退款金额' field_label,'approved_refund_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,29 sort_order,'核定退款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='approvedRefundAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'offsetAmount' field_key,'冲抵金额' field_label,'offset_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,30 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='offsetAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'payableAmount' field_key,'应付金额 = 核定 - 冲抵' field_label,'payable_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,31 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='payableAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'paidAmount' field_key,'已付金额' field_label,'paid_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,32 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='paidAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'loanBalance' field_key,'借款未还余额' field_label,'loan_balance' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,33 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='loanBalance');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'expectRepayDate' field_key,'预计还款日期' field_label,'expect_repay_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,34 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='expectRepayDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'status' field_key,'单据状态' field_label,'status' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,35 sort_order,NULL placeholder,
       NULL help_text,'DRAFT' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='status');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'submitTime' field_key,'提交时间' field_label,'submit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,36 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='submitTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'approveTime' field_key,'审批通过时间' field_label,'approve_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,37 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='approveTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'payTime' field_key,'支付完成时间' field_label,'pay_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,38 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='payTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'auditUserId' field_key,'共享审核人用户ID' field_label,'audit_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,39 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='auditUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'auditTime' field_key,'共享审核时间' field_label,'audit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,40 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='auditTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'auditRemark' field_key,'审核意见' field_label,'audit_remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,41 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='auditRemark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'voucherNo' field_key,'会计凭证号' field_label,'voucher_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,42 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='voucherNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'sourceBillId' field_key,'来源单据ID' field_label,'source_bill_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,43 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='sourceBillId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'remark' field_key,'备注' field_label,'remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,44 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='remark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'contractId' field_key,'关联合同ID' field_label,'contract_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,45 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='contractId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'poReceiptId' field_key,'关联收货单ID' field_label,'po_receipt_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,46 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='poReceiptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'poId' field_key,'关联采购订单ID' field_label,'po_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,47 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='poId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'customerId' field_key,'关联往来单位ID' field_label,'customer_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,48 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='customerId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'payeeName' field_key,'收款方' field_label,'payee_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,49 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='payeeName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'payTarget' field_key,'收款对象' field_label,'pay_target' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,50 sort_order,'收款对象' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='payTarget');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'payeeAccountNo' field_key,'收款账号' field_label,'payee_account_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,51 sort_order,'收款账号' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='payeeAccountNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'billNo' field_key,'单据编号' field_label,'bill_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,1 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='billNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'deliveryStatus' field_key,'实物签收状态' field_label,'delivery_status' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,2 sort_order,'实物签收状态' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='deliveryStatus');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'rejectType' field_key,'驳回类型' field_label,'reject_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,3 sort_order,'驳回类型' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='rejectType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'billType' field_key,'单据类型' field_label,'bill_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,4 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='billType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'billSubType' field_key,'单据子类' field_label,'bill_sub_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,5 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='billSubType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'companyId' field_key,'company_id' field_label,'company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,6 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='companyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'settleCompanyId' field_key,'结算' field_label,'settle_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,7 sort_order,'结算(报销)单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='settleCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'payCompanyId' field_key,'支付单位' field_label,'pay_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,8 sort_order,'支付单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='payCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'loanCompanyId' field_key,'借款单位' field_label,'loan_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,9 sort_order,'借款单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='loanCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'deptId' field_key,'dept_id' field_label,'dept_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,10 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='deptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'costCenterId' field_key,'成本中心ID' field_label,'cost_center_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,11 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='costCenterId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'projectId' field_key,'项目ID' field_label,'project_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,12 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='projectId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'applyUserId' field_key,'申请人用户ID' field_label,'apply_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,13 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='applyUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'applyUserName' field_key,'借款人' field_label,'apply_user_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,14 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='applyUserName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'agentUserId' field_key,'代填人用户ID' field_label,'agent_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,15 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='agentUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'receiverUserId' field_key,'收款人' field_label,'receiver_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,16 sort_order,'收款人' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='receiverUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'applyDate' field_key,'申请日期' field_label,'apply_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,17 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='applyDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'periodYear' field_key,'归属会计年度' field_label,'period_year' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,18 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='periodYear');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'periodMonth' field_key,'归属会计月份' field_label,'period_month' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,19 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='periodMonth');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'currencyCode' field_key,'单据币种' field_label,'currency_code' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,20 sort_order,NULL placeholder,
       NULL help_text,'CNY' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='currencyCode');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'exchangeRate' field_key,'汇率' field_label,'exchange_rate' db_column,NULL section,
       'PERCENT' widget_type,'FULL' control_width,12 span,21 sort_order,NULL placeholder,
       NULL help_text,'1.0000000000' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='exchangeRate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'priceType' field_key,'录入口径' field_label,'price_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,22 sort_order,'录入口径' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='priceType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'totalAmount' field_key,'借款金额' field_label,'total_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,23 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='totalAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'amountCn' field_key,'金额大写' field_label,'amount_cn' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,24 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='amountCn');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'taxAmount' field_key,'税额合计' field_label,'tax_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,25 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='taxAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'netAmount' field_key,'不含税金额合计' field_label,'net_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,26 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='netAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'approvedAmount' field_key,'审批核定金额' field_label,'approved_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,27 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='approvedAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'approvedPayAmount' field_key,'核定付款金额' field_label,'approved_pay_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,28 sort_order,'核定付款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='approvedPayAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'approvedRefundAmount' field_key,'核定退款金额' field_label,'approved_refund_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,29 sort_order,'核定退款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='approvedRefundAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'offsetAmount' field_key,'冲抵金额' field_label,'offset_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,30 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='offsetAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'payableAmount' field_key,'应付金额 = 核定 - 冲抵' field_label,'payable_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,31 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='payableAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'paidAmount' field_key,'已付金额' field_label,'paid_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,32 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='paidAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'loanBalance' field_key,'借款未还余额' field_label,'loan_balance' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,33 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='loanBalance');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'expectRepayDate' field_key,'预计还款日' field_label,'expect_repay_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,34 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='expectRepayDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'status' field_key,'单据状态' field_label,'status' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,35 sort_order,NULL placeholder,
       NULL help_text,'DRAFT' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='status');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'submitTime' field_key,'提交时间' field_label,'submit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,36 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='submitTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'approveTime' field_key,'审批通过时间' field_label,'approve_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,37 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='approveTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'payTime' field_key,'支付完成时间' field_label,'pay_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,38 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='payTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'auditUserId' field_key,'共享审核人用户ID' field_label,'audit_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,39 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='auditUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'auditTime' field_key,'共享审核时间' field_label,'audit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,40 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='auditTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'auditRemark' field_key,'审核意见' field_label,'audit_remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,41 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='auditRemark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'voucherNo' field_key,'会计凭证号' field_label,'voucher_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,42 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='voucherNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'sourceBillId' field_key,'来源单据ID' field_label,'source_bill_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,43 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='sourceBillId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'remark' field_key,'备注' field_label,'remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,44 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='remark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'contractId' field_key,'关联合同ID' field_label,'contract_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,45 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='contractId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'poReceiptId' field_key,'关联收货单ID' field_label,'po_receipt_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,46 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='poReceiptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'poId' field_key,'关联采购订单ID' field_label,'po_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,47 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='poId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'customerId' field_key,'关联往来单位ID' field_label,'customer_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,48 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='customerId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'payeeName' field_key,'收款方名称' field_label,'payee_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,49 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='payeeName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'payTarget' field_key,'收款对象' field_label,'pay_target' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,50 sort_order,'收款对象' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='payTarget');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'payeeAccountNo' field_key,'收款账号' field_label,'payee_account_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,51 sort_order,'收款账号' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='payeeAccountNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'billNo' field_key,'单据编号' field_label,'bill_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,1 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='billNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'deliveryStatus' field_key,'实物签收状态' field_label,'delivery_status' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,2 sort_order,'实物签收状态' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='deliveryStatus');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'rejectType' field_key,'驳回类型' field_label,'reject_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,3 sort_order,'驳回类型' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='rejectType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'billType' field_key,'单据类型' field_label,'bill_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,4 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='billType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'billSubType' field_key,'单据子类' field_label,'bill_sub_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,5 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='billSubType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'companyId' field_key,'company_id' field_label,'company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,6 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='companyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'settleCompanyId' field_key,'结算' field_label,'settle_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,7 sort_order,'结算(报销)单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='settleCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'payCompanyId' field_key,'支付单位' field_label,'pay_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,8 sort_order,'支付单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='payCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'loanCompanyId' field_key,'借款单位' field_label,'loan_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,9 sort_order,'借款单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='loanCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'deptId' field_key,'dept_id' field_label,'dept_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,10 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='deptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'costCenterId' field_key,'成本中心ID' field_label,'cost_center_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,11 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='costCenterId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'projectId' field_key,'项目ID' field_label,'project_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,12 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='projectId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'applyUserId' field_key,'申请人用户ID' field_label,'apply_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,13 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='applyUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'applyUserName' field_key,'还款人' field_label,'apply_user_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,14 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='applyUserName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'agentUserId' field_key,'代填人用户ID' field_label,'agent_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,15 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='agentUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'receiverUserId' field_key,'收款人' field_label,'receiver_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,16 sort_order,'收款人' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='receiverUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'applyDate' field_key,'申请日期' field_label,'apply_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,17 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='applyDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'periodYear' field_key,'归属会计年度' field_label,'period_year' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,18 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='periodYear');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'periodMonth' field_key,'归属会计月份' field_label,'period_month' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,19 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='periodMonth');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'currencyCode' field_key,'单据币种' field_label,'currency_code' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,20 sort_order,NULL placeholder,
       NULL help_text,'CNY' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='currencyCode');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'exchangeRate' field_key,'汇率' field_label,'exchange_rate' db_column,NULL section,
       'PERCENT' widget_type,'FULL' control_width,12 span,21 sort_order,NULL placeholder,
       NULL help_text,'1.0000000000' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='exchangeRate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'priceType' field_key,'录入口径' field_label,'price_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,22 sort_order,'录入口径' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='priceType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'totalAmount' field_key,'还款金额' field_label,'total_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,23 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='totalAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'amountCn' field_key,'金额大写' field_label,'amount_cn' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,24 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='amountCn');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'taxAmount' field_key,'税额合计' field_label,'tax_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,25 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='taxAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'netAmount' field_key,'不含税金额合计' field_label,'net_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,26 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='netAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'approvedAmount' field_key,'审批核定金额' field_label,'approved_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,27 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='approvedAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'approvedPayAmount' field_key,'核定付款金额' field_label,'approved_pay_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,28 sort_order,'核定付款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='approvedPayAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'approvedRefundAmount' field_key,'核定退款金额' field_label,'approved_refund_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,29 sort_order,'核定退款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='approvedRefundAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'offsetAmount' field_key,'冲抵金额' field_label,'offset_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,30 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='offsetAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'payableAmount' field_key,'应付金额 = 核定 - 冲抵' field_label,'payable_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,31 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='payableAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'paidAmount' field_key,'已付金额' field_label,'paid_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,32 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='paidAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'loanBalance' field_key,'借款未还余额' field_label,'loan_balance' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,33 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='loanBalance');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'expectRepayDate' field_key,'预计还款日期' field_label,'expect_repay_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,34 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='expectRepayDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'status' field_key,'单据状态' field_label,'status' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,35 sort_order,NULL placeholder,
       NULL help_text,'DRAFT' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='status');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'submitTime' field_key,'提交时间' field_label,'submit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,36 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='submitTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'approveTime' field_key,'审批通过时间' field_label,'approve_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,37 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='approveTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'payTime' field_key,'支付完成时间' field_label,'pay_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,38 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='payTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'auditUserId' field_key,'共享审核人用户ID' field_label,'audit_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,39 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='auditUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'auditTime' field_key,'共享审核时间' field_label,'audit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,40 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='auditTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'auditRemark' field_key,'审核意见' field_label,'audit_remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,41 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='auditRemark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'voucherNo' field_key,'会计凭证号' field_label,'voucher_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,42 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='voucherNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'sourceBillId' field_key,'来源单据ID' field_label,'source_bill_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,43 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='sourceBillId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'remark' field_key,'备注' field_label,'remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,44 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='remark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'contractId' field_key,'关联合同ID' field_label,'contract_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,45 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='contractId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'poReceiptId' field_key,'关联收货单ID' field_label,'po_receipt_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,46 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='poReceiptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'poId' field_key,'关联采购订单ID' field_label,'po_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,47 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='poId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'customerId' field_key,'关联往来单位ID' field_label,'customer_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,48 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='customerId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'payeeName' field_key,'收款方' field_label,'payee_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,49 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='payeeName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'payTarget' field_key,'收款对象' field_label,'pay_target' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,50 sort_order,'收款对象' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='payTarget');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'payeeAccountNo' field_key,'收款账号' field_label,'payee_account_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,51 sort_order,'收款账号' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='payeeAccountNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'billNo' field_key,'单据编号' field_label,'bill_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,1 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='billNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'deliveryStatus' field_key,'实物签收状态' field_label,'delivery_status' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,2 sort_order,'实物签收状态' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='deliveryStatus');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'rejectType' field_key,'驳回类型' field_label,'reject_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,3 sort_order,'驳回类型' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='rejectType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'billType' field_key,'单据类型' field_label,'bill_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,4 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='billType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'billSubType' field_key,'单据子类' field_label,'bill_sub_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,5 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='billSubType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'companyId' field_key,'company_id' field_label,'company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,6 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='companyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'settleCompanyId' field_key,'结算' field_label,'settle_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,7 sort_order,'结算(报销)单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='settleCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'payCompanyId' field_key,'支付单位' field_label,'pay_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,8 sort_order,'支付单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='payCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'loanCompanyId' field_key,'借款单位' field_label,'loan_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,9 sort_order,'借款单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='loanCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'deptId' field_key,'dept_id' field_label,'dept_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,10 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='deptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'costCenterId' field_key,'成本中心ID' field_label,'cost_center_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,11 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='costCenterId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'projectId' field_key,'项目ID' field_label,'project_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,12 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='projectId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'applyUserId' field_key,'申请人用户ID' field_label,'apply_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,13 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='applyUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'applyUserName' field_key,'申请人姓名' field_label,'apply_user_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,14 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='applyUserName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'agentUserId' field_key,'代填人用户ID' field_label,'agent_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,15 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='agentUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'receiverUserId' field_key,'收款人' field_label,'receiver_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,16 sort_order,'收款人' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='receiverUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'applyDate' field_key,'申请日期' field_label,'apply_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,17 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='applyDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'periodYear' field_key,'归属会计年度' field_label,'period_year' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,18 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='periodYear');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'periodMonth' field_key,'归属会计月份' field_label,'period_month' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,19 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='periodMonth');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'currencyCode' field_key,'单据币种' field_label,'currency_code' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,20 sort_order,NULL placeholder,
       NULL help_text,'CNY' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='currencyCode');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'exchangeRate' field_key,'汇率' field_label,'exchange_rate' db_column,NULL section,
       'PERCENT' widget_type,'FULL' control_width,12 span,21 sort_order,NULL placeholder,
       NULL help_text,'1.0000000000' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='exchangeRate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'priceType' field_key,'录入口径' field_label,'price_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,22 sort_order,'录入口径' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='priceType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'totalAmount' field_key,'付款金额' field_label,'total_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,23 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='totalAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'amountCn' field_key,'金额大写' field_label,'amount_cn' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,24 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='amountCn');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'taxAmount' field_key,'税额合计' field_label,'tax_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,25 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='taxAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'netAmount' field_key,'不含税金额合计' field_label,'net_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,26 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='netAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'approvedAmount' field_key,'审批核定金额' field_label,'approved_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,27 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='approvedAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'approvedPayAmount' field_key,'核定付款金额' field_label,'approved_pay_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,28 sort_order,'核定付款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='approvedPayAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'approvedRefundAmount' field_key,'核定退款金额' field_label,'approved_refund_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,29 sort_order,'核定退款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='approvedRefundAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'offsetAmount' field_key,'冲抵金额' field_label,'offset_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,30 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='offsetAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'payableAmount' field_key,'应付金额 = 核定 - 冲抵' field_label,'payable_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,31 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='payableAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'paidAmount' field_key,'已付金额' field_label,'paid_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,32 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='paidAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'loanBalance' field_key,'借款未还余额' field_label,'loan_balance' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,33 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='loanBalance');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'expectRepayDate' field_key,'预计还款日期' field_label,'expect_repay_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,34 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='expectRepayDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'status' field_key,'单据状态' field_label,'status' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,35 sort_order,NULL placeholder,
       NULL help_text,'DRAFT' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='status');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'submitTime' field_key,'提交时间' field_label,'submit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,36 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='submitTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'approveTime' field_key,'审批通过时间' field_label,'approve_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,37 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='approveTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'payTime' field_key,'支付完成时间' field_label,'pay_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,38 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='payTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'auditUserId' field_key,'共享审核人用户ID' field_label,'audit_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,39 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='auditUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'auditTime' field_key,'共享审核时间' field_label,'audit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,40 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='auditTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'auditRemark' field_key,'审核意见' field_label,'audit_remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,41 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='auditRemark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'voucherNo' field_key,'会计凭证号' field_label,'voucher_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,42 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='voucherNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'sourceBillId' field_key,'来源单据ID' field_label,'source_bill_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,43 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='sourceBillId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'remark' field_key,'备注' field_label,'remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,44 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='remark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'contractId' field_key,'关联合同ID' field_label,'contract_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,45 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='contractId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'poReceiptId' field_key,'关联收货单ID' field_label,'po_receipt_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,46 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='poReceiptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'poId' field_key,'关联采购订单ID' field_label,'po_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,47 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='poId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'customerId' field_key,'关联往来单位ID' field_label,'customer_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,48 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='customerId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'payeeName' field_key,'收款方' field_label,'payee_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,49 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='payeeName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'payTarget' field_key,'收款对象' field_label,'pay_target' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,50 sort_order,'收款对象' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='payTarget');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'payeeAccountNo' field_key,'收款账号' field_label,'payee_account_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,51 sort_order,'收款账号' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='payeeAccountNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'billNo' field_key,'单据编号' field_label,'bill_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,1 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='billNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'deliveryStatus' field_key,'实物签收状态' field_label,'delivery_status' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,2 sort_order,'实物签收状态' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='deliveryStatus');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'rejectType' field_key,'驳回类型' field_label,'reject_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,3 sort_order,'驳回类型' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='rejectType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'billType' field_key,'单据类型' field_label,'bill_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,4 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='billType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'billSubType' field_key,'单据子类' field_label,'bill_sub_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,5 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='billSubType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'companyId' field_key,'company_id' field_label,'company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,6 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='companyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'settleCompanyId' field_key,'结算' field_label,'settle_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,7 sort_order,'结算(报销)单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='settleCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'payCompanyId' field_key,'支付单位' field_label,'pay_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,8 sort_order,'支付单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='payCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'loanCompanyId' field_key,'借款单位' field_label,'loan_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,9 sort_order,'借款单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='loanCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'deptId' field_key,'dept_id' field_label,'dept_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,10 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='deptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'costCenterId' field_key,'成本中心ID' field_label,'cost_center_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,11 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='costCenterId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'projectId' field_key,'项目ID' field_label,'project_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,12 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='projectId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'applyUserId' field_key,'申请人用户ID' field_label,'apply_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,13 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='applyUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'applyUserName' field_key,'申请人姓名' field_label,'apply_user_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,14 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='applyUserName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'agentUserId' field_key,'代填人用户ID' field_label,'agent_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,15 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='agentUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'receiverUserId' field_key,'收款人' field_label,'receiver_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,16 sort_order,'收款人' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='receiverUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'applyDate' field_key,'申请日期' field_label,'apply_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,17 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='applyDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'periodYear' field_key,'归属会计年度' field_label,'period_year' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,18 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='periodYear');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'periodMonth' field_key,'归属会计月份' field_label,'period_month' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,19 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='periodMonth');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'currencyCode' field_key,'单据币种' field_label,'currency_code' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,20 sort_order,NULL placeholder,
       NULL help_text,'CNY' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='currencyCode');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'exchangeRate' field_key,'汇率' field_label,'exchange_rate' db_column,NULL section,
       'PERCENT' widget_type,'FULL' control_width,12 span,21 sort_order,NULL placeholder,
       NULL help_text,'1.0000000000' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='exchangeRate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'priceType' field_key,'录入口径' field_label,'price_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,22 sort_order,'录入口径' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='priceType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'totalAmount' field_key,'预付金额' field_label,'total_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,23 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='totalAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'amountCn' field_key,'金额大写' field_label,'amount_cn' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,24 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='amountCn');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'taxAmount' field_key,'税额合计' field_label,'tax_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,25 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='taxAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'netAmount' field_key,'不含税金额合计' field_label,'net_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,26 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='netAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'approvedAmount' field_key,'审批核定金额' field_label,'approved_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,27 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='approvedAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'approvedPayAmount' field_key,'核定付款金额' field_label,'approved_pay_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,28 sort_order,'核定付款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='approvedPayAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'approvedRefundAmount' field_key,'核定退款金额' field_label,'approved_refund_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,29 sort_order,'核定退款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='approvedRefundAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'offsetAmount' field_key,'冲抵金额' field_label,'offset_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,30 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='offsetAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'payableAmount' field_key,'应付金额 = 核定 - 冲抵' field_label,'payable_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,31 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='payableAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'paidAmount' field_key,'已付金额' field_label,'paid_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,32 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='paidAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'loanBalance' field_key,'借款未还余额' field_label,'loan_balance' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,33 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='loanBalance');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'expectRepayDate' field_key,'预计还款日期' field_label,'expect_repay_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,34 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='expectRepayDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'status' field_key,'单据状态' field_label,'status' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,35 sort_order,NULL placeholder,
       NULL help_text,'DRAFT' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='status');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'submitTime' field_key,'提交时间' field_label,'submit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,36 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='submitTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'approveTime' field_key,'审批通过时间' field_label,'approve_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,37 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='approveTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'payTime' field_key,'支付完成时间' field_label,'pay_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,38 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='payTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'auditUserId' field_key,'共享审核人用户ID' field_label,'audit_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,39 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='auditUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'auditTime' field_key,'共享审核时间' field_label,'audit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,40 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='auditTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'auditRemark' field_key,'审核意见' field_label,'audit_remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,41 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='auditRemark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'voucherNo' field_key,'会计凭证号' field_label,'voucher_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,42 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='voucherNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'sourceBillId' field_key,'来源单据ID' field_label,'source_bill_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,43 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='sourceBillId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'remark' field_key,'备注' field_label,'remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,44 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='remark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'contractId' field_key,'关联合同ID' field_label,'contract_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,45 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='contractId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'poReceiptId' field_key,'关联收货单ID' field_label,'po_receipt_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,46 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='poReceiptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'poId' field_key,'关联采购订单ID' field_label,'po_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,47 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='poId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'customerId' field_key,'关联往来单位ID' field_label,'customer_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,48 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='customerId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'payeeName' field_key,'收款方' field_label,'payee_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,49 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='payeeName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'payTarget' field_key,'收款对象' field_label,'pay_target' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,50 sort_order,'收款对象' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='payTarget');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'payeeAccountNo' field_key,'收款账号' field_label,'payee_account_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,51 sort_order,'收款账号' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='payeeAccountNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'billNo' field_key,'单据编号' field_label,'bill_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,1 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='billNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'deliveryStatus' field_key,'实物签收状态' field_label,'delivery_status' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,2 sort_order,'实物签收状态' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='deliveryStatus');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'rejectType' field_key,'驳回类型' field_label,'reject_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,3 sort_order,'驳回类型' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='rejectType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'billType' field_key,'单据类型' field_label,'bill_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,4 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='billType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'billSubType' field_key,'单据子类' field_label,'bill_sub_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,5 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='billSubType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'companyId' field_key,'company_id' field_label,'company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,6 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='companyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'settleCompanyId' field_key,'结算' field_label,'settle_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,7 sort_order,'结算(报销)单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='settleCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'payCompanyId' field_key,'支付单位' field_label,'pay_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,8 sort_order,'支付单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='payCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'loanCompanyId' field_key,'借款单位' field_label,'loan_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,9 sort_order,'借款单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='loanCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'deptId' field_key,'dept_id' field_label,'dept_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,10 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='deptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'costCenterId' field_key,'成本中心ID' field_label,'cost_center_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,11 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='costCenterId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'projectId' field_key,'项目ID' field_label,'project_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,12 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='projectId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'applyUserId' field_key,'申请人用户ID' field_label,'apply_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,13 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='applyUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'applyUserName' field_key,'申请人姓名' field_label,'apply_user_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,14 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='applyUserName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'agentUserId' field_key,'代填人用户ID' field_label,'agent_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,15 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='agentUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'receiverUserId' field_key,'收款人' field_label,'receiver_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,16 sort_order,'收款人' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='receiverUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'applyDate' field_key,'申请日期' field_label,'apply_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,17 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='applyDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'periodYear' field_key,'归属会计年度' field_label,'period_year' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,18 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='periodYear');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'periodMonth' field_key,'归属会计月份' field_label,'period_month' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,19 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='periodMonth');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'currencyCode' field_key,'单据币种' field_label,'currency_code' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,20 sort_order,NULL placeholder,
       NULL help_text,'CNY' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='currencyCode');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'exchangeRate' field_key,'汇率' field_label,'exchange_rate' db_column,NULL section,
       'PERCENT' widget_type,'FULL' control_width,12 span,21 sort_order,NULL placeholder,
       NULL help_text,'1.0000000000' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='exchangeRate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'priceType' field_key,'录入口径' field_label,'price_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,22 sort_order,'录入口径' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='priceType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'totalAmount' field_key,'结算金额' field_label,'total_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,23 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='totalAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'amountCn' field_key,'金额大写' field_label,'amount_cn' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,24 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='amountCn');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'taxAmount' field_key,'税额合计' field_label,'tax_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,25 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='taxAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'netAmount' field_key,'不含税金额合计' field_label,'net_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,26 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='netAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'approvedAmount' field_key,'审批核定金额' field_label,'approved_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,27 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='approvedAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'approvedPayAmount' field_key,'核定付款金额' field_label,'approved_pay_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,28 sort_order,'核定付款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='approvedPayAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'approvedRefundAmount' field_key,'核定退款金额' field_label,'approved_refund_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,29 sort_order,'核定退款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='approvedRefundAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'offsetAmount' field_key,'冲抵金额' field_label,'offset_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,30 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='offsetAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'payableAmount' field_key,'应付金额 = 核定 - 冲抵' field_label,'payable_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,31 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='payableAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'paidAmount' field_key,'已付金额' field_label,'paid_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,32 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='paidAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'loanBalance' field_key,'借款未还余额' field_label,'loan_balance' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,33 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='loanBalance');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'expectRepayDate' field_key,'预计还款日期' field_label,'expect_repay_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,34 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='expectRepayDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'status' field_key,'单据状态' field_label,'status' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,35 sort_order,NULL placeholder,
       NULL help_text,'DRAFT' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='status');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'submitTime' field_key,'提交时间' field_label,'submit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,36 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='submitTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'approveTime' field_key,'审批通过时间' field_label,'approve_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,37 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='approveTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'payTime' field_key,'支付完成时间' field_label,'pay_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,38 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='payTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'auditUserId' field_key,'共享审核人用户ID' field_label,'audit_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,39 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='auditUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'auditTime' field_key,'共享审核时间' field_label,'audit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,40 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='auditTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'auditRemark' field_key,'审核意见' field_label,'audit_remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,41 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='auditRemark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'voucherNo' field_key,'会计凭证号' field_label,'voucher_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,42 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='voucherNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'sourceBillId' field_key,'来源单据ID' field_label,'source_bill_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,43 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='sourceBillId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'remark' field_key,'备注' field_label,'remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,44 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='remark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'contractId' field_key,'关联合同ID' field_label,'contract_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,45 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='contractId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'poReceiptId' field_key,'关联收货单ID' field_label,'po_receipt_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,46 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='poReceiptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'poId' field_key,'关联采购订单ID' field_label,'po_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,47 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='poId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'customerId' field_key,'关联往来单位ID' field_label,'customer_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,48 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='customerId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'payeeName' field_key,'收款方' field_label,'payee_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,49 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='payeeName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'payTarget' field_key,'收款对象' field_label,'pay_target' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,50 sort_order,'收款对象' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='payTarget');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'payeeAccountNo' field_key,'收款账号' field_label,'payee_account_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,51 sort_order,'收款账号' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='payeeAccountNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'billNo' field_key,'单据编号' field_label,'bill_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,1 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='billNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'deliveryStatus' field_key,'实物签收状态' field_label,'delivery_status' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,2 sort_order,'实物签收状态' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='deliveryStatus');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'rejectType' field_key,'驳回类型' field_label,'reject_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,3 sort_order,'驳回类型' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='rejectType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'billType' field_key,'单据类型' field_label,'bill_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,4 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='billType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'billSubType' field_key,'单据子类' field_label,'bill_sub_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,5 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='billSubType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'companyId' field_key,'company_id' field_label,'company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,6 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='companyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'settleCompanyId' field_key,'结算' field_label,'settle_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,7 sort_order,'结算(报销)单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='settleCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'payCompanyId' field_key,'支付单位' field_label,'pay_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,8 sort_order,'支付单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='payCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'loanCompanyId' field_key,'借款单位' field_label,'loan_company_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,9 sort_order,'借款单位' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='loanCompanyId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'deptId' field_key,'dept_id' field_label,'dept_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,10 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='deptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'costCenterId' field_key,'成本中心ID' field_label,'cost_center_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,11 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='costCenterId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'projectId' field_key,'项目ID' field_label,'project_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,12 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='projectId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'applyUserId' field_key,'申请人用户ID' field_label,'apply_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,13 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='applyUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'applyUserName' field_key,'申请人姓名' field_label,'apply_user_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,14 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='applyUserName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'agentUserId' field_key,'代填人用户ID' field_label,'agent_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,15 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='agentUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'receiverUserId' field_key,'收款人' field_label,'receiver_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,16 sort_order,'收款人' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='receiverUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'applyDate' field_key,'申请日期' field_label,'apply_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,17 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='applyDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'periodYear' field_key,'归属会计年度' field_label,'period_year' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,18 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='periodYear');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'periodMonth' field_key,'归属会计月份' field_label,'period_month' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,19 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='periodMonth');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'currencyCode' field_key,'单据币种' field_label,'currency_code' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,20 sort_order,NULL placeholder,
       NULL help_text,'CNY' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='currencyCode');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'exchangeRate' field_key,'汇率' field_label,'exchange_rate' db_column,NULL section,
       'PERCENT' widget_type,'FULL' control_width,12 span,21 sort_order,NULL placeholder,
       NULL help_text,'1.0000000000' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='exchangeRate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'priceType' field_key,'录入口径' field_label,'price_type' db_column,NULL section,
       'SELECT' widget_type,'FULL' control_width,12 span,22 sort_order,'录入口径' placeholder,
       NULL help_text,NULL default_value,'{"source": "enum", "hint": "取值由 sys_dict_data 维护"}' options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='priceType');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'totalAmount' field_key,'申请金额' field_label,'total_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,23 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='totalAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'amountCn' field_key,'金额大写' field_label,'amount_cn' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,24 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='amountCn');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'taxAmount' field_key,'税额合计' field_label,'tax_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,25 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='taxAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'netAmount' field_key,'不含税金额合计' field_label,'net_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,26 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='netAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'approvedAmount' field_key,'审批核定金额' field_label,'approved_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,27 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='approvedAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'approvedPayAmount' field_key,'核定付款金额' field_label,'approved_pay_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,28 sort_order,'核定付款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='approvedPayAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'approvedRefundAmount' field_key,'核定退款金额' field_label,'approved_refund_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,29 sort_order,'核定退款金额' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='approvedRefundAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'offsetAmount' field_key,'冲抵金额' field_label,'offset_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,30 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='offsetAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'payableAmount' field_key,'应付金额 = 核定 - 冲抵' field_label,'payable_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,31 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='payableAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'paidAmount' field_key,'已付金额' field_label,'paid_amount' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,32 sort_order,NULL placeholder,
       NULL help_text,'0.00' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='paidAmount');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'loanBalance' field_key,'借款未还余额' field_label,'loan_balance' db_column,NULL section,
       'MONEY' widget_type,'FULL' control_width,12 span,33 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='loanBalance');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'expectRepayDate' field_key,'预计还款日期' field_label,'expect_repay_date' db_column,NULL section,
       'DATE' widget_type,'FULL' control_width,12 span,34 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='expectRepayDate');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'status' field_key,'单据状态' field_label,'status' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,35 sort_order,NULL placeholder,
       NULL help_text,'DRAFT' default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='status');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'submitTime' field_key,'提交时间' field_label,'submit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,36 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='submitTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'approveTime' field_key,'审批通过时间' field_label,'approve_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,37 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='approveTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'payTime' field_key,'支付完成时间' field_label,'pay_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,38 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='payTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'auditUserId' field_key,'共享审核人用户ID' field_label,'audit_user_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,39 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='auditUserId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'auditTime' field_key,'共享审核时间' field_label,'audit_time' db_column,NULL section,
       'DATETIME' widget_type,'FULL' control_width,12 span,40 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='auditTime');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'auditRemark' field_key,'审核意见' field_label,'audit_remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,41 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='auditRemark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'voucherNo' field_key,'会计凭证号' field_label,'voucher_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,42 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='voucherNo');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'sourceBillId' field_key,'来源单据ID' field_label,'source_bill_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,43 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='sourceBillId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'remark' field_key,'备注' field_label,'remark' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,44 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='remark');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'contractId' field_key,'关联合同ID' field_label,'contract_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,45 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='contractId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'poReceiptId' field_key,'关联收货单ID' field_label,'po_receipt_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,46 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='poReceiptId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'poId' field_key,'关联采购订单ID' field_label,'po_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,47 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='poId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'customerId' field_key,'关联往来单位ID' field_label,'customer_id' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,48 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='customerId');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'payeeName' field_key,'收款方' field_label,'payee_name' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,49 sort_order,NULL placeholder,
       NULL help_text,NULL default_value,NULL options_json,1 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='payeeName');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'payTarget' field_key,'收款对象' field_label,'pay_target' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,50 sort_order,'收款对象' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='payTarget');

INSERT INTO `md_form_field`
  (`tenant_id`,`form_code`,`field_key`,`field_label`,`db_column`,`section`,`widget_type`,
   `control_width`,`span`,`sort_order`,`placeholder`,`help_text`,`default_value`,`options_json`,
   `required`,`locked`,`unique_flag`,`system_flag`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'payeeAccountNo' field_key,'收款账号' field_label,'payee_account_no' db_column,NULL section,
       'INPUT' widget_type,'FULL' control_width,12 span,51 sort_order,'收款账号' placeholder,
       NULL help_text,NULL default_value,NULL options_json,0 required,0 locked,
       0 unique_flag,1 system_flag,'ENABLED' status,'由 exp_bill 表结构反射生成(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_field`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='payeeAccountNo');


-- 校验规则：324 条

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'billNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'单据编号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='billNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'deliveryStatus' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'实物签收状态长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='deliveryStatus' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'rejectType' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'驳回类型长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='rejectType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'billType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据类型长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='billType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'billSubType' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'单据子类长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='billSubType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'applyUserName' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'申请人长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='applyUserName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'currencyCode' field_key,'MAX_LENGTH' rule_type,'10' rule_value,'单据币种长度不能超过10' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='currencyCode' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'exchangeRate' field_key,'MIN_VALUE' rule_type,'0' rule_value,'汇率不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='exchangeRate' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'priceType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'录入口径长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='priceType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'totalAmount' field_key,'NOT_NULL' rule_type,NULL rule_value,'申请金额不能为空' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='totalAmount' AND `rule_type`='NOT_NULL');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'amountCn' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'金额大写长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='amountCn' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'taxAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'税额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='taxAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'netAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'不含税金额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='netAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'approvedAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'审批核定金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='approvedAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'approvedPayAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定付款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='approvedPayAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'approvedRefundAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定退款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='approvedRefundAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'offsetAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'冲抵金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='offsetAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'payableAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'应付金额 = 核定 - 冲抵不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='payableAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'paidAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'已付金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='paidAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'loanBalance' field_key,'MIN_VALUE' rule_type,'0' rule_value,'借款未还余额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='loanBalance' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'status' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据状态长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='status' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'auditRemark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'审核意见长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='auditRemark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'voucherNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'会计凭证号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='voucherNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'remark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'备注长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='remark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'payeeName' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'收款方名称长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='payeeName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'payTarget' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'收款对象长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='payTarget' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_MEETING' form_code,'payeeAccountNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'收款账号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_MEETING' AND `field_key`='payeeAccountNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'billNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'单据编号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='billNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'deliveryStatus' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'实物签收状态长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='deliveryStatus' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'rejectType' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'驳回类型长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='rejectType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'billType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据类型长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='billType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'billSubType' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'单据子类长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='billSubType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'applyUserName' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'申请人长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='applyUserName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'currencyCode' field_key,'MAX_LENGTH' rule_type,'10' rule_value,'单据币种长度不能超过10' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='currencyCode' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'exchangeRate' field_key,'MIN_VALUE' rule_type,'0' rule_value,'汇率不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='exchangeRate' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'priceType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'录入口径长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='priceType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'totalAmount' field_key,'NOT_NULL' rule_type,NULL rule_value,'预计金额不能为空' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='totalAmount' AND `rule_type`='NOT_NULL');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'amountCn' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'金额大写长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='amountCn' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'taxAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'税额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='taxAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'netAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'不含税金额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='netAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'approvedAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'审批核定金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='approvedAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'approvedPayAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定付款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='approvedPayAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'approvedRefundAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定退款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='approvedRefundAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'offsetAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'冲抵金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='offsetAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'payableAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'应付金额 = 核定 - 冲抵不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='payableAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'paidAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'已付金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='paidAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'loanBalance' field_key,'MIN_VALUE' rule_type,'0' rule_value,'借款未还余额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='loanBalance' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'status' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据状态长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='status' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'auditRemark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'审核意见长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='auditRemark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'voucherNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'会计凭证号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='voucherNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'remark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'备注长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='remark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'payeeName' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'收款方名称长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='payeeName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'payTarget' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'收款对象长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='payTarget' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAVEL' form_code,'payeeAccountNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'收款账号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAVEL' AND `field_key`='payeeAccountNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'billNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'单据编号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='billNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'deliveryStatus' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'实物签收状态长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='deliveryStatus' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'rejectType' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'驳回类型长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='rejectType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'billType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据类型长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='billType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'billSubType' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'单据子类长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='billSubType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'applyUserName' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'申请人姓名长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='applyUserName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'currencyCode' field_key,'MAX_LENGTH' rule_type,'10' rule_value,'单据币种长度不能超过10' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='currencyCode' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'exchangeRate' field_key,'MIN_VALUE' rule_type,'0' rule_value,'汇率不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='exchangeRate' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'priceType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'录入口径长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='priceType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'totalAmount' field_key,'NOT_NULL' rule_type,NULL rule_value,'预计金额不能为空' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='totalAmount' AND `rule_type`='NOT_NULL');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'amountCn' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'金额大写长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='amountCn' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'taxAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'税额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='taxAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'netAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'不含税金额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='netAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'approvedAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'审批核定金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='approvedAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'approvedPayAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定付款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='approvedPayAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'approvedRefundAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定退款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='approvedRefundAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'offsetAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'冲抵金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='offsetAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'payableAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'应付金额 = 核定 - 冲抵不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='payableAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'paidAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'已付金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='paidAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'loanBalance' field_key,'MIN_VALUE' rule_type,'0' rule_value,'借款未还余额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='loanBalance' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'status' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据状态长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='status' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'auditRemark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'审核意见长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='auditRemark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'voucherNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'会计凭证号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='voucherNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'remark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'备注长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='remark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'payeeName' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'收款方名称长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='payeeName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'payTarget' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'收款对象长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='payTarget' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_TRAIN' form_code,'payeeAccountNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'收款账号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_TRAIN' AND `field_key`='payeeAccountNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'billNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'单据编号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='billNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'deliveryStatus' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'实物签收状态长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='deliveryStatus' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'rejectType' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'驳回类型长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='rejectType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'billType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据类型长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='billType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'billSubType' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'单据子类长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='billSubType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'applyUserName' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'报销人长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='applyUserName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'currencyCode' field_key,'MAX_LENGTH' rule_type,'10' rule_value,'单据币种长度不能超过10' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='currencyCode' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'exchangeRate' field_key,'MIN_VALUE' rule_type,'0' rule_value,'汇率不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='exchangeRate' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'priceType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'录入口径长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='priceType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'totalAmount' field_key,'NOT_NULL' rule_type,NULL rule_value,'报销金额不能为空' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='totalAmount' AND `rule_type`='NOT_NULL');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'amountCn' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'金额大写长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='amountCn' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'taxAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'税额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='taxAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'netAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'不含税金额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='netAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'approvedAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'审批核定金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='approvedAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'approvedPayAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定付款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='approvedPayAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'approvedRefundAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定退款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='approvedRefundAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'offsetAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'冲抵金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='offsetAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'payableAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'应付金额 = 核定 - 冲抵不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='payableAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'paidAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'已付金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='paidAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'loanBalance' field_key,'MIN_VALUE' rule_type,'0' rule_value,'借款未还余额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='loanBalance' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'status' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据状态长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='status' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'auditRemark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'审核意见长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='auditRemark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'voucherNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'会计凭证号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='voucherNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'remark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'备注长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='remark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'payeeName' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'收款方长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='payeeName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'payTarget' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'收款对象长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='payTarget' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM' form_code,'payeeAccountNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'收款账号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM' AND `field_key`='payeeAccountNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'billNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'单据编号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='billNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'deliveryStatus' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'实物签收状态长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='deliveryStatus' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'rejectType' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'驳回类型长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='rejectType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'billType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据类型长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='billType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'billSubType' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'单据子类长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='billSubType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'applyUserName' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'申请人姓名长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='applyUserName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'currencyCode' field_key,'MAX_LENGTH' rule_type,'10' rule_value,'单据币种长度不能超过10' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='currencyCode' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'exchangeRate' field_key,'MIN_VALUE' rule_type,'0' rule_value,'汇率不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='exchangeRate' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'priceType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'录入口径长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='priceType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'totalAmount' field_key,'NOT_NULL' rule_type,NULL rule_value,'报销金额不能为空' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='totalAmount' AND `rule_type`='NOT_NULL');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'amountCn' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'金额大写长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='amountCn' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'taxAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'税额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='taxAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'netAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'不含税金额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='netAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'approvedAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'审批核定金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='approvedAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'approvedPayAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定付款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='approvedPayAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'approvedRefundAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定退款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='approvedRefundAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'offsetAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'冲抵金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='offsetAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'payableAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'应付金额 = 核定 - 冲抵不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='payableAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'paidAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'已付金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='paidAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'loanBalance' field_key,'MIN_VALUE' rule_type,'0' rule_value,'借款未还余额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='loanBalance' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'status' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据状态长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='status' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'auditRemark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'审核意见长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='auditRemark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'voucherNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'会计凭证号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='voucherNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'remark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'备注长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='remark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'payeeName' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'收款方长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='payeeName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'payTarget' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'收款对象长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='payTarget' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_CLAIM_TRAVEL' form_code,'payeeAccountNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'收款账号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_CLAIM_TRAVEL' AND `field_key`='payeeAccountNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'billNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'单据编号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='billNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'deliveryStatus' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'实物签收状态长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='deliveryStatus' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'rejectType' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'驳回类型长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='rejectType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'billType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据类型长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='billType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'billSubType' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'单据子类长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='billSubType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'applyUserName' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'申请人姓名长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='applyUserName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'currencyCode' field_key,'MAX_LENGTH' rule_type,'10' rule_value,'单据币种长度不能超过10' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='currencyCode' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'exchangeRate' field_key,'MIN_VALUE' rule_type,'0' rule_value,'汇率不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='exchangeRate' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'priceType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'录入口径长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='priceType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'totalAmount' field_key,'NOT_NULL' rule_type,NULL rule_value,'代付金额不能为空' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='totalAmount' AND `rule_type`='NOT_NULL');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'amountCn' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'金额大写长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='amountCn' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'taxAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'税额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='taxAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'netAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'不含税金额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='netAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'approvedAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'审批核定金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='approvedAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'approvedPayAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定付款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='approvedPayAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'approvedRefundAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定退款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='approvedRefundAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'offsetAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'冲抵金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='offsetAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'payableAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'应付金额 = 核定 - 冲抵不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='payableAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'paidAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'已付金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='paidAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'loanBalance' field_key,'MIN_VALUE' rule_type,'0' rule_value,'借款未还余额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='loanBalance' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'status' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据状态长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='status' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'auditRemark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'审核意见长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='auditRemark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'voucherNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'会计凭证号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='voucherNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'remark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'备注长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='remark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'payeeName' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'收款方长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='payeeName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'payTarget' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'收款对象长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='payTarget' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AGENCY' form_code,'payeeAccountNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'收款账号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AGENCY' AND `field_key`='payeeAccountNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'billNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'单据编号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='billNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'deliveryStatus' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'实物签收状态长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='deliveryStatus' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'rejectType' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'驳回类型长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='rejectType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'billType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据类型长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='billType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'billSubType' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'单据子类长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='billSubType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'applyUserName' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'借款人长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='applyUserName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'currencyCode' field_key,'MAX_LENGTH' rule_type,'10' rule_value,'单据币种长度不能超过10' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='currencyCode' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'exchangeRate' field_key,'MIN_VALUE' rule_type,'0' rule_value,'汇率不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='exchangeRate' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'priceType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'录入口径长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='priceType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'totalAmount' field_key,'NOT_NULL' rule_type,NULL rule_value,'借款金额不能为空' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='totalAmount' AND `rule_type`='NOT_NULL');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'amountCn' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'金额大写长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='amountCn' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'taxAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'税额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='taxAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'netAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'不含税金额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='netAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'approvedAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'审批核定金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='approvedAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'approvedPayAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定付款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='approvedPayAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'approvedRefundAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定退款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='approvedRefundAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'offsetAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'冲抵金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='offsetAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'payableAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'应付金额 = 核定 - 冲抵不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='payableAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'paidAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'已付金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='paidAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'loanBalance' field_key,'MIN_VALUE' rule_type,'0' rule_value,'借款未还余额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='loanBalance' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'status' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据状态长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='status' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'auditRemark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'审核意见长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='auditRemark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'voucherNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'会计凭证号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='voucherNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'remark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'备注长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='remark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'payeeName' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'收款方名称长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='payeeName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'payTarget' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'收款对象长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='payTarget' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_LOAN' form_code,'payeeAccountNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'收款账号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_LOAN' AND `field_key`='payeeAccountNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'billNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'单据编号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='billNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'deliveryStatus' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'实物签收状态长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='deliveryStatus' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'rejectType' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'驳回类型长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='rejectType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'billType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据类型长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='billType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'billSubType' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'单据子类长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='billSubType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'applyUserName' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'还款人长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='applyUserName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'currencyCode' field_key,'MAX_LENGTH' rule_type,'10' rule_value,'单据币种长度不能超过10' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='currencyCode' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'exchangeRate' field_key,'MIN_VALUE' rule_type,'0' rule_value,'汇率不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='exchangeRate' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'priceType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'录入口径长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='priceType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'totalAmount' field_key,'NOT_NULL' rule_type,NULL rule_value,'还款金额不能为空' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='totalAmount' AND `rule_type`='NOT_NULL');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'amountCn' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'金额大写长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='amountCn' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'taxAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'税额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='taxAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'netAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'不含税金额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='netAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'approvedAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'审批核定金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='approvedAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'approvedPayAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定付款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='approvedPayAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'approvedRefundAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定退款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='approvedRefundAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'offsetAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'冲抵金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='offsetAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'payableAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'应付金额 = 核定 - 冲抵不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='payableAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'paidAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'已付金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='paidAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'loanBalance' field_key,'MIN_VALUE' rule_type,'0' rule_value,'借款未还余额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='loanBalance' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'status' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据状态长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='status' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'auditRemark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'审核意见长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='auditRemark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'voucherNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'会计凭证号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='voucherNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'remark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'备注长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='remark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'payeeName' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'收款方长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='payeeName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'payTarget' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'收款对象长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='payTarget' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_REPAY' form_code,'payeeAccountNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'收款账号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_REPAY' AND `field_key`='payeeAccountNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'billNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'单据编号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='billNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'deliveryStatus' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'实物签收状态长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='deliveryStatus' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'rejectType' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'驳回类型长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='rejectType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'billType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据类型长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='billType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'billSubType' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'单据子类长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='billSubType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'applyUserName' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'申请人姓名长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='applyUserName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'currencyCode' field_key,'MAX_LENGTH' rule_type,'10' rule_value,'单据币种长度不能超过10' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='currencyCode' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'exchangeRate' field_key,'MIN_VALUE' rule_type,'0' rule_value,'汇率不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='exchangeRate' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'priceType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'录入口径长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='priceType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'totalAmount' field_key,'NOT_NULL' rule_type,NULL rule_value,'付款金额不能为空' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='totalAmount' AND `rule_type`='NOT_NULL');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'amountCn' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'金额大写长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='amountCn' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'taxAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'税额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='taxAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'netAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'不含税金额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='netAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'approvedAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'审批核定金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='approvedAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'approvedPayAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定付款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='approvedPayAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'approvedRefundAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定退款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='approvedRefundAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'offsetAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'冲抵金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='offsetAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'payableAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'应付金额 = 核定 - 冲抵不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='payableAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'paidAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'已付金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='paidAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'loanBalance' field_key,'MIN_VALUE' rule_type,'0' rule_value,'借款未还余额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='loanBalance' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'status' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据状态长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='status' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'auditRemark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'审核意见长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='auditRemark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'voucherNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'会计凭证号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='voucherNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'remark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'备注长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='remark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'payeeName' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'收款方长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='payeeName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'payTarget' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'收款对象长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='payTarget' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_SETTLE' form_code,'payeeAccountNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'收款账号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_SETTLE' AND `field_key`='payeeAccountNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'billNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'单据编号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='billNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'deliveryStatus' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'实物签收状态长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='deliveryStatus' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'rejectType' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'驳回类型长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='rejectType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'billType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据类型长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='billType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'billSubType' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'单据子类长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='billSubType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'applyUserName' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'申请人姓名长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='applyUserName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'currencyCode' field_key,'MAX_LENGTH' rule_type,'10' rule_value,'单据币种长度不能超过10' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='currencyCode' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'exchangeRate' field_key,'MIN_VALUE' rule_type,'0' rule_value,'汇率不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='exchangeRate' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'priceType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'录入口径长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='priceType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'totalAmount' field_key,'NOT_NULL' rule_type,NULL rule_value,'预付金额不能为空' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='totalAmount' AND `rule_type`='NOT_NULL');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'amountCn' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'金额大写长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='amountCn' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'taxAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'税额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='taxAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'netAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'不含税金额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='netAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'approvedAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'审批核定金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='approvedAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'approvedPayAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定付款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='approvedPayAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'approvedRefundAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定退款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='approvedRefundAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'offsetAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'冲抵金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='offsetAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'payableAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'应付金额 = 核定 - 冲抵不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='payableAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'paidAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'已付金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='paidAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'loanBalance' field_key,'MIN_VALUE' rule_type,'0' rule_value,'借款未还余额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='loanBalance' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'status' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据状态长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='status' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'auditRemark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'审核意见长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='auditRemark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'voucherNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'会计凭证号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='voucherNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'remark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'备注长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='remark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'payeeName' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'收款方长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='payeeName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'payTarget' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'收款对象长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='payTarget' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_PREPAY' form_code,'payeeAccountNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'收款账号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_PREPAY' AND `field_key`='payeeAccountNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'billNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'单据编号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='billNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'deliveryStatus' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'实物签收状态长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='deliveryStatus' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'rejectType' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'驳回类型长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='rejectType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'billType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据类型长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='billType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'billSubType' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'单据子类长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='billSubType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'applyUserName' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'申请人姓名长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='applyUserName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'currencyCode' field_key,'MAX_LENGTH' rule_type,'10' rule_value,'单据币种长度不能超过10' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='currencyCode' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'exchangeRate' field_key,'MIN_VALUE' rule_type,'0' rule_value,'汇率不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='exchangeRate' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'priceType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'录入口径长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='priceType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'totalAmount' field_key,'NOT_NULL' rule_type,NULL rule_value,'结算金额不能为空' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='totalAmount' AND `rule_type`='NOT_NULL');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'amountCn' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'金额大写长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='amountCn' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'taxAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'税额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='taxAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'netAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'不含税金额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='netAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'approvedAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'审批核定金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='approvedAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'approvedPayAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定付款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='approvedPayAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'approvedRefundAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定退款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='approvedRefundAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'offsetAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'冲抵金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='offsetAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'payableAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'应付金额 = 核定 - 冲抵不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='payableAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'paidAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'已付金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='paidAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'loanBalance' field_key,'MIN_VALUE' rule_type,'0' rule_value,'借款未还余额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='loanBalance' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'status' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据状态长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='status' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'auditRemark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'审核意见长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='auditRemark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'voucherNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'会计凭证号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='voucherNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'remark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'备注长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='remark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'payeeName' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'收款方长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='payeeName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'payTarget' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'收款对象长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='payTarget' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR' form_code,'payeeAccountNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'收款账号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR' AND `field_key`='payeeAccountNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'billNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'单据编号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='billNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'deliveryStatus' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'实物签收状态长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='deliveryStatus' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'rejectType' field_key,'MAX_LENGTH' rule_type,'16' rule_value,'驳回类型长度不能超过16' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='rejectType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'billType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据类型长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='billType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'billSubType' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'单据子类长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='billSubType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'applyUserName' field_key,'MAX_LENGTH' rule_type,'50' rule_value,'申请人姓名长度不能超过50' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='applyUserName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'currencyCode' field_key,'MAX_LENGTH' rule_type,'10' rule_value,'单据币种长度不能超过10' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='currencyCode' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'exchangeRate' field_key,'MIN_VALUE' rule_type,'0' rule_value,'汇率不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='exchangeRate' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'priceType' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'录入口径长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='priceType' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'totalAmount' field_key,'NOT_NULL' rule_type,NULL rule_value,'申请金额不能为空' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='totalAmount' AND `rule_type`='NOT_NULL');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'amountCn' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'金额大写长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='amountCn' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'taxAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'税额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='taxAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'netAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'不含税金额合计不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='netAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'approvedAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'审批核定金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='approvedAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'approvedPayAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定付款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='approvedPayAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'approvedRefundAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'核定退款金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='approvedRefundAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'offsetAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'冲抵金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='offsetAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'payableAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'应付金额 = 核定 - 冲抵不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='payableAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'paidAmount' field_key,'MIN_VALUE' rule_type,'0' rule_value,'已付金额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='paidAmount' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'loanBalance' field_key,'MIN_VALUE' rule_type,'0' rule_value,'借款未还余额不能为负数' error_message,
       'ERROR' severity,'BOTH' scope,1 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='loanBalance' AND `rule_type`='MIN_VALUE');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'status' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'单据状态长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='status' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'auditRemark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'审核意见长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='auditRemark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'voucherNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'会计凭证号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='voucherNo' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'remark' field_key,'MAX_LENGTH' rule_type,'1000' rule_value,'备注长度不能超过1000' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='remark' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'payeeName' field_key,'MAX_LENGTH' rule_type,'200' rule_value,'收款方长度不能超过200' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='payeeName' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'payTarget' field_key,'MAX_LENGTH' rule_type,'20' rule_value,'收款对象长度不能超过20' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='payTarget' AND `rule_type`='MAX_LENGTH');

INSERT INTO `md_form_validation`
  (`tenant_id`,`form_code`,`field_key`,`rule_type`,`rule_value`,`error_message`,
   `severity`,`scope`,`sort_order`,`status`,`remark`)
SELECT 1,'EXP_AIR_APPLY' form_code,'payeeAccountNo' field_key,'MAX_LENGTH' rule_type,'64' rule_value,'收款账号长度不能超过64' error_message,
       'ERROR' severity,'SAVE' scope,2 sort_order,'ENABLED' status,'由字段元数据派生(V1.4.86)' remark
WHERE NOT EXISTS (SELECT 1 FROM `md_form_validation`
                    WHERE `form_code`='EXP_AIR_APPLY' AND `field_key`='payeeAccountNo' AND `rule_type`='MAX_LENGTH');
