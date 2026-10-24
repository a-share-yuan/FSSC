-- =============================================================================
-- V1.4.92 档案管理模式：公司级模式载体 + 归档原件存放地两列
-- =============================================================================
-- 来源：Ashare/人工测试/档案集中管理模式开关_非集中模式流程改造方案_20261024.md
--       AMC-01（模式载体放公司维度）、AMC-05（保留原件存放地最小登记）
--
-- 本件只做**零行为变更的可逆加列**：
--   1) sys_company.archive_mode      默认 CENTRAL ⇒ 改造前后所有公司行为不变；
--   2) op_archive.paper_keeper_company_id / paper_location  两列全 NULL，当前 0 行。
-- 不包含任何模式切换动作（wf_process_config 的 company_id 路由、SCAN_STAFF 授权收放、
-- bill.completeness 键改写）——那三条各有人工前置依赖，见方案 §5 与 §7，
-- 未点名非集中公司、_NC 模型未建之前不得执行。
--
--  表名更正：本方案初稿把公司主体表写成 md_company，实测库内无此表；
--   公司主体表为 sys_company（SysCompany.java @TableName("sys_company")），
--   本件按实测表名写。
--
-- 执行前基线（2026-10-24 07:57 即时复点，只读）：
--   sys_company          9 行（del_flag=0），无 archive_mode 列；档案/邮寄/快递/扫描维列 0 命中
--                        （仅 address＝注册地址，不承担原件存放地语义）
--   op_archive           0 行，无 paper_* 列
--   exp_bill / wf_instance / op_audit_task / op_physical_delivery / op_barcode  均 0 行
--   sys_config 归档键    archive.enabled=0 / archive.cold_months=12 / archive.terminal_status=…
--    本库 2026-10-23 20:03~20:23 曾被整库重建，任何跨窗引用都须临执行前重跑上面的读数。
--
-- 幂等：逐列按 information_schema 存在性护栏，重复执行不报错、不重复加列。
-- 回滚：V1.4.92_archive_mode_rollback.sql（仅当三列均为缺省值时才允许 DROP）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 步骤 0：执行前只读复点（对不上就停，不要往下跑）
-- -----------------------------------------------------------------------------
SELECT 'A company_col_exists(expect 0)' AS chk,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_company'
           AND COLUMN_NAME = 'archive_mode') AS v
UNION ALL
SELECT 'B archive_paper_cols(expect 0)', (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
           AND COLUMN_NAME IN ('paper_keeper_company_id','paper_location'))
UNION ALL
SELECT 'C sys_company_rows(expect 9)', (SELECT COUNT(*) FROM sys_company WHERE del_flag = 0)
UNION ALL
SELECT 'D op_archive_rows(expect 0)',  (SELECT COUNT(*) FROM op_archive);

-- -----------------------------------------------------------------------------
-- 步骤 1：sys_company.archive_mode（公司级模式载体，默认 CENTRAL＝改造前行为）
-- -----------------------------------------------------------------------------
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_company'
      AND COLUMN_NAME = 'archive_mode') = 0,
  'ALTER TABLE `sys_company` ADD COLUMN `archive_mode` VARCHAR(16) NOT NULL DEFAULT ''CENTRAL''
     COMMENT ''档案管理模式：CENTRAL 集中（纸质原件寄送共享中心）/ NON_CENTRAL 非集中（原件留原单位，不产生实物流）''
     AFTER `remark`',
  'SET @noop := 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 步骤 2：op_archive 原件存放地两列（非集中模式下由原单位登记；集中模式留空）
-- -----------------------------------------------------------------------------
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
      AND COLUMN_NAME = 'paper_keeper_company_id') = 0,
  'ALTER TABLE `op_archive` ADD COLUMN `paper_keeper_company_id` BIGINT NULL
     COMMENT ''原件保管单位（sys_company.id）；非集中模式必填，集中模式为空''
     AFTER `proc_inst_id`',
  'SET @noop := 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
      AND COLUMN_NAME = 'paper_location') = 0,
  'ALTER TABLE `op_archive` ADD COLUMN `paper_location` VARCHAR(200) NULL
     COMMENT ''原件存放位置（非集中模式的原单位库房/柜位）''
     AFTER `paper_keeper_company_id`',
  'SET @noop := 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 步骤 3：执行后复点（三列齐备、默认值分布正确）
-- -----------------------------------------------------------------------------
SELECT 'E company_col_exists(expect 1)' AS chk,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_company'
           AND COLUMN_NAME = 'archive_mode') AS v
UNION ALL
SELECT 'F archive_paper_cols(expect 2)', (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
           AND COLUMN_NAME IN ('paper_keeper_company_id','paper_location'))
UNION ALL
SELECT 'G non_central_rows(expect 0)', (SELECT COUNT(*) FROM sys_company WHERE archive_mode <> 'CENTRAL')
UNION ALL
SELECT 'H null_mode_rows(expect 0)',   (SELECT COUNT(*) FROM sys_company WHERE archive_mode IS NULL)
UNION ALL
SELECT 'I paper_keeper_filled(expect 0)', (SELECT COUNT(*) FROM op_archive WHERE paper_keeper_company_id IS NOT NULL)
UNION ALL
SELECT 'J paper_location_filled(expect 0)', (SELECT COUNT(*) FROM op_archive WHERE paper_location IS NOT NULL);
