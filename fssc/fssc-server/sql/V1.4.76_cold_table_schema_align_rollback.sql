-- =============================================================================
-- V1.4.76 回滚  修复冷表与热表结构脱节（schema drift）
-- 日期：2026-10-17
-- 反向重建 3 张冷表：删掉 V1.4.76 补入的列，还原到「与热表脱节」的旧状态。
--  仅在本库冷表仍为 0 行（建表后从未归档过）时可直接执行；
--    若冷表已有数据，回滚会先 INSERT 迁移现有数据再换表，不丢数据但结构回到会失败的状态。
-- =============================================================================

-- 回滚 exp_bill_cold：删掉 V1.4.76 补入的列 ['loan_company_id']
DROP TABLE IF EXISTS `exp_bill_cold_rollback`;
-- 去重清理(DDL 隐式提交)
CREATE TABLE `exp_bill_cold_rollback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `bill_no` varchar(64) NOT NULL COMMENT '单据编号(由编号规则生成)',
  `paperless` varchar(8) DEFAULT 'N' COMMENT '无纸化标记：Y 无纸化 / N 需实物',
  `delivery_status` varchar(16) COMMENT '实物签收状态：NOT_REQUIRED/UNSIGNED/SIGNED',
  `reject_type` varchar(16) COMMENT '驳回类型：THROW_BACK 漏单驳回 / GRADE_BY_GRADE 逐级驳回',
  `bill_type` varchar(20) NOT NULL COMMENT '单据类型:APPLY事前申请/CLAIM费用报销/LOAN员工借款/REPAY借款还款/PAY对公支付',
  `bill_sub_type` varchar(50) COMMENT '单据子类:TRAVEL差旅/DAILY日常/ENTERTAIN招待/TRAIN培训等(取字典)',
  `title` varchar(300) NOT NULL COMMENT '事由/标题',
  `company_id` bigint,
  `settle_company_id` bigint COMMENT '结算(报销)单位：跨组织场景下与费用承担单位 company_id 分离',
  `pay_company_id` bigint COMMENT '支付单位：实际付款主体，跨组织资金结算使用',
  `dept_id` bigint,
  `cost_center_id` bigint COMMENT '成本中心ID',
  `project_id` bigint COMMENT '项目ID',
  `apply_user_id` bigint NOT NULL COMMENT '申请人用户ID',
  `apply_user_name` varchar(50) COMMENT '申请人姓名(冗余,防离职后查不到)',
  `agent_user_id` bigint COMMENT '代填人用户ID(秘书代报销场景)',
  `receiver_user_id` bigint COMMENT '收款人：代理报销时报销人(apply_user_id)≠收款人，款项付给 receiver_user_id',
  `apply_date` date NOT NULL COMMENT '申请日期',
  `period_year` int COMMENT '归属会计年度',
  `period_month` int COMMENT '归属会计月份',
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '单据币种',
  `exchange_rate` decimal(20,10) NOT NULL DEFAULT 1.0000000000 COMMENT '汇率(折本位币)',
  `price_type` varchar(20) COMMENT '录入口径：TAX_IN含税录入(默认)/TAX_EX不含税录入',
  `total_amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '申请/报销总金额(含税)',
  `amount_cn` varchar(200) COMMENT '金额大写(中文大写,打印/OA用)',
  `tax_amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '税额合计',
  `net_amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '不含税金额合计',
  `approved_amount` decimal(20,2) COMMENT '审批核定金额(审批人可核减)',
  `approved_pay_amount` decimal(20,2) COMMENT '核定付款金额：≤核定报销金额，付款控制口径',
  `approved_refund_amount` decimal(20,2) COMMENT '核定退款金额：=核定报销 − 核定付款（多退回少的部分）',
  `offset_amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '冲抵金额(冲借款/预付)',
  `payable_amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '应付金额 = 核定 - 冲抵',
  `paid_amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '已付金额',
  `amount_checksum` varchar(64) COMMENT '关键字段校验和(SHA-256，提交时生成)',
  `loan_balance` decimal(20,2) COMMENT '借款未还余额(bill_type=LOAN时维护)',
  `expect_repay_date` date COMMENT '预计还款日期',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '单据状态:DRAFT草稿/APPROVING审批中/REJECTED已驳回/APPROVED审批通过/AUDITING共享审核中/AUDIT_BACK审核退回/PAYING支付中/PAID已支付/ACCOUNTED已入账/CLOSED已关闭/CANCELLED已作废',
  `proc_inst_id` varchar(64) COMMENT '流程实例ID',
  `submit_time` datetime COMMENT '提交时间',
  `approve_time` datetime COMMENT '审批通过时间',
  `pay_time` datetime COMMENT '支付完成时间',
  `audit_user_id` bigint COMMENT '共享审核人用户ID',
  `audit_time` datetime COMMENT '共享审核时间',
  `audit_remark` varchar(1000) COMMENT '审核意见',
  `image_status` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '影像状态:NONE无/WAITING待上传/UPLOADED已上传/CHECKED已核验/BACK影像退回',
  `archive_status` varchar(20) NOT NULL DEFAULT 'UNARCHIVED' COMMENT '归档状态 UNARCHIVED未归档/ARCHIVED已归档',
  `archive_time` datetime COMMENT '归档时间',
  `archive_by` bigint COMMENT '归档人',
  `voucher_no` varchar(64) COMMENT '会计凭证号',
  `voucher_date` date COMMENT '记账日期',
  `urgent` tinyint NOT NULL DEFAULT 0 COMMENT '是否加急:0否 1是',
  `source` varchar(20) NOT NULL DEFAULT 'PC' COMMENT '录入来源:PC/H5/APP/IMPORT/API',
  `source_bill_id` bigint COMMENT '来源单据ID(报销单关联的事前申请单)',
  `remark` varchar(1000) COMMENT '备注',
  `ext_json` text COMMENT '扩展字段JSON(租户自定义表单项)',
  `create_by` bigint,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT 0,
  `contract_id` bigint COMMENT '关联合同ID(cont_contract)',
  `po_receipt_id` bigint COMMENT '关联收货单ID',
  `po_id` bigint COMMENT '关联采购订单ID',
  `customer_id` bigint COMMENT '关联往来单位ID(crm_customer)',
  `payee_name` varchar(200) COMMENT '收款方名称(对公支付)',
  `pay_target` varchar(20) COMMENT '收款对象：EMPLOYEE员工/SUPPLIER供应商/CUSTOMER客户',
  `payee_account_no` varchar(64) COMMENT '收款账号：按 pay_target 驱动必填（员工→个人卡，供应商/客户→对公卡）',
  `archived_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `exp_bill_cold_rollback` (`id`, `tenant_id`, `bill_no`, `paperless`, `delivery_status`, `reject_type`, `bill_type`, `bill_sub_type`, `title`, `company_id`, `settle_company_id`, `pay_company_id`, `dept_id`, `cost_center_id`, `project_id`, `apply_user_id`, `apply_user_name`, `agent_user_id`, `receiver_user_id`, `apply_date`, `period_year`, `period_month`, `currency_code`, `exchange_rate`, `price_type`, `total_amount`, `amount_cn`, `tax_amount`, `net_amount`, `approved_amount`, `approved_pay_amount`, `approved_refund_amount`, `offset_amount`, `payable_amount`, `paid_amount`, `amount_checksum`, `loan_balance`, `expect_repay_date`, `status`, `proc_inst_id`, `submit_time`, `approve_time`, `pay_time`, `audit_user_id`, `audit_time`, `audit_remark`, `image_status`, `archive_status`, `archive_time`, `archive_by`, `voucher_no`, `voucher_date`, `urgent`, `source`, `source_bill_id`, `remark`, `ext_json`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `contract_id`, `po_receipt_id`, `po_id`, `customer_id`, `payee_name`, `pay_target`, `payee_account_no`, `archived_at`) SELECT `id`, `tenant_id`, `bill_no`, `paperless`, `delivery_status`, `reject_type`, `bill_type`, `bill_sub_type`, `title`, `company_id`, `settle_company_id`, `pay_company_id`, `dept_id`, `cost_center_id`, `project_id`, `apply_user_id`, `apply_user_name`, `agent_user_id`, `receiver_user_id`, `apply_date`, `period_year`, `period_month`, `currency_code`, `exchange_rate`, `price_type`, `total_amount`, `amount_cn`, `tax_amount`, `net_amount`, `approved_amount`, `approved_pay_amount`, `approved_refund_amount`, `offset_amount`, `payable_amount`, `paid_amount`, `amount_checksum`, `loan_balance`, `expect_repay_date`, `status`, `proc_inst_id`, `submit_time`, `approve_time`, `pay_time`, `audit_user_id`, `audit_time`, `audit_remark`, `image_status`, `archive_status`, `archive_time`, `archive_by`, `voucher_no`, `voucher_date`, `urgent`, `source`, `source_bill_id`, `remark`, `ext_json`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `contract_id`, `po_receipt_id`, `po_id`, `customer_id`, `payee_name`, `pay_target`, `payee_account_no`, `archived_at` FROM `exp_bill_cold`;
