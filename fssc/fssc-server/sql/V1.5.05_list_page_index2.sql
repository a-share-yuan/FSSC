-- =====================================================================
-- V1.5.05 · 列表页/翻页/计数慢查询治理（续）：两枚复合索引
-- 生成日期：2026-10-30    适用库：fssc（MySQL 8.0）
-- =====================================================================
-- 背景：V1.5.04 之后按「每端点 3 次取最小值」重扫 197 个 list/page 端点，
--   200 全部可达，仍有 2 条 >300ms 与 6 条 >150ms。逐条抓 MySQL 通用日志
--   （log_output=TABLE，只读观测，抓完即关）定位到两类残留：
--
--   A. 缺「过滤列 + 排序列」同索引 ⇒ COUNT 回表过滤 + 分页面 filesort
--      ① op_quality   20.0 万行：分页 WHERE del_flag=0 ORDER BY check_time DESC，
--         库里只有 (tenant_id) / (tenant_id,del_flag) / (tenant_id,checker_id,result)，
--         check_time 无索引端 ⇒ COUNT 121ms + 分页 filesort 491ms，端点 377~427ms。
--      ② inv_invoice  11.2 万行：分页 WHERE del_flag=0 AND company_id=? ORDER BY id DESC，
--         无 (tenant_id, del_flag, company_id) 组合 ⇒ COUNT 逐行回表判 del_flag/company_id：
--         实测 186ms，端点 178~223ms。
--
--   B. 逐行往返（N+1）——不在本文件，见同轮代码修复：
--      /expense/bill/myPage、/expense/bill/air-ticket/page 的「当前流程/当前节点」
--      改为整页批量回填（每页 40 条 SQL → 3 条）；/pay/match/page 与
--      /wf/query/history/page 已在上一轮定性（前者是逐单真实匹配计算，后者受
--      百万行精确计数所限），作为已知下界记入方案册，不再为凑数字改业务逻辑。
--
--   等值列其次，排序列再次，主键收尾 ⇒ COUNT 走覆盖索引、分页走索引序早停。
--
-- 执行说明：在线加索引（INPLACE + LOCK=NONE），不锁表、不中断业务；
--   两表均 20 万行以内，单枚预计 < 5 秒。禁 INPLACE 报 1846 时去掉子句重跑。
-- 幂等性：重复执行报 1061 Duplicate key name，属预期。
-- 回滚：同名 _rollback.sql。
-- =====================================================================

-- 0) 前置：确认两枚索引尚不存在（应为 0 行）
SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_oq_tenant_del_ct', 'idx_ii_tenant_del_comp_id')
GROUP BY TABLE_NAME, INDEX_NAME;

-- 1) op_quality：质检分页（del_flag 等值 + check_time 倒序）COUNT 与分页同索引
ALTER TABLE op_quality
    ADD INDEX idx_oq_tenant_del_ct (tenant_id, del_flag, check_time, id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 2) inv_invoice：发票列表（del_flag + company_id 等值 + id 倒序）
ALTER TABLE inv_invoice
    ADD INDEX idx_ii_tenant_del_comp_id (tenant_id, del_flag, company_id, id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 3) 自检：两枚索引应全部在册（应为 2 行）
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_oq_tenant_del_ct', 'idx_ii_tenant_del_comp_id')
GROUP BY TABLE_NAME, INDEX_NAME;
