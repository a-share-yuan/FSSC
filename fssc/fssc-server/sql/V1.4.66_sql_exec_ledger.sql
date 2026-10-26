-- =============================================================================
-- V1.4.66 SQL 执行台账 sys_sql_exec（6-A）
--
-- 目的：`fssc-server/sql/` 下96 支手工脚本**没有任何"是否已执行"的记录**。
--       换机器重建库时，只能靠人记哪支跑过；本件建立最小对账机制（不引 Flyway —— 已裁决）。
--
-- 【设计边界】
--   · **不引 Flyway/Liquibase**：保持"手工 SQL + 台账"的既定路线，只补记账能力；
--   · **不改任何既有脚本**：台账由执行器（`scripts/_apply_sql.py`）在写入数据库时写入，
--     脚本本身保持纯 SQL、可单独复制到客户端执行；
--   · checksum 记 MD5，用于识别"同名脚本事后被改过"（脚本改了但 checksum 未变＝没重新执行）。
--
-- 【唯一写入位置】
--   `fssc/scripts/_apply_sql.py --commit`，每条语句执行后在同一事务内 INSERT 一行。
--   手工在客户端跑的脚本**不会**进台账 ⇒ `_sql_diff.py` 会把它们报成"未执行"，
--   这正是需要人工确认的信号（不是 bug）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS fssc.sys_sql_exec (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id       BIGINT       NULL COMMENT '租户ID（DDL 类脚本为 NULL）',
  script_name     VARCHAR(128) NOT NULL COMMENT '脚本文件名（含 _seed/_rollback 后缀）',
  version_tag     VARCHAR(32)  NULL COMMENT '版本号（V1.4.66 →1.4.66）',
  stmt_index      INT          NOT NULL COMMENT '脚本内第几条语句（从1 开始）',
  stmt_digest     CHAR(16)     NULL COMMENT '语句内容 MD4/MD5 前 16 位（定位改动）',
  rows_affected   INT          NOT NULL DEFAULT 0 COMMENT '影响行数',
  status          VARCHAR(16)  NOT NULL DEFAULT 'OK' COMMENT 'OK / FAILED',
  error_msg       VARCHAR(500) NULL COMMENT '失败原因',
  executed_by     VARCHAR(64)  NULL COMMENT '执行人（OS 用户）',
  checksum        CHAR(32)     NULL COMMENT '整份脚本内容 MD5',
  executed_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_script_stmt (script_name, stmt_index),
  KEY idx_script (script_name),
  KEY idx_version (version_tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL 脚本执行台账（手工迁移对账用）';

-- ----------------------------------------------------------------------------
-- §1 复点：期望表存在且0 行
-- ----------------------------------------------------------------------------
-- SELECT COUNT(*) FROM fssc.sys_sql_exec;
--
-- §2 说明：本脚本自身不入台账（它是建表脚本），
--    由 scripts/_apply_sql.py 以 --bootstrap 方式登记一次，见脚本头注释。