DROP TABLE `exp_bill_cold`;
RENAME TABLE `exp_bill_cold_rollback` TO `exp_bill_cold`;

-- 回滚 exp_bill_line_cold：删掉 V1.4.76 补入的列 ['entertain_type', 'voucher_form', 'cn_desc']
DROP TABLE IF EXISTS `exp_bill_line_cold_rollback`;
-- 去重清理(DDL 隐式提交)
CREATE TABLE `exp_bill_line_cold_rollback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `bill_id` bigint NOT NULL COMMENT '单据主表ID',
  `line_no` int NOT NULL DEFAULT 1 COMMENT '行号',
  `item_id` bigint NOT NULL COMMENT '费用项目ID',
  `item_name` varchar(200) COMMENT '费用项目名称(冗余快照)',
  `expense_date` date COMMENT '费用发生日期',
  `dept_id` bigint COMMENT '行级承担部门(默认取表头)',
  `cost_center_id` bigint COMMENT '行级成本中心',
  `project_id` bigint COMMENT '行级项目',
  `subject_id` bigint COMMENT '会计科目ID(生成凭证用)',
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '原币种',
  `exchange_rate` decimal(20,10) NOT NULL DEFAULT 1.0000000000 COMMENT '汇率',
  `orig_amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '原币金额(含税)',
  `price_type` varchar(20) COMMENT '行录入口径：TAX_IN含税录入/TAX_EX不含税录入，缺省继承表头',
  `amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '本位币金额(含税)',
  `tax_rate` decimal(6,4) COMMENT '税率',
  `tax_amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '税额',
  `net_amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '不含税金额',
  `deduct_amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '可抵扣进项税额',
  `approved_amount` decimal(20,2) COMMENT '行级核定金额',
  `quantity` decimal(20,4) COMMENT '数量(人数/天数/次数)',
  `unit_price` decimal(20,4) COMMENT '单价',
  `person_count` int COMMENT '涉及人数(招待费必填)',
  `attendees` varchar(1000) COMMENT '参与人员(招待费/会议费)',
  `std_amount` decimal(20,2) COMMENT '匹配到的标准限额',
  `over_std` tinyint NOT NULL DEFAULT 0 COMMENT '是否超标:0否 1是',
  `over_reason` varchar(500) COMMENT '超标说明',
  `budget_item_id` bigint COMMENT '命中的预算明细ID',
  `remark` varchar(500) COMMENT '费用说明',
  `ext_json` text COMMENT '扩展字段JSON',
  `create_by` bigint,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT 0,
  `archived_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `exp_bill_line_cold_rollback` (`id`, `tenant_id`, `bill_id`, `line_no`, `item_id`, `item_name`, `expense_date`, `dept_id`, `cost_center_id`, `project_id`, `subject_id`, `currency_code`, `exchange_rate`, `orig_amount`, `price_type`, `amount`, `tax_rate`, `tax_amount`, `net_amount`, `deduct_amount`, `approved_amount`, `quantity`, `unit_price`, `person_count`, `attendees`, `std_amount`, `over_std`, `over_reason`, `budget_item_id`, `remark`, `ext_json`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `archived_at`) SELECT `id`, `tenant_id`, `bill_id`, `line_no`, `item_id`, `item_name`, `expense_date`, `dept_id`, `cost_center_id`, `project_id`, `subject_id`, `currency_code`, `exchange_rate`, `orig_amount`, `price_type`, `amount`, `tax_rate`, `tax_amount`, `net_amount`, `deduct_amount`, `approved_amount`, `quantity`, `unit_price`, `person_count`, `attendees`, `std_amount`, `over_std`, `over_reason`, `budget_item_id`, `remark`, `ext_json`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `archived_at` FROM `exp_bill_line_cold`;
DROP TABLE `exp_bill_line_cold`;
RENAME TABLE `exp_bill_line_cold_rollback` TO `exp_bill_line_cold`;

-- 回滚 exp_bill_payment_cold：删掉 V1.4.76 补入的列 ['proc_inst_id']
DROP TABLE IF EXISTS `exp_bill_payment_cold_rollback`;
-- 去重清理(DDL 隐式提交)
CREATE TABLE `exp_bill_payment_cold_rollback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `bill_id` bigint NOT NULL COMMENT '单据主表ID',
  `line_no` int NOT NULL DEFAULT 1 COMMENT '行号',
  `payee_type` varchar(20) NOT NULL DEFAULT 'EMPLOYEE' COMMENT '收款方类型:EMPLOYEE/SUPPLIER/COMPANY/OTHER',
  `payee_id` bigint COMMENT '收款方账户ID(md_payee)',
  `payee_name` varchar(200) NOT NULL COMMENT '收款户名(快照)',
  `bank_name` varchar(200) COMMENT '开户行(快照)',
  `bank_account` varchar(64) COMMENT '银行账号(快照,脱敏展示)',
  `pay_method` varchar(20) NOT NULL DEFAULT 'BANK' COMMENT '支付方式:BANK银行转账/CASH现金/CARD公务卡/OFFSET内部冲抵',
  `pay_account_id` bigint COMMENT '付款账户ID(md_bank_account)',
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '结算金额',
  `pay_status` varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT '支付状态:WAITING待支付/PAYING支付中/PAID已支付/FAILED支付失败',
  `pay_time` datetime COMMENT '支付时间',
  `pay_batch_no` varchar(64) COMMENT '支付批次号',
  `pay_serial_no` varchar(100) COMMENT '银行流水号',
  `fail_reason` varchar(500) COMMENT '失败原因',
  `remark` varchar(500),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT 0,
  `contract_id` bigint COMMENT '关联合同ID',
  `customer_id` bigint COMMENT '关联往来单位ID',
  `archived_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `exp_bill_payment_cold_rollback` (`id`, `tenant_id`, `bill_id`, `line_no`, `payee_type`, `payee_id`, `payee_name`, `bank_name`, `bank_account`, `pay_method`, `pay_account_id`, `currency_code`, `amount`, `pay_status`, `pay_time`, `pay_batch_no`, `pay_serial_no`, `fail_reason`, `remark`, `create_time`, `update_time`, `del_flag`, `contract_id`, `customer_id`, `archived_at`) SELECT `id`, `tenant_id`, `bill_id`, `line_no`, `payee_type`, `payee_id`, `payee_name`, `bank_name`, `bank_account`, `pay_method`, `pay_account_id`, `currency_code`, `amount`, `pay_status`, `pay_time`, `pay_batch_no`, `pay_serial_no`, `fail_reason`, `remark`, `create_time`, `update_time`, `del_flag`, `contract_id`, `customer_id`, `archived_at` FROM `exp_bill_payment_cold`;
DROP TABLE `exp_bill_payment_cold`;
RENAME TABLE `exp_bill_payment_cold_rollback` TO `exp_bill_payment_cold`;
