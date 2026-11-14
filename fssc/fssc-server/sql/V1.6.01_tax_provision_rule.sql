-- =====================================================================
-- V1.6.01 税金计提规则表+ 申报性质+ 付款单税种
-- 依赖：V1.6.00（助手过程 fssc_add_column）
-- 可重复执行：是（建表 IF NOT EXISTS；加列走助手过程）
-- 影响表：tax_provision_rule（新建）/ tax_declaration / exp_bill
-- 说明：全部为「加列/建表」，不改既有列、不动数据，可在业务运行期执行。
-- =====================================================================

-- ---------- 1. 税金计提规则 ----------
CREATE TABLE IF NOT EXISTS `tax_provision_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `tax_type` varchar(32) NOT NULL COMMENT '税种码：VAT/SURCHARGE/INCOME/IIT/STAMP/PROPERTY/LAND/VEHICLE',
  `rule_name` varchar(100) NOT NULL COMMENT '规则名（展示用）',
  `base_source` varchar(20) NOT NULL DEFAULT 'MANUAL' COMMENT '基数来源：MANUAL手工录入/DECLARATION按已申报数汇总/INVOICE按发票池已认证进项税汇总',
  `base_amount` decimal(18,2) DEFAULT NULL COMMENT '手工基数来源时的缺省计税基数',
  `tax_rate` decimal(10,4) NOT NULL COMMENT '税率（百分数，13.00 表示 13%）',
  `debit_subject_code` varchar(64) DEFAULT NULL COMMENT '计提凭证借方科目码（6403/6801）',
  `credit_subject_code` varchar(64) DEFAULT NULL COMMENT '计提凭证贷方科目码（应交税费明细）',
  `declare_credit_subject_code` varchar(64) DEFAULT '2221' COMMENT '申报凭证贷方科目码（未交税金）',
  `pay_debit_subject_code` varchar(64) DEFAULT '2221' COMMENT '缴纳（付款）凭证借方科目码',
  `period_type` varchar(16) DEFAULT 'MONTHLY' COMMENT '纳税周期：MONTHLY/QUARTERLY/HALF_YEARLY/ANNUAL/PER_TIME',
  `declare_deadline_day` int DEFAULT '15' COMMENT '申报截止日（月内第几日）',
  `pay_deadline_day` int DEFAULT '15' COMMENT '缴款截止日（月内第几日）',
  `sort` int DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/DISABLED',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_tax_type` (`tenant_id`,`tax_type`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='税金计提规则（税种/基数来源/税率/借贷科目）';

-- ---------- 2. 申报性质 + 更正关联 ----------
CALL fssc_add_column('tax_declaration', 'declaration_kind',
  "`declaration_kind` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT '申报性质：NORMAL正常申报/ADJUST更正（调整）申报' AFTER `declare_type`");
CALL fssc_add_column('tax_declaration', 'related_id',
  "`related_id` bigint DEFAULT NULL COMMENT '更正申报关联的原申报单 id' AFTER `declaration_kind`");

UPDATE `tax_declaration` SET `declaration_kind` = 'NORMAL'
 WHERE `declaration_kind` IS NULL OR `declaration_kind` = '';

-- ---------- 3. 付款单「缴纳税种 / 税期」 ----------
-- 非空即声明本笔付款是缴税：付款批次出凭证时借方按税种取应交税费(2221)明细，而不是默认 2202 应付账款。
CALL fssc_add_column('exp_bill', 'tax_type',
  "`tax_type` varchar(32) DEFAULT NULL COMMENT '缴纳税种（仅对公付款的税款付款填）：非空即按税金付款出凭证' AFTER `delivery_status`");
CALL fssc_add_column('exp_bill', 'tax_period',
  "`tax_period` varchar(6) DEFAULT NULL COMMENT '缴纳税期 yyyyMM（与 tax_type 成对）' AFTER `tax_type`");

-- ---------- 4. 校验 ----------
SELECT 'V1.6.01' AS script,
       (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_provision_rule') AS tax_rule_table,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_declaration'
           AND COLUMN_NAME IN ('declaration_kind','related_id')) AS tax_decl_cols,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exp_bill'
           AND COLUMN_NAME IN ('tax_type','tax_period')) AS exp_bill_cols;
-- 期望：tax_rule_table=1, tax_decl_cols=2, exp_bill_cols=2
