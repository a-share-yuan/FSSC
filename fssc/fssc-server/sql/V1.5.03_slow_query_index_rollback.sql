-- =====================================================================
-- V1.5.03 回滚：删除三枚复合索引
-- 警告：V1.5.03 的提速还依赖代码侧两处改写（ArchiveService#getArchiveStats 单条 GROUP BY、
--   ExpenseMineStatService#stat 拆两臂）。只回滚索引、不回滚代码仍可用，只是读数回到 1s 级；
--   只回滚代码、不回滚索引同样可用（索引成为无用负担）。两端可各自独立回滚。
-- =====================================================================

ALTER TABLE op_archive DROP INDEX idx_t_del_comp_ct, ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE op_archive DROP INDEX idx_t_del_comp_status, ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE exp_bill_payment DROP INDEX idx_t_del_status_amount, ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE exp_bill DROP INDEX idx_t_comp_status_img, ALGORITHM = INPLACE, LOCK = NONE;

-- 自检：应为 0 行
SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_t_del_comp_ct', 'idx_t_del_comp_status', 'idx_t_del_status_amount', 'idx_t_comp_status_img')
GROUP BY TABLE_NAME, INDEX_NAME;
