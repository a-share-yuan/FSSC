--
-- FSSC 影像域性能索引（2026-11-14 影像压测库 fssc_image_perf 并发压测实测驱动）
-- ----------------------------------------------------------------------------
-- 目标库：影像库（fssc_image / fssc_image_perf），**不是** fssc 主库。
--   执行时选定库：mysql <目标库> < V1.6.109_20261114_image_perf_indexes.sql
--   压测库验证： mysql fssc_image_perf < 本脚本
--   主库移植：   mysql fssc_image      < 本脚本
--
-- 数据规模（压测库）：img_batch 50 万 / img_file 100 万 / ocr_record 40 万，
--   且 biz_id 关联 fssc_perf.exp_bill、file_id 关联 fssc_perf.sys_file，零孤儿。
--
-- 证据来源：
--   ① concurrency 5/10/20/40 阶梯压测（preflight 9 项全绿，业务失败 0）
--   ② performance_schema.events_statements_summary_by_digest（SQL 形态 + 平均耗时 + 扫行）
--   ③ EXPLAIN（下表 key/rows/Extra）
--
-- EXPLAIN 现状（加索引前）：
--   A /batch/list?bizId=...
--     type=index key=idx_tenant rows=495015 Extra="Using where; Backward index scan"
--     -> idx_tenant_biz(tenant_id,biz_type,biz_id) 缺 biz_type 左前缀，退化全索引扫描
--   D COUNT + seal_status
--     key=idx_ib_tenant_del_biz Extra="Using where; Using index for skip scan" (avg 324ms)
--   I ImageFacade getFileIdsByBillId / findImageFilesByBizId
--     type=ref key=idx_tenant rows=493647 Extra="Using where" -> img_file 按 biz_id 全扫
--   J countByInvoiceNo（原生 SQL 缺 tenant_id）
--     key=idx_invoice_no rows=39300 Extra="Using where; Using index for skip scan"
--   K invoiceNosByFileIds / latestInvoiceKeysByFileIds（file_id IN）
--     key=idx_tenant_batch rows=196504 Extra="Using where; Using temporary"
--
-- 实测收益（压测库 50 万 / 100 万 / 40 万行）：
--   /batch/list?bizId  单发 1277ms -> 11ms（EXPLAIN 495015 行 -> 1 行）
--   img_file 按 biz_id EXPLAIN 493647 行 -> 2 行
--   countByInvoiceNo   EXPLAIN 39300 行 -> 7 行（Using index）
--   file_id IN         EXPLAIN 196504 行 + Using temporary -> 3 行（Using index）
--   重查询场景 40 并发 RPS 5.58 -> 9.26（+66%，同一批数据仅切索引 VISIBLE/INVISIBLE）
--
--  第 2 条 idx_ib_tenant_del_seal **不会被优化器选中**：EXPLAIN 仍走
--   idx_ib_tenant_del_biz（del_flag 全表同值导致选择性误判），裸计时 305ms；
--   FORCE INDEX 后 126ms（2.4x）。已配套上线：
--   ImgBatchMapper#countBySealStatus（FORCE INDEX）+ ImageService#countTask 在
--   「仅封单状态筛选」时调用它 —— 索引与 SQL 必须成对上线，否则这条索引纯写放大。
--
--  本脚本只加索引，不改任何业务逻辑，可安全回滚。
--  索引救不了的部分已于同日 17:20–17:55 配套改代码并上线（详见压测报告第 7 / 11 节）：
--     - 5 个分页端点接 60s CountSnapshot（ImagePageKit），每条 COUNT 本轮只跑 2 次（基线 1191 次）
--     - statBatchByBizType 改 ImageStatsSnapshot，并修枚举 PASSED/DONE -> OK/SEALED
--     - 4 处 keyword 改 likeRight 前缀匹配，去掉 %keyword% 全表扫描
--     - /batch/list 按 ListPaging 钳位 1..500 + 前端 remote 远程搜索
--     => 混合读 40 并发 75.43 -> 4162.9 RPS（55x），重查询 5.58 -> 4793.4 RPS。
--
--  别把「加索引」当成容量解药：混合读场景 40 并发 RPS 75.43 -> 69.97（无提升），
--   因为取数据页恒 0.6~0.7ms，而每个分页端点的 COUNT 恒 190~540ms、累计扫 5.5 亿行。
--   RPS 上限由 COUNT 决定，只有改代码才能突破。
--
-- 回滚（逐条）：
--   ALTER TABLE img_batch  DROP INDEX idx_ib_tenant_bizid;
--   ALTER TABLE img_batch  DROP INDEX idx_ib_tenant_del_seal;
--   ALTER TABLE img_file   DROP INDEX idx_if_tenant_bizid;
--   ALTER TABLE ocr_record DROP INDEX idx_or_invoice_no;
--   ALTER TABLE ocr_record DROP INDEX idx_or_tenant_file;
--

-- ===================== img_batch =====================
-- 1) 按业务单据 ID 查影像批次（/batch/list?bizId，档案/单据影像状态回查）
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'img_batch'
     AND index_name   = 'idx_ib_tenant_bizid'
);
SET @ddl := IF(@idx_exists > 0, 'DO 0',
  'ALTER TABLE img_batch ADD INDEX idx_ib_tenant_bizid (tenant_id, biz_id)');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) 影像任务按封单状态分页的 COUNT：覆盖索引，避免 skip scan
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'img_batch'
     AND index_name   = 'idx_ib_tenant_del_seal'
);
SET @ddl := IF(@idx_exists > 0, 'DO 0',
  'ALTER TABLE img_batch ADD INDEX idx_ib_tenant_del_seal (tenant_id, del_flag, seal_status)');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ===================== img_file =====================
-- 3) ImageFacade.getFileIdsByBillId / findImageFilesByBizId：按 biz_id 取单据影像页
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'img_file'
     AND index_name   = 'idx_if_tenant_bizid'
);
SET @ddl := IF(@idx_exists > 0, 'DO 0',
  'ALTER TABLE img_file ADD INDEX idx_if_tenant_bizid (tenant_id, biz_id)');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ===================== ocr_record =====================
-- 4) countByInvoiceNo 是原生 SQL「where invoice_no = ?」不带 tenant_id，
--    idx_invoice_no(tenant_id,invoice_no) 左前缀失效 => 补单列索引。
--    （更彻底的做法是给该 SQL 补 tenant_id 条件，属代码改动，见报告）
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'ocr_record'
     AND index_name   = 'idx_or_invoice_no'
);
SET @ddl := IF(@idx_exists > 0, 'DO 0',
  'ALTER TABLE ocr_record ADD INDEX idx_or_invoice_no (invoice_no)');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 5) invoiceNosByFileIds / latestInvoiceKeysByFileIds / fileIdsByInvoiceNo 的
--    file_id IN (...) 与 select distinct file_id：加 (tenant_id,file_id) 免全扫+临时表
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'ocr_record'
     AND index_name   = 'idx_or_tenant_file'
);
SET @ddl := IF(@idx_exists > 0, 'DO 0',
  'ALTER TABLE ocr_record ADD INDEX idx_or_tenant_file (tenant_id, file_id)');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ===================== 验证 =====================
SELECT table_name, index_name,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND index_name IN ('idx_ib_tenant_bizid','idx_ib_tenant_del_seal',
                      'idx_if_tenant_bizid','idx_or_invoice_no','idx_or_tenant_file')
 GROUP BY table_name, index_name
 ORDER BY table_name, index_name;

ANALYZE TABLE img_batch, img_file, ocr_record;
