-- =============================================================================
-- V1.4.81 打印/套打引擎 —— 表结构（SEED）
-- =============================================================================
-- 目的：闭环差距②「打印无套打设计器」。此前 print_template 全仓零命中，
--       5 套打印模板硬编码在 fssc-web/src/composables/usePrintTemplate.js 的
--       TEMPLATES 常量里 ⇒ 新增一种单据打印样式＝改前端代码并发版，改不了版就改不了。
--       对各业务领域几十种业务单据宜采用套打模板设计，模板是数据不是代码。
--
-- 【本件建 4 张表】
--   md_print_template      打印模板头（业务类型 + 版式参数 + 启用状态）
--   md_print_template_item 模板字段项（布局坐标 + 必录/锁定属性 + 数据源绑定）
--   md_print_template_set  模板套（一次打印多个模板的组合，如「报销单套=主单+明细+审批页」）
--   md_print_template_set_item 套内成员
--
-- 【设计要点】
--   ① 模板=数据：版式、坐标、字段属性全部写入数据库，前端只负责渲染引擎
--   ② 字段属性对齐通行规则：必录 required、锁定 locked（打印时灰显不可改）
--   ③ 数据源用 SpEL 式路径（head.xxx / lines[].yyy），与后端查询解耦
--   ④ 版本号 version + 生效状态，支持「改模板不发版」与灰度
--
-- 回滚脚本：V1.4.81_print_template_schema_rollback.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ① 打印模板头
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `md_print_template` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `template_code`   VARCHAR(64)  NOT NULL COMMENT '模板编码(全局唯一,租户内)',
  `template_name`   VARCHAR(128) NOT NULL COMMENT '模板名称',
  `biz_type`        VARCHAR(32)  NOT NULL COMMENT '业务单据类型:EXPENSE_CLAIM费用报销/CORP_PAY对公付款/CONTRACT合同/INVOICE发票/LOAN借款/VOUCHER记账凭证/AR_STATEMENT对账单/AP_REQUEST付款申请',
  `paper_size`      VARCHAR(16)  NOT NULL DEFAULT 'A4' COMMENT '纸张:A4/A5/自定义',
  `orientation`     VARCHAR(8)   NOT NULL DEFAULT 'PORTRAIT' COMMENT '方向:PORTRAIT纵向/LANDSCAPE横向',
  `width_mm`        DECIMAL(10,2) NOT NULL DEFAULT 210.00 COMMENT '宽(mm)',
  `height_mm`       DECIMAL(10,2) NOT NULL DEFAULT 297.00 COMMENT '高(mm)',
  `margin_top_mm`   DECIMAL(10,2) NOT NULL DEFAULT 15.00 COMMENT '上边距(mm)',
  `margin_bottom_mm`DECIMAL(10,2) NOT NULL DEFAULT 15.00 COMMENT '下边距(mm)',
  `margin_left_mm`  DECIMAL(10,2) NOT NULL DEFAULT 15.00 COMMENT '左边距(mm)',
  `margin_right_mm` DECIMAL(10,2) NOT NULL DEFAULT 15.00 COMMENT '右边距(mm)',
  `grid_rows`       INT          NOT NULL DEFAULT 30 COMMENT '设计网格行数(套打设计器坐标基准)',
  `grid_cols`       INT          NOT NULL DEFAULT 12 COMMENT '设计网格列数',
  `repeat_header`   TINYINT      NOT NULL DEFAULT 1 COMMENT '明细表跨页是否重复表头',
  `version`         INT          NOT NULL DEFAULT 1 COMMENT '版本号(改模板不覆盖历史版本)',
  `is_system`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否系统预置:1-是(禁止删除)0-否(租户自建)',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED启用/DISABLED停用',
  `remark`          VARCHAR(500) NULL COMMENT '备注',
  `create_by`       BIGINT       NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`       BIGINT       NULL,
  `update_time`     DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_tpl_code` (`tenant_id`,`template_code`,`version`),
  KEY `idx_pt_tenant_biz` (`tenant_id`,`biz_type`,`status`),
  KEY `idx_pt_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打印模板头（套打设计器载体，对齐行业套打平台与单据设计器口径）';

-- -----------------------------------------------------------------------------
-- ② 打印模板字段项（布局坐标 + 字段属性）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `md_print_template_item` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `template_id`   BIGINT       NOT NULL COMMENT '所属模板 md_print_template.id',
  `item_key`      VARCHAR(64)  NOT NULL COMMENT '字段标识(模板内唯一)',
  `label`         VARCHAR(128) NOT NULL COMMENT '显示标题',
  `data_source`   VARCHAR(200) NULL COMMENT '数据源路径:head.billNo / lines[].amount / system.today',
  `item_type`     VARCHAR(16)  NOT NULL DEFAULT 'FIELD' COMMENT 'FIELD字段/LINE_DETAIL明细行/QR_CODE二维码/BARCODE条码/IMAGE图片/DIVIDER分隔线',
  `row_index`     INT          NOT NULL DEFAULT 1 COMMENT '起始行(基于 template.grid_rows)',
  `col_index`     INT          NOT NULL DEFAULT 1 COMMENT '起始列(基于 template.grid_cols)',
  `row_span`      INT          NOT NULL DEFAULT 1 COMMENT '跨行',
  `col_span`      INT          NOT NULL DEFAULT 1 COMMENT '跨列',
  `width_mm`      DECIMAL(10,2) NULL COMMENT '宽(mm),为空按跨列自动均分',
  `height_mm`     DECIMAL(10,2) NULL COMMENT '高(mm)',
  `font_size`     INT          NOT NULL DEFAULT 12 COMMENT '字号(pt)',
  `font_weight`   VARCHAR(8)   NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL常规/BOLD加粗',
  `align`         VARCHAR(8)   NOT NULL DEFAULT 'LEFT' COMMENT 'LEFT/CENTER/RIGHT',
  `required`      TINYINT      NOT NULL DEFAULT 0 COMMENT '必录属性:1-必录(缺值时打印前拦截)',
  `locked`        TINYINT      NOT NULL DEFAULT 0 COMMENT '锁定属性:1-打印时灰显不可改(通行口径)',
  `visible`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示',
  `format_type`   VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT文本/NUMBER金额/DATE日期/PERCENT百分比',
  `decimal_places` INT         NOT NULL DEFAULT 2 COMMENT '小数位(金额)',
  `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `remark`        VARCHAR(500) NULL COMMENT '备注',
  `create_by`     BIGINT       NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`     BIGINT       NULL,
  `update_time`   DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pti_tpl_key` (`template_id`,`item_key`),
  KEY `idx_pti_tenant_tpl` (`tenant_id`,`template_id`),
  KEY `idx_pti_tpl_sort` (`template_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打印模板字段项（布局坐标+必录/锁定属性+数据源绑定）';

-- -----------------------------------------------------------------------------
-- ③ 模板套（一次打印多模板组合）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `md_print_template_set` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `set_code`     VARCHAR(64)  NOT NULL COMMENT '模板套编码',
  `set_name`     VARCHAR(128) NOT NULL COMMENT '模板套名称',
  `biz_type`     VARCHAR(32)  NOT NULL COMMENT '业务单据类型',
  `sort_order`   INT          NOT NULL DEFAULT 0 COMMENT '套内打印顺序',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark`       VARCHAR(500) NULL COMMENT '备注',
  `create_by`    BIGINT       NULL,
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`    BIGINT       NULL,
  `update_time`  DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pts_code` (`tenant_id`,`set_code`),
  KEY `idx_pts_tenant_biz` (`tenant_id`,`biz_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打印模板套（一次打印多模板组合，如主单+明细+审批页）';

-- -----------------------------------------------------------------------------
-- ④ 模板套成员
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `md_print_template_set_item` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`     BIGINT      NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `set_id`        BIGINT      NOT NULL COMMENT '所属模板套 md_print_template_set.id',
  `template_id`   BIGINT      NOT NULL COMMENT '成员模板 md_print_template.id',
  `page_order`    INT         NOT NULL DEFAULT 1 COMMENT '打印页序',
  `copies`        INT         NOT NULL DEFAULT 1 COMMENT '打印份数',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `del_flag`      TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ptsi_set_tpl` (`set_id`,`template_id`),
  KEY `idx_ptsi_tenant_set` (`tenant_id`,`set_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打印模板套成员';
