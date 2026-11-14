-- =============================================================================
-- V1.4.92 回滚：撤掉档案管理模式的三个加列
-- =============================================================================
-- 对应正向件：V1.4.92_archive_mode.sql
--   sys_company.archive_mode
--   op_archive.paper_keeper_company_id
--   op_archive.paper_location
--
-- 安全校验：只在「三列都没有承载任何业务选择」时才 DROP ——
--   archive_mode 全为 'CENTRAL' 且无 NULL；
--   paper_keeper_company_id / paper_location 全为 NULL。
-- 任一条不满足即跳过该列的 DROP 并打印计数，交由人工判断（DROP 会丢已登记的原件存放地，
-- 不可逆；本件不会替你决定）。
--
-- 前置：回滚须与**代码环节同步回退**。代码一旦读取 archive_mode／paper_*，先撤代码再跑本件，
--       否则运行期列缺失会直接报错。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 步骤 1：sys_company.archive_mode
-- -----------------------------------------------------------------------------
SET @e := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_company'
              AND COLUMN_NAME = 'archive_mode');
SET @chk := IF(@e = 0, 'SET @u := 0',
  'SET @u := (SELECT COUNT(*) FROM sys_company WHERE archive_mode <> ''CENTRAL'' OR archive_mode IS NULL)');
PREPARE stmt FROM @chk; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SELECT 'archive_mode' AS col, @e AS col_exists, @u AS unsafe_rows;
SET @sql := IF(@e = 0 OR @u > 0, 'SET @noop := 0',
  'ALTER TABLE `sys_company` DROP COLUMN `archive_mode`');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 步骤 2：op_archive.paper_keeper_company_id
-- -----------------------------------------------------------------------------
SET @e := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
              AND COLUMN_NAME = 'paper_keeper_company_id');
SET @chk := IF(@e = 0, 'SET @u := 0',
  'SET @u := (SELECT COUNT(*) FROM op_archive WHERE paper_keeper_company_id IS NOT NULL)');
PREPARE stmt FROM @chk; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SELECT 'paper_keeper_company_id' AS col, @e AS col_exists, @u AS unsafe_rows;
SET @sql := IF(@e = 0 OR @u > 0, 'SET @noop := 0',
  'ALTER TABLE `op_archive` DROP COLUMN `paper_keeper_company_id`');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 步骤 3：op_archive.paper_location
-- -----------------------------------------------------------------------------
SET @e := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
              AND COLUMN_NAME = 'paper_location');
SET @chk := IF(@e = 0, 'SET @u := 0',
  'SET @u := (SELECT COUNT(*) FROM op_archive WHERE paper_location IS NOT NULL)');
PREPARE stmt FROM @chk; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SELECT 'paper_location' AS col, @e AS col_exists, @u AS unsafe_rows;
SET @sql := IF(@e = 0 OR @u > 0, 'SET @noop := 0',
  'ALTER TABLE `op_archive` DROP COLUMN `paper_location`');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 步骤 4：复点（三列应全部为 0）
-- -----------------------------------------------------------------------------
SELECT 'K company_col_exists(expect 0)' AS chk,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_company'
           AND COLUMN_NAME = 'archive_mode') AS v
UNION ALL
SELECT 'L archive_paper_cols(expect 0)', (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
           AND COLUMN_NAME IN ('paper_keeper_company_id','paper_location'));
