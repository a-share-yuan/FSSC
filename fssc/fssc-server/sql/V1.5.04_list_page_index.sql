-- =====================================================================
-- V1.5.04 · 列表页/翻页/计数慢查询治理：九枚复合索引
-- 生成日期：2026-10-30    适用库：fssc（MySQL 8.0）
-- =====================================================================
-- 根因（2026-10-30 实测，库内为百万级造数语料；证据＝performance_schema
--   events_statements_summary_by_digest + EXPLAIN ANALYZE，均为只读测量）：
--
--   ① wf_process_log 108 万行：分页 COUNT 只有 (tenant_id) 前缀可走，
--      del_flag 不在索引里 ⇒ 每行回表判过滤：COUNT 5,146ms（端点 7,389ms）。
--   ② op_dispatch_log 25 万行：同上，COUNT 771ms（端点 1,806ms）。
--   ③ op_transfer_log 8 万行：同上，COUNT 447ms（端点 506ms）。
--   ④ wf_task_history 164 万行：按 action+oper_time 过滤时只有 (tenant_id,oper_time)
--      可走，action 逐行回表判：COUNT 5,908ms；「我参与过」分支的
--      target_user_id 完全无索引（端点 /wf/query/history/page 空条件查询挂死 >120s）。
--   ⑤ archive_access_log 20 万行：只有单列 idx_approval_status，tenant_id 逐行回表：
--      COUNT 146ms（端点 70ms~500ms 波动，随缓存）。
--   ⑥ op_archive 52 万行：分页 ORDER BY create_time 与 archive_status 过滤不同索引，
--      二者叠加时 MySQL 选中 (tenant,del,company,archive_status,archived_at) 且
--      因 ORDER BY 必须回表取 create_time：COUNT 2,137ms（端点 325ms 热态）。
--   ⑦ bank_reconcile 20 万行：COUNT 已覆盖（160ms），但列表 ORDER BY id DESC 无
--      对应前缀索引，翻到深页时仍要扫租户全量索引项；补 (tenant_id, id) 一并服务。
--
-- 列序理由：
--   其后按"等值列 → 排序列 → 主键"排列，使 COUNT（覆盖）与
--   ORDER BY … DESC LIMIT（索引序扫描 + 早停）同时命中同一枚索引，
--   不为两用各建一枚——每枚索引都要在每次插入时维护，表越热代价越高。
--
-- 执行说明：全部在线加索引（INPLACE + LOCK=NONE），不锁表、不中断业务。
--   量级：wf_task_history 164 万行单枚预计 10~40 秒，其余 < 10 秒。
--   若实例参数禁用 INPLACE 报 1846，去掉 ALGORITHM/LOCK 子句重跑。
-- 幂等性：重复执行会报 1061 Duplicate key name，属预期（说明已生效），不破坏数据。
-- 回滚：同名 _rollback.sql。
-- =====================================================================

-- 0) 前置：确认九枚索引尚不存在（应为 0 行）
SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_t_del_comp_status_ct', 'idx_tal_tenant_status_ct', 'idx_wpl_tenant_del_id',
                     'idx_odl_tenant_del_id', 'idx_otl_tenant_del_id',
                     'idx_wth_tenant_action_oper', 'idx_wth_tenant_target', 'idx_br_tenant_id',
                     'idx_t_inst_assignee', 'idx_t_title_id')
GROUP BY TABLE_NAME, INDEX_NAME;

-- 1) op_archive：待归档/已归档列表（状态等值 + create_time 排序）COUNT 与首页同索引
ALTER TABLE op_archive
    ADD INDEX idx_t_del_comp_status_ct (tenant_id, del_flag, company_id, archive_status, create_time),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 2) archive_access_log：调阅审批分页（approval_status 等值 + created_at 升序）
--    注：本表的时间列实名 created_at（实体 createTime 映射到 created_at），非 create_time。
ALTER TABLE archive_access_log
    ADD INDEX idx_tal_tenant_status_ct (tenant_id, approval_status, created_at, id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 3) wf_process_log：流程日志分页（全量计数 + ORDER BY id DESC）
ALTER TABLE wf_process_log
    ADD INDEX idx_wpl_tenant_del_id (tenant_id, del_flag, id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 4) op_dispatch_log：自动分配日志分页（全量计数 + ORDER BY id DESC）
ALTER TABLE op_dispatch_log
    ADD INDEX idx_odl_tenant_del_id (tenant_id, del_flag, id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 5) op_transfer_log：移交日志分页（全量计数 + ORDER BY id DESC）
ALTER TABLE op_transfer_log
    ADD INDEX idx_otl_tenant_del_id (tenant_id, del_flag, id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 6) wf_task_history：审批历史按动作+时间过滤（批退明细/质量报表），以及
--    "我参与过"分支的 target_user_id 等值查找
ALTER TABLE wf_task_history
    ADD INDEX idx_wth_tenant_action_oper (tenant_id, action, oper_time, id),
    ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE wf_task_history
    ADD INDEX idx_wth_tenant_target (tenant_id, target_user_id, id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 7) bank_reconcile：勾对列表（全量计数 + ORDER BY id DESC）
ALTER TABLE bank_reconcile
    ADD INDEX idx_br_tenant_id (tenant_id, id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 8) wf_task：按实例取任务（「我参与过的流程」实例集合、"这个实例卡在哪" 联查）
--    原先只有 (tenant_id, proc_inst_id) 相关子查询无索引可用，审批历史页退化为逐行全表扫。
ALTER TABLE wf_task
    ADD INDEX idx_t_inst_assignee (tenant_id, proc_inst_id, assignee_id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 9) sys_oper_log：执行结果视图按标题取最近事件（where title=? order by id desc limit N）
--    117 万行表无 title 索引 ⇒ 全表扫 + filesort：端点 5,907ms。
ALTER TABLE sys_oper_log
    ADD INDEX idx_t_title_id (tenant_id, title, id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 10) 自检：十枚索引应全部在册（应为 10 行）
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_t_del_comp_status_ct', 'idx_tal_tenant_status_ct', 'idx_wpl_tenant_del_id',
                     'idx_odl_tenant_del_id', 'idx_otl_tenant_del_id',
                     'idx_wth_tenant_action_oper', 'idx_wth_tenant_target', 'idx_br_tenant_id',
                     'idx_t_inst_assignee', 'idx_t_title_id')
GROUP BY TABLE_NAME, INDEX_NAME;
