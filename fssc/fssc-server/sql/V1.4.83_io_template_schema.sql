-- =============================================================================
-- V1.4.83 统一导入导出引擎 —— 表结构（SEED）
-- =============================================================================
-- 目的：闭环差距②后半「导入导出无统一引擎」。
--       此前 fssc-server 内有 6 支手写 *ExcelService
--       （ApPlanExcelService / ArReceiptExcelService / ArOffsetExcelService /
--         ArStatementExcelService / BudgetItemExcelService / RcBadDebtExcelService），
--       每支各自硬编码列头数组、各自写一遍 POI 解析、各自决定错误文案，
--       新增一种导入模板 = 复制一支 Service 再改列头 ⇒ 必然长出 N+1 支。
--       财会〔2024〕12号 §7 要求「提供易于理解的标准数据接口」，
--       同类系统的做法是「勾选允许引入…引入模板在系统后台自动生成」——
--       即模板也是数据，导入引擎按模板定义跑，不需要每种模板写代码。
--
-- 【本件建 2 张表】
--   md_io_template        导入导出模板头（业务类型 + 方向 + 版本 + 字段定义）
--   md_io_template_field  字段定义（列头文案 ↔ 数据库字段的映射与转换规则）
--
-- 【关键设计】
--   ① 列头文案与库字段解耦：改中文列头不碰代码（对齐 E1 对账模板口径漂移的痛点）
--   ② 转换规则声明式：DATE/NUMBER/ENUM/DICT/REF/EXPR，引擎统一执行
--   ③ 模板版本化 + 启用状态，支持「换模板不发版」
--   ④ 字典引用 dictType 让下拉值在导入时也能校验（如单据状态枚举）
--
-- 回滚脚本：V1.4.83_io_template_schema_rollback.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ① 导入导出模板头
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `md_io_template` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `template_code`   VARCHAR(64)  NOT NULL COMMENT '模板编码(租户内唯一,含版本)',
  `template_name`   VARCHAR(128) NOT NULL COMMENT '模板名称',
  `biz_type`        VARCHAR(32)  NOT NULL COMMENT '业务类型:AR_STATEMENT对账单/AR_RECEIPT收款核销/AR_OFFSET对冲/AP_PLAN付款计划/BUDGET_ITEM预算项目/RC_BAD_DEBT坏账',
  `direction`       VARCHAR(8)   NOT NULL DEFAULT 'IMPORT' COMMENT '方向:IMPORT导入/EXPORT导出/BOTH双向',
  `target_table`    VARCHAR(64)  NULL COMMENT '落库目标表(EXPORT 时为目标表名,便于导出取数)',
  `sheet_name`      VARCHAR(64)  NOT NULL DEFAULT 'Sheet1' COMMENT 'Excel 工作表名',
  `header_row`      INT          NOT NULL DEFAULT 1 COMMENT '表头所在行(1-based,允许前导说明行)',
  `max_rows`        INT          NOT NULL DEFAULT 10000 COMMENT '单次最大数据行数(防打爆内存)',
  `version`         INT          NOT NULL DEFAULT 1 COMMENT '版本号',
  `is_system`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否系统预置:1-是 0-否',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED启用/DISABLED停用',
  `remark`          VARCHAR(500) NULL COMMENT '备注',
  `create_by`       BIGINT       NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`       BIGINT       NULL,
  `update_time`     DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_io_code` (`tenant_id`,`template_code`,`version`),
  KEY `idx_io_tenant_biz` (`tenant_id`,`biz_type`,`direction`,`status`),
  KEY `idx_io_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入导出模板头（声明式模板，替代每模块一支手写ExcelService）';

-- -----------------------------------------------------------------------------
-- ② 模板字段定义（列头 ↔ 库字段映射 + 转换规则）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `md_io_template_field` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `template_id`   BIGINT       NOT NULL COMMENT '所属模板 md_io_template.id',
  `field_key`     VARCHAR(64)  NOT NULL COMMENT '库字段名(实体属性名,驼峰)',
  `column_name`   VARCHAR(128) NOT NULL COMMENT 'Excel 列头文案(用户可见)',
  `column_type`   VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT '数据类型:STRING/NUMBER/DATE/DATETIME/BOOLEAN/ENUM',
  `db_column`     VARCHAR(64)  NULL COMMENT '数据库列名(默认取 field_key 驼峰转下划线)',
  `required`      TINYINT      NOT NULL DEFAULT 0 COMMENT '必录:1-是(缺值该行报错)',
  `dict_type`     VARCHAR(64)  NULL COMMENT '字典类型(ENUM 时校验取值,如 bill_status)',
  `enum_values`   VARCHAR(500) NULL COMMENT '枚举可选值(逗号分隔,ENUM 且无字典时生效)',
  `ref_table`     VARCHAR(64)  NULL COMMENT '参照表(如 REF 类型:按 code 查 name 回填 id)',
  `ref_code_col`  VARCHAR(64)  NULL COMMENT '参照表的编码列',
  `ref_name_col`  VARCHAR(64)  NULL COMMENT '参照表的名称列',
  `default_value` VARCHAR(200) NULL COMMENT '缺省值(该列为空时写入)',
  `max_length`    INT          NULL COMMENT '最大长度(超长报错,替代各 Service 手写校验)',
  `decimal_scale` INT          NULL COMMENT '小数位(金额列)',
  `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '列序',
  `remark`        VARCHAR(500) NULL COMMENT '备注/取值说明(导入失败时回显给用户)',
  `create_by`     BIGINT       NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`     BIGINT       NULL,
  `update_time`   DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iof_tpl_key` (`template_id`,`field_key`),
  KEY `idx_iof_tenant_tpl` (`tenant_id`,`template_id`),
  KEY `idx_iof_tpl_sort` (`template_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入导出模板字段定义（列头文案↔库字段映射+声明式转换规则）';
