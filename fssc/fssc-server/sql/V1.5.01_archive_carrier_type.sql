-- =============================================================================
-- V1.5.01  ARC-M4 前置：案卷载体类型列（纸质／电子）
-- 依据：fssc/docs/档案归档闭环方案_20261028.md §10-6 三（"M4 未做的一半"）
--       fssc/docs/ARC-M4_纸质卷必填闸_实施方案_待放行_20261028.md
--
-- 为什么必须有这一列：DA/T 22 的"编页"只对**纸质**卷成立（装订后按页连续）；
--   电子卷以"件/文件"为最小单位，强编页号等于让档案员对 PDF 编号，属错误要求。
--   现 op_archive 无任何载体字段 ⇒ 只能做"全编或全不编"的一致性校验，无法只对纸质卷强制必填。
--
-- 语义：PAPER 纸质（原件保管，归档前必须逐件编页且自第 1 页连续）
--       ELECTRONIC 电子（以文件计数，不强制编页；若编了仍须一致连续）
--
-- 默认值取舍：DEFAULT 'PAPER'。理由是现库 5 卷全部来自报销单影像腿/人工立卷（原件为纸质），
--   默认取"严格侧"不会放过未编页的纸质卷；若某卷实为电子，迁移后逐卷改 ELECTRONIC 即可。
--   反过来（默认 ELECTRONIC）会静默放过全部历史纸质卷，等于闸门先自废一半。
--
--  本件写入过 0 次（编制时未连接数据库）。执行条件：载体类型字段属"新列＝新迁移"，
--   须与后端代码同窗发布（代码先读该列，缺列会 500）——先执行本件，再上代码。
-- 回滚：V1.5.01_archive_carrier_type_rollback.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 步骤 0：执行前只读复点（A 期望 0＝列不存在；B＝在册卷数）
-- -----------------------------------------------------------------------------
SELECT 'A carrier_col_exists(expect 0)' AS chk,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
           AND COLUMN_NAME = 'carrier_type') AS v
UNION ALL
SELECT 'B op_archive_rows', (SELECT COUNT(*) FROM op_archive WHERE del_flag = 0)
UNION ALL
SELECT 'C archive_status_split', (SELECT COUNT(*) FROM op_archive WHERE del_flag = 0 AND archive_status = 'ARCHIVED');

-- -----------------------------------------------------------------------------
-- 步骤 1：加列（information_schema 护栏，重复执行不报错）
-- -----------------------------------------------------------------------------
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
      AND COLUMN_NAME = 'carrier_type') = 0,
  'ALTER TABLE `op_archive` ADD COLUMN `carrier_type` VARCHAR(16) NOT NULL DEFAULT ''PAPER''
     COMMENT ''载体类型：PAPER 纸质（原件保管，归档前必须逐件编页）/ ELECTRONIC 电子（按件计数，不强制编页）''
     AFTER `archive_type`',
  'SET @noop := 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 步骤 2：执行后复点（D 期望 1；E 期望 0＝无空值；F 期望 ＝ B）
-- -----------------------------------------------------------------------------
SELECT 'D carrier_col_exists(expect 1)' AS chk,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_archive'
           AND COLUMN_NAME = 'carrier_type') AS v
UNION ALL
SELECT 'E null_carrier(expect 0)', (SELECT COUNT(*) FROM op_archive WHERE del_flag = 0 AND carrier_type IS NULL)
UNION ALL
SELECT 'F paper_rows(expect = B)', (SELECT COUNT(*) FROM op_archive WHERE del_flag = 0 AND carrier_type = 'PAPER')
UNION ALL
SELECT 'G bad_value(expect 0)', (SELECT COUNT(*) FROM op_archive WHERE del_flag = 0 AND carrier_type NOT IN ('PAPER','ELECTRONIC'));

-- -----------------------------------------------------------------------------
-- 步骤 3：（按卷订正，可选）确认某卷实为电子档后逐卷改，不要整表 UPDATE
--   UPDATE op_archive SET carrier_type = 'ELECTRONIC', update_time = NOW()
--    WHERE id = <卷ID> AND archive_status IN ('PENDING','ARCHIVING');
--   （已 ARCHIVED 的卷改载体类型会再次打破完整性哈希语义外的元数据，需先与签认单一起裁）
-- -----------------------------------------------------------------------------
