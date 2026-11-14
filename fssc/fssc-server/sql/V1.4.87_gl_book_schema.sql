-- =============================================================================
-- V1.4.87 多账簿 / 多准则 / 多本位币体系（SEED）
-- =============================================================================
-- 目的：闭环差距③「无账簿/准则/多本位币体系（多账簿零代码）」。
--       实测：309 张表里 %book% / %ledger% 零命中，只有 sys_company(9 行) + md_org_type，
--       gl_consolidate 表在但 0 行 ⇒ 现在的「总账」本质是单套复式记账的记账区，
--       不是账簿体系。参考规则：
--         账簿：「账簿类型分为主账簿和副账簿，一个核算组织在同一核算体系内仅能存在
--                   唯一的主账簿」
--         多账簿应用：「按不同的会计准则与制度出具多套财务核算数据」
--       本件把这三条落成可执行约束（唯一索引 + 服务层校验 + 校验视图）。
--
-- 【表设计】
--   gl_accounting_book       账簿（主/副，绑核算组织+准则+本位币）
--   gl_standard              会计准则（企业/小企业/政府/准则切换）
--   gl_currency              币种（本位币登记 + 与 md 汇率的关系）
--   gl_book_subject_map      账簿×科目映射（不同准则下科目体系不同 ⇒ 必须映射）
--   gl_book_balance          账簿期初余额（按年为期初）
--   gl_book_voucher          账簿记账凭证（凭证从「只有 company」升级为「落到账簿」）
--
-- 【为什么要有 gl_book_subject_map】
--   不同会计准则下科目体系不同（如企业会计准则「应收账款 1122」 vs 小企业会计准则的简表）。
--   没有映射表，「按不同准则出多套核算数据」就只能靠代码里 if-else ⇒ 回到老路。
--
-- 【零破坏边界】
--   本件只新增表，**不改 op_voucher / op_gl_entry**：
--   现有 741 张凭证仍挂在 company_id 上，book_id 先落 NULL；
--   待账簿数据验证通过后，再由 V1.4.88 做「历史凭证回填 book_id」的显式迁移。
--
-- 回滚脚本：V1.4.87_gl_book_schema_rollback.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ① 会计准则
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gl_standard` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `standard_code`  VARCHAR(32)  NOT NULL COMMENT '准则编码:ASBE企业会计准则/SME小企业会计准则/GOV政府会计制度/PUB事业单位会计',
  `standard_name`  VARCHAR(100) NOT NULL COMMENT '准则名称',
  `effective_date` DATE         NULL COMMENT '生效日期',
  `expire_date`    DATE         NULL COMMENT '失效日期(准则切换用)',
  `base_currency`  VARCHAR(8)   NOT NULL DEFAULT 'CNY' COMMENT '该准则下的本位币',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED启用/DISABLED停用',
  `remark`         VARCHAR(500) NULL COMMENT '备注',
  `create_by`      BIGINT       NULL,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`      BIGINT       NULL,
  `update_time`    DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gs_code` (`tenant_id`,`standard_code`),
  KEY `idx_gs_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计准则（多准则出多套核算数据的基础）';

