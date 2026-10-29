-- =====================================================================
-- V1.5.07 · 统计类端点慢查询治理：影像批次 / 档案卷 / 合规三表
-- 生成日期：2026-10-30    适用库：fssc_image（img_batch）、fssc（其余五表）
-- =====================================================================
-- 目标端点（改前＝开修前单趟；改后＝2026-10-30 02:52 复测，同一工装带 n= 破缓存）：
--   /image/stats/batch-count      499ms → 61ms
--   /image/stats/by-biz-type      574ms → 165ms
--   /compliance/archive/stats     455ms → 199ms
--   /dashboard/compliance-risk   1665ms → 235ms
--   /dashboard/ops/overview        947ms → 271ms（任务类型分组带平均时长 669ms → 约 125ms）
-- 端点读数出处见《百万级造数与性能测试/02_性能测试方案.md》§17.5，DB 地板见 §17.6。
--
-- 1) img_batch（约 30 万行，租户内计数与按业务类型汇总）
--   现状：优化器选 uk_tenant_batch_no（只含 tenant_id 的宽索引），30 万行逐行回表
--   判 del_flag → 计数 420ms、分组汇总 540ms。
--   加 (tenant_id, del_flag, biz_type, doc_count, review_status, seal_status) 一枚
--   全列覆盖索引后：计数 58ms、汇总 163ms。
--   注意：优化器把 del_flag=0 估成 1% 过滤率，即使有覆盖索引也仍偏好宽唯一索引；
--   取数端（ImgBatchMapper.countByTenant / statByBizType）已固定 FORCE INDEX
--   指向本索引，故索引名不可再改。
--
-- 2) op_archive（约 51 万行，按 archive_status 分桶出三数）
--   现状：已有 idx_t_del_comp_status 覆盖，但 company_id 夹在 archive_status 之前，
--   GROUP BY archive_status 走不到索引序 → Using temporary，实测 280ms。
--   加 (tenant_id, del_flag, archive_status) 后按索引序分组、无临时表：
--   实测 158~162ms。
--
-- 3) 合规三表（precheck_result 24 万 / fraud_detection_log 25 万 / op_review_log 15 万）
--   问题不是慢查询写法，而是三张表连 tenant_id 索引都没有——多租户拦截器拼上的
--   tenant_id = ? 只能当过滤条件，任何查询都是整表扫。
--   配合取数端改成「按状态列分组计数」（覆盖索引扫描），实测：
--     预检批次数 453ms → 75ms（派生表分组，见 PrecheckResultMapper.countDistinctBatch）
--     预检结果分布 整表扫 399ms → 105ms
--     欺诈告警 131ms → 62ms
--     审单分布 227ms → 35ms
--
-- 执行说明：在线加索引（INPLACE + LOCK=NONE）；img_batch 一枚约 30MB。
-- 幂等性：重复执行报 1061 Duplicate key name，属预期。
-- 回滚：同名 _rollback.sql。
-- =====================================================================

-- 0) 前置：确认七枚索引尚不存在（应为 0 行）
SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA IN ('fssc', 'fssc_image')
  AND INDEX_NAME IN ('idx_ib_tenant_del_biz', 'idx_t_del_status', 'idx_pr_tenant_batch_result',
                     'idx_pr_tenant_result', 'idx_fdl_tenant_result_id', 'idx_orl_tenant_action',
                     'idx_oat_tenant_del_type_ft')
GROUP BY TABLE_NAME, INDEX_NAME;

-- 1) 影像批次：租户内计数与按业务类型汇总全覆盖
ALTER TABLE fssc_image.img_batch
    ADD INDEX idx_ib_tenant_del_biz
        (tenant_id, del_flag, biz_type, doc_count, review_status, seal_status),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 2) 档案卷：按归档状态分桶（索引序分组，免临时表）
ALTER TABLE fssc.op_archive
    ADD INDEX idx_t_del_status (tenant_id, del_flag, archive_status),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 3) 预检结果：租户内批次去重 + 按结果分组
ALTER TABLE fssc.precheck_result
    ADD INDEX idx_pr_tenant_batch_result (tenant_id, precheck_batch_id, check_result),
    ADD INDEX idx_pr_tenant_result (tenant_id, check_result),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 4) 欺诈检测：租户内按结果分组（id 尾列供列表页倒序早停）
ALTER TABLE fssc.fraud_detection_log
    ADD INDEX idx_fdl_tenant_result_id (tenant_id, detection_result, id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 6) 派工任务：按任务类型分组计数与平均处理时长（finish_time/create_time 覆盖，免回表）
--   del_flag 必须进索引：取数端（DashboardService.opsOverview）是实体查询，逻辑删除
--   会拼上 del_flag = 0；少了它索引就不覆盖，优化器退回整表扫 25 万行 × 全列，
--   同一条分组语句实测 669ms（应用侧）对 124.6ms（覆盖索引）。原
--   (tenant_id, task_type, finish_time, create_time) 因同样原因从未被选中，已撤。
ALTER TABLE fssc.op_audit_task
    ADD INDEX idx_oat_tenant_del_type_ft (tenant_id, del_flag, task_type, finish_time, create_time),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 7) 审单日志：租户内按动作分组
ALTER TABLE fssc.op_review_log
    ADD INDEX idx_orl_tenant_action (tenant_id, action),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 8) 自检：七枚索引应全部在册（应为 7 行）
SELECT TABLE_SCHEMA, TABLE_NAME, INDEX_NAME,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA IN ('fssc', 'fssc_image')
  AND INDEX_NAME IN ('idx_ib_tenant_del_biz', 'idx_t_del_status', 'idx_pr_tenant_batch_result',
                     'idx_pr_tenant_result', 'idx_fdl_tenant_result_id', 'idx_orl_tenant_action',
                     'idx_oat_tenant_del_type_ft')
GROUP BY TABLE_SCHEMA, TABLE_NAME, INDEX_NAME;
