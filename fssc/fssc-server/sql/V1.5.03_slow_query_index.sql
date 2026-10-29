-- =====================================================================
-- V1.5.03 · 全站同类慢查询治理：三枚复合索引
-- 生成日期：2026-10-30    适用库：fssc（MySQL 8.0）
-- =====================================================================
-- 根因（2026-10-30 实测，库内为百万级造数语料）：
--   op_archive 52 万行，可用前缀索引只有 uk_tenant_archive_no(tenant_id, archive_no)，
--   所有带 company_id / archive_status / del_flag 的 COUNT 与 SUM(CASE…) 都退化成
--   "扫完整个租户的索引项再逐行回表判过滤条件"：
--     /tenant/compliance/archive/page        COUNT 平均 1.22s（4 次采样），端点整耗时 2.67s
--     /tenant/compliance/archive/stats       三次独立 COUNT 各约 1.1s，端点 3.33s
--     /tenant/sharing/enhance/archive/page   COUNT(*) 1.08s
--     /tenant/dashboard/archive/progress     SUM(CASE…) 1.18s
--   exp_bill_payment 27 万行的付款进度 GROUP BY 同样逐行回表判 del_flag：0.49s。
--
-- 列序理由：
--   过滤条件恒为 (tenant_id, del_flag[, company_id])，故这两个前缀固定在最前；
--   之后按用途分叉，不做"既能排序又能聚合"但两用都不覆盖的万能索引（索引越宽，
--   每次写 op_archive 的维护成本越高——立卷/归档每次都要写这行）：
--     · 列表用途：create_time 紧随 company_id，服务 ORDER BY create_time DESC LIMIT 的分页
--       与同条件的 COUNT（索引覆盖，无需回表）。
--     · 归档聚合用途：archive_status + archived_at 紧随 company_id，服务
--       "按归档状态计数"与"今日已归档"两个 SUM(CASE…)（索引覆盖，无需回表）。
--
-- 执行说明：三枚均为在线加索引（INPLACE + LOCK=NONE），不锁表、不中断业务；
--   52 万行量级单枚预计 3~10 秒。若实例参数禁用 INPLACE 而报 1846，去掉 ALGORITHM/LOCK 子句重跑即可。
-- 幂等性：重复执行会报 1061 Duplicate key name，属预期（说明已生效），不会破坏数据。
-- =====================================================================

-- 0) 前置：确认四枚索引尚不存在（应为 0 行）
SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_t_del_comp_ct', 'idx_t_del_comp_status', 'idx_t_del_status_amount', 'idx_t_comp_status_img')
GROUP BY TABLE_NAME, INDEX_NAME;

-- 1) op_archive：档案列表分页 + 同条件计数
ALTER TABLE op_archive
    ADD INDEX idx_t_del_comp_ct (tenant_id, del_flag, company_id, create_time),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 2) op_archive：归档状态聚合（档案统计 / 进度看板 / 共享归档页计数）
ALTER TABLE op_archive
    ADD INDEX idx_t_del_comp_status (tenant_id, del_flag, company_id, archive_status, archived_at),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 3) exp_bill_payment：付款进度按状态分组聚合（覆盖索引，含 sum(amount) 所需列）
ALTER TABLE exp_bill_payment
    ADD INDEX idx_t_del_status_amount (tenant_id, del_flag, pay_status, amount),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 4) exp_bill：待归档单据清单（审批完成 + 影像齐套 + 未归档，按单据 id 升序分页）
--    原计划全落在 uk_tenant_bill_no(tenant_id) 上，34 万行逐行回表：COUNT 608ms、首页 212ms。
--    本索引把六个过滤列整体覆盖后，范围扫描直接剪到空集：0.07ms。
ALTER TABLE exp_bill
    ADD INDEX idx_t_comp_status_img (tenant_id, del_flag, company_id, status, image_status, archive_status),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 5) 自检：四枚索引应全部在册（应为 4 行）
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_t_del_comp_ct', 'idx_t_del_comp_status', 'idx_t_del_status_amount', 'idx_t_comp_status_img')
GROUP BY TABLE_NAME, INDEX_NAME;