-- -----------------------------------------------------------------------------
-- ② 币种
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gl_currency` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `currency_code` VARCHAR(8)  NOT NULL COMMENT '币种代码:CNY/USD/EUR/JPY/HKD',
  `currency_name` VARCHAR(50) NOT NULL COMMENT '币种名称',
  `symbol`       VARCHAR(10)  NULL COMMENT '符号',
  `scale`        INT          NOT NULL DEFAULT 2 COMMENT '小数位(JPY=0,CNY=2)',
  `rate_source`  VARCHAR(64)  NULL COMMENT '汇率来源:sys_config ar.fx.{ccy}/HMDA/手工',
  `is_base`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否本位币:1-是',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark`       VARCHAR(500) NULL COMMENT '备注',
  `create_by`    BIGINT       NULL,
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`    BIGINT       NULL,
  `update_time`  DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gcur_code` (`tenant_id`,`currency_code`),
  KEY `idx_gcur_base` (`tenant_id`,`is_base`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='币种与本位币登记';

-- -----------------------------------------------------------------------------
-- ③ 账簿（主/副）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gl_accounting_book` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `book_code`       VARCHAR(32)  NOT NULL COMMENT '账簿编码',
  `book_name`       VARCHAR(100) NOT NULL COMMENT '账簿名称',
  `book_type`       VARCHAR(16)  NOT NULL DEFAULT 'MAIN' COMMENT '账簿类型:MAIN主账簿/SUB副账簿(对齐通行口径)',
  `company_id`      BIGINT       NOT NULL COMMENT '核算组织 sys_company.id',
  `standard_id`     BIGINT       NOT NULL COMMENT '会计准则 gl_standard.id',
  `base_currency_id` BIGINT      NOT NULL COMMENT '本位币 gl_currency.id',
  `accounting_year` INT          NOT NULL COMMENT '会计年度',
  `fiscal_start`    DATE         NULL COMMENT '会计年度起始日(非1月1日时用,如4月制)',
  `fiscal_end`      DATE         NULL COMMENT '会计年度结束日',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN启用/CLOOSED已关账',
  `closed_time`     DATETIME     NULL COMMENT '关账时间',
  `remark`          VARCHAR(500) NULL COMMENT '备注',
  `create_by`       BIGINT       NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`       BIGINT       NULL,
  `update_time`     DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gab_code` (`tenant_id`,`book_code`),
  -- 【核心约束】同一核算组织 + 同一准则 + 同一年度 + 同一账面类型 ⇒ 唯一
  -- 「一个核算组织在同一核算体系内仅能存在唯一的主账簿」
  UNIQUE KEY `uk_gab_main` (`tenant_id`,`company_id`,`standard_id`,`accounting_year`,`book_type`),
  KEY `idx_gab_tenant_company` (`tenant_id`,`company_id`,`accounting_year`),
  KEY `idx_gab_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计账簿（主账簿/副账簿，含准则与本位币）';

-- -----------------------------------------------------------------------------
-- ④ 账簿×科目映射（不同准则下科目体系不同）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gl_book_subject_map` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `book_id`        BIGINT       NOT NULL COMMENT '账簿 gl_accounting_book.id',
  `standard_id`    BIGINT       NOT NULL COMMENT '准则 gl_standard.id',
  `local_subject_id` BIGINT     NOT NULL COMMENT '本准则下的科目 md_subject.id',
  `local_subject_code` VARCHAR(64) NULL COMMENT '科目编码(快照)',
  `local_subject_name` VARCHAR(200) NULL COMMENT '科目名称(快照)',
  `common_subject_id` BIGINT    NULL COMMENT '对应通用科目 md_subject.id(跨准则归一)',
  `mapping_type`   VARCHAR(16)  NOT NULL DEFAULT 'DIRECT' COMMENT 'DIRECT直接对应/RECLASS重分类/NOT_APPLICABLE本准则不适用',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark`         VARCHAR(500) NULL COMMENT '备注(差异原因)',
  `create_by`      BIGINT       NULL,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`      BIGINT       NULL,
  `update_time`    DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gbsm_book_subject` (`book_id`,`local_subject_id`),
  KEY `idx_gbsm_standard` (`tenant_id`,`standard_id`),
  KEY `idx_gbsm_common` (`tenant_id`,`common_subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账簿×科目映射（多准则出多套核算数据的桥梁）';

-- -----------------------------------------------------------------------------
-- ⑤ 账簿期初余额
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gl_book_balance` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `book_id`     BIGINT       NOT NULL COMMENT '账簿 gl_accounting_book.id',
  `accounting_year` INT      NOT NULL COMMENT '会计年度',
  `period`      VARCHAR(8)   NOT NULL DEFAULT '0000' COMMENT '期间 yyyyMM；0000=年度期初',
  `subject_id`  BIGINT       NOT NULL COMMENT '科目 md_subject.id',
  `currency_id` BIGINT       NOT NULL COMMENT '币种 gl_currency.id',
  `direction`   VARCHAR(8)   NOT NULL DEFAULT 'DEBIT' COMMENT '余额方向:DEBIT借/CREDIT贷',
  `begin_balance` DECIMAL(20,2) NOT NULL DEFAULT 0 COMMENT '期初余额',
  `begin_qty`   DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT '期初数量(数量核算科目)',
  `period_begin` DECIMAL(20,2) NOT NULL DEFAULT 0 COMMENT '本期期初',
  `period_debit` DECIMAL(20,2) NOT NULL DEFAULT 0 COMMENT '本期借方发生',
  `period_credit` DECIMAL(20,2) NOT NULL DEFAULT 0 COMMENT '本期贷方发生',
  `period_end`  DECIMAL(20,2) NOT NULL DEFAULT 0 COMMENT '本期期末',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL正常',
  `create_by`   BIGINT       NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`   BIGINT       NULL,
  `update_time` DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gbb_book_period_subject` (`book_id`,`period`,`subject_id`,`currency_id`),
  KEY `idx_gbb_tenant_book` (`tenant_id`,`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账簿科目余额（期初/发生/期末，多账簿各自独立）';

-- -----------------------------------------------------------------------------
-- ⑥ 账簿记账凭证（从 company 升级到 book）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gl_book_voucher` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `book_id`        BIGINT       NOT NULL COMMENT '账簿 gl_accounting_book.id',
  `voucher_no`     VARCHAR(64)  NOT NULL COMMENT '凭证号(账簿内唯一)',
  `voucher_word`   VARCHAR(20)  NOT NULL DEFAULT 'JOURNAL' COMMENT '凭证字:JOURNAL记/RECEIPT收/PAYMENT付/TRANSFER转',
  `accounting_year` INT         NOT NULL COMMENT '会计年度',
  `period`         VARCHAR(8)   NOT NULL COMMENT '会计期间 yyyyMM',
  `voucher_date`   DATE         NOT NULL COMMENT '凭证日期',
  `source_type`    VARCHAR(32)  NOT NULL COMMENT '来源类型(与 op_voucher.source_type 同口径)',
  `source_doc_type` VARCHAR(32) NULL COMMENT '来源单据类型(溯源)',
  `source_doc_id`  BIGINT       NULL COMMENT '来源单据ID(溯源)',
  `summary`        VARCHAR(500) NULL COMMENT '凭证摘要',
  `debit_total`    DECIMAL(20,2) NOT NULL DEFAULT 0 COMMENT '借方合计(本位币)',
  `credit_total`   DECIMAL(20,2) NOT NULL DEFAULT 0 COMMENT '贷方合计(本位币)',
  `is_balanced`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否借贷平衡',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'POSTED' COMMENT 'DRAFT草稿/POSTED已记账/REVERSED已冲销',
  `post_user_id`   BIGINT       NULL COMMENT '记账人',
  `post_time`      DATETIME     NULL COMMENT '记账时间',
  `remark`         VARCHAR(500) NULL,
  `create_by`      BIGINT       NULL,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`      BIGINT       NULL,
  `update_time`    DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gbv_book_no` (`book_id`,`voucher_no`),
  KEY `idx_gbv_tenant_book_period` (`tenant_id`,`book_id`,`accounting_year`,`period`),
  KEY `idx_gbv_source` (`tenant_id`,`source_doc_type`,`source_doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账簿记账凭证（多账簿各记各账的载体）';

-- -----------------------------------------------------------------------------
-- ⑦ 账簿记账凭证分录
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gl_book_voucher_line` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `book_voucher_id` BIGINT     NOT NULL COMMENT '凭证 gl_book_voucher.id',
  `line_no`       INT          NOT NULL DEFAULT 1 COMMENT '分录行号',
  `summary`       VARCHAR(500) NULL COMMENT '摘要',
  `subject_id`    BIGINT       NOT NULL COMMENT '科目 md_subject.id（账簿口径，已过准则映射）',
  `subject_code`  VARCHAR(64)  NULL COMMENT '科目编码快照',
  `subject_name`  VARCHAR(200) NULL COMMENT '科目名称快照',
  `direction`     VARCHAR(10)  NOT NULL DEFAULT 'DEBIT' COMMENT 'DEBIT借/CREDIT贷',
  `currency_id`   BIGINT       NULL COMMENT '币种(本位币时为空)',
  `orig_amount`   DECIMAL(20,2) NOT NULL DEFAULT 0 COMMENT '原币金额',
  `exchange_rate` DECIMAL(20,10) NOT NULL DEFAULT 1 COMMENT '汇率(原币→本位币)',
  `base_amount`   DECIMAL(20,2) NOT NULL DEFAULT 0 COMMENT '本位币金额',
  `dept_id`       BIGINT       NULL COMMENT '辅助核算-部门',
  `project_id`    BIGINT       NULL COMMENT '辅助核算-项目',
  `customer_id`   BIGINT       NULL COMMENT '辅助核算-客户',
  `supplier_id`   BIGINT       NULL COMMENT '辅助核算-供应商',
  `employee_id`   BIGINT       NULL COMMENT '辅助核算-员工',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_gbvl_voucher` (`book_voucher_id`,`line_no`),
  KEY `idx_gbvl_subject` (`tenant_id`,`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账簿记账凭证分录（本位币金额 + 辅助核算）';
