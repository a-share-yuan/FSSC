-- =============================================================================
-- V1.4.85 元数据驱动 —— 字段级元数据（SEED）
-- =============================================================================
-- 目的：闭环差距①「单据/界面不是元数据驱动，新增一种单据＝改代码」。
--       现状实查：md_form_config 13 行在库，但 config_json 只有 {"sections":[...]} 这种
--       **分节名**，没有任何字段定义 —— 表是死的，前端零消费（V1.4.85 前 Java 仅 3 处引用：
--       实体 + Mapper + Controller）。
--       行业方案 bos 平台的口径是「业务对象设计、界面定义、流程定义、业务规则设计、报表设计、
--       部署等环节」，且「支持业务单据的布局、字段必录和锁定属性、业务流程以及打印的设置」。
--
-- 【为什么不全塞进 config_json】
--   JSON blob 里的字段定义没法被 SQL 查、没法做权限矩阵、没法被引擎复用。
--   拆成「表单头 + 字段项」两张表后：
--     · 字段必录/锁定/布局可被服务端校验引擎直接读（不靠前端自觉）
--     · 字段权限矩阵可以对字段项做 JOIN（闭环差距⑤）
--     · 新增一种单据的字段＝插数据，不改代码
--
-- 【本件建 2 张表】
--   md_form_field      字段元数据（控件类型/布局/必录/锁定/校验规则/默认值）
--   md_form_validation 校验规则（支持多级：必录/数值范围/正则/表达式/跨字段）
--
-- 【与已有表的关系】
--   md_form_config（已有，13 行）＝ 表单头，指向本表的字段集合；不改动它，
--   只在本表按 form_code 建字段，使它从「死登记」变成真被消费的配置。
--
-- 回滚脚本：V1.4.85_form_field_schema_rollback.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ① 字段元数据
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `md_form_field` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`        BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `form_code`        VARCHAR(50)  NOT NULL COMMENT '所属表单 md_form_config.form_code',
  `field_key`        VARCHAR(64)  NOT NULL COMMENT '字段标识(前端 key + 后端实体属性名)',
  `field_label`      VARCHAR(100) NOT NULL COMMENT '字段显示名',
  `db_column`        VARCHAR(64)  NULL COMMENT '数据库列名(为空则不落库,仅展示/计算字段)',
  `section`          VARCHAR(64)  NULL COMMENT '所属分节(对应 config_json.sections 之一)',
  `widget_type`      VARCHAR(24)  NOT NULL DEFAULT 'INPUT' COMMENT '控件:INPUT/INPUT_NUMBER/TEXTAREA/SELECT/DATE/DATETIME/MONEY/PERCENT/SWITCH/UPLOAD/AMOUNT_LIST/USER_SELECT/DEPT_SELECT/REF',
  `control_width`    VARCHAR(16)  NOT NULL DEFAULT 'FULL' COMMENT '控件宽度:FULL/HALF/THIRD/QUARTER',
  `span`             INT          NOT NULL DEFAULT 12 COMMENT '栅格跨列(24栅格制)',
  `sort_order`       INT          NOT NULL DEFAULT 0 COMMENT '字段顺序',
  `placeholder`      VARCHAR(200) NULL COMMENT '输入提示',
  `help_text`        VARCHAR(500) NULL COMMENT '字段说明',
  `default_value`    VARCHAR(500) NULL COMMENT '默认值',
  `options_json`     TEXT         NULL COMMENT '选项配置 JSON(SELECT 的选项树/参照表查询)',
  `required`         TINYINT      NOT NULL DEFAULT 0 COMMENT '必录属性:1-必录(对应行业方案"字段必录")',
  `locked`           TINYINT      NOT NULL DEFAULT 0 COMMENT '锁定属性:1-编辑时灰显不可改(对应行业方案"锁定属性")',
  `readonly_roles`   VARCHAR(500) NULL COMMENT '只读角色码(逗号分隔;命中角色即只读)',
  `visible_roles`    VARCHAR(500) NULL COMMENT '可见角色码(逗号分隔;空=全部可见)',
  `unique_flag`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否唯一键',
  `system_flag`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否系统字段:1-是(禁删)',
  `status`           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark`           VARCHAR(500) NULL COMMENT '备注',
  `create_by`        BIGINT       NULL,
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`        BIGINT       NULL,
  `update_time`      DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ff_form_key` (`form_code`,`field_key`),
  KEY `idx_ff_tenant_form` (`tenant_id`,`form_code`,`sort_order`),
  KEY `idx_ff_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单字段元数据（控件/布局/必录/锁定/选项，新增单据字段＝插数据）';

-- -----------------------------------------------------------------------------
-- ② 字段校验规则（一条字段可配多条规则）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `md_form_validation` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `form_code`     VARCHAR(50)  NOT NULL COMMENT '所属表单',
  `field_key`     VARCHAR(64)  NOT NULL COMMENT '字段标识',
  `rule_type`     VARCHAR(24)  NOT NULL COMMENT '规则类型:REQUIRED必录/NOT_NULL非空/MIN_VALUE最小值/MAX_VALUE最大值/MIN_LENGTH最小长度/MAX_LENGTH最大长度/PATTERN正则/EMAIL邮箱/PHONE手机号/ID_CARD身份证/UNIQUE唯一/CUSTOM_EXPRESSION自定义表达式/CROSS_FIELD跨字段比对',
  `rule_value`    VARCHAR(500) NULL COMMENT '规则参数(如正则表达式/最小值)',
  `rule_value2`   VARCHAR(500) NULL COMMENT '第二参数(跨字段比对时的另一字段)',
  `error_message` VARCHAR(300) NULL COMMENT '校验失败文案(为空则按规则类型自动生成)',
  `severity`      VARCHAR(16)  NOT NULL DEFAULT 'ERROR' COMMENT 'ERROR阻断提交/WARN仅告警',
  `scope`         VARCHAR(16)  NOT NULL DEFAULT 'SAVE' COMMENT '生效时机:SAVE保存时/APPROVE审批时/BOTH',
  `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '执行顺序(对齐检查点配置的"执行顺序")',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark`        VARCHAR(500) NULL COMMENT '备注',
  `create_by`     BIGINT       NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`     BIGINT       NULL,
  `update_time`   DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fv_form_field_rule` (`form_code`,`field_key`,`rule_type`),
  KEY `idx_fv_tenant_form` (`tenant_id`,`form_code`),
  KEY `idx_fv_tenant_field` (`tenant_id`,`form_code`,`field_key`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单字段校验规则（对齐行业检查点配置：校验规则/执行顺序/不通过处理）';
