-- =====================================================================================
-- V1.6.108_20261114_perf_indexes.sql
-- 目的：把压测库 fssc_perf 上实测确认的三条索引落到主库/生产库。
-- 性质：纯 DDL（只加索引，不改数据、不改列），可重复执行（已存在则跳过），可在线执行。
--
-- 依据（2026-11-14 实测，fssc_perf：op_voucher 1,024,002 行 / rc_receivable 89,200 行）：
--
--   ① op_voucher(tenant_id, del_flag, status)
--      现状主库只有 idx_tenant_del(tenant_id, del_flag)，status 无索引 ⇒ 优化器为了找
--      非主流状态（DRAFT 仅 2.5%）只能回表逐行过滤：
--        · 台账按 status=DRAFT 取首页      ：89.7ms → 0.66ms   （136 倍）
--        · COUNT(*) ... status='DRAFT'    ：1,820ms → 13.6ms  （134 倍）
--        · COUNT(*) ... status='POSTED'   ：4,617ms → 513ms   （ 9 倍，97% 行仍是 O(N)）
--      索引体积 ≈ 31.6 MB / 百万张凭证。
--
--   ② op_voucher(tenant_id, del_flag, source_type)
--      台账"来源族"页签按 source_type 过滤，该列原本完全无索引：
--        · 单值列表（source_type='AP_REQUEST'）      ：55.9ms → 1.8ms  （ 31 倍）
--        · COUNT(*) 单值                              ：2,302ms → 76.8ms（ 30 倍）
--      注意：多值 IN ('A','B') 时 ORDER BY id DESC 仍要合并两段范围后排序，
--            不加速（实测 83ms → 82ms），这不是索引失效，是跨范围排序的固有代价。
--      索引体积 ≈ 35.6 MB / 百万张凭证。
--
--   ③ rc_receivable(tenant_id, company_id, del_flag)
--      收款核销列表按公司维度过滤（RcReceivableService.page 的 eq(companyId)），
--      以及 RcReceiptService 的公司归属派生 SQL，都要靠 company_id 定位。
--      主库原本只有 tenant_id 索引 ⇒ 每次都把全租户应收扫一遍：
--        · COUNT(*) ... company_id=900000032（命中 653 行）：283.6ms → 1.36ms（208 倍）
--      索引体积 ≈ 3.5 MB / 9 万行，随应收单线性增长，代价极低但收益最直接。
--
-- 执行方式：
--   mysql -h<host> -P3306 -uroot -p <db> < V1.6.108_20261114_perf_indexes.sql
-- 回滚：V1.6.108_20261114_perf_indexes_rollback.sql
-- 注意：ADD INDEX 走 INPLACE / LOCK=NONE（online DDL），建索引期间 DML 不阻塞；
--       但会消耗 IO 与临时空间，建议业务低峰执行，大表（>1000 万行）前先看磁盘余量。
-- 注意：压测库 fssc_perf 上已有同构的 idx_probe_tenant_del_status(tenant_id,del_flag,status)，
--       那份是探针时手工建的、命名不同。**不要**在 fssc_perf 上再跑本脚本 ①，
--       否则会出现两条功能完全相同的索引，白占 31.6MB/百万行。
-- =====================================================================================

-- ---------------------------------------------------------------------------
-- ① op_voucher(tenant_id, del_flag, status)
-- ---------------------------------------------------------------------------
SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE op_voucher ADD INDEX idx_tenant_del_status (tenant_id, del_flag, status), ALGORITHM=INPLACE, LOCK=NONE',
        'SELECT ''skip: idx_tenant_del_status already exists'' AS note'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'op_voucher'
      AND index_name = 'idx_tenant_del_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- ② op_voucher(tenant_id, del_flag, source_type)
-- ---------------------------------------------------------------------------
SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE op_voucher ADD INDEX idx_tenant_del_source (tenant_id, del_flag, source_type), ALGORITHM=INPLACE, LOCK=NONE',
        'SELECT ''skip: idx_tenant_del_source already exists'' AS note'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'op_voucher'
      AND index_name = 'idx_tenant_del_source'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- ③ rc_receivable(tenant_id, company_id, del_flag)
-- ---------------------------------------------------------------------------
SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE rc_receivable ADD INDEX idx_tenant_company_del (tenant_id, company_id, del_flag), ALGORITHM=INPLACE, LOCK=NONE',
        'SELECT ''skip: idx_tenant_company_del already exists'' AS note'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'rc_receivable'
      AND index_name = 'idx_tenant_company_del'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 执行后自检：三条索引都应存在，且列序与预期一致
-- ---------------------------------------------------------------------------
SELECT table_name, index_name,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN ('idx_tenant_del_status', 'idx_tenant_del_source', 'idx_tenant_company_del')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
