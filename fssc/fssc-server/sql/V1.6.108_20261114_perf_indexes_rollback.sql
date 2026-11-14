-- =====================================================================================
-- V1.6.108_20261114_perf_indexes_rollback.sql
-- 回滚 V1.6.108_20261114_perf_indexes.sql 建立的三条索引。
--
-- 何时才该回滚：索引导致写入明显变慢、或磁盘空间不足。
-- 注意：这三条都是「读多写少」列表的过滤维度，删掉只会让查询变慢，不会让数据出错；
--       删除前请确认不是因为别的原因（如统计信息陈旧）误判。
-- =====================================================================================

SET @sql := (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE op_voucher DROP INDEX idx_tenant_del_status, ALGORITHM=INPLACE, LOCK=NONE',
              'SELECT ''skip: idx_tenant_del_status not exists'' AS note')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'op_voucher' AND index_name = 'idx_tenant_del_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE op_voucher DROP INDEX idx_tenant_del_source, ALGORITHM=INPLACE, LOCK=NONE',
              'SELECT ''skip: idx_tenant_del_source not exists'' AS note')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'op_voucher' AND index_name = 'idx_tenant_del_source'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE rc_receivable DROP INDEX idx_tenant_company_del, ALGORITHM=INPLACE, LOCK=NONE',
              'SELECT ''skip: idx_tenant_company_del not exists'' AS note')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'rc_receivable' AND index_name = 'idx_tenant_company_del'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT 'rollback done' AS result;
