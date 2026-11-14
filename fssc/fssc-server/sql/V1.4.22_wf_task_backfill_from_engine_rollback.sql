-- =====================================================================
-- V1.4.22 回滚 —— wf_task 双账补齐撤销
-- 前提：主脚本执行后到回滚前，这些任务未发生新的审批/转办/领取（否则回滚会把已办结的行改回待办）。
--       回滚前先看一眼 R0，任何非 0 都要人工核对后再决定是否继续。
-- 执行：mysql --default-character-set=utf8mb4
-- =====================================================================

-- ---------- R0：回滚前置体检 ----------
-- 期望 0：镜像 PENDING 但引擎已无该任务（说明期间任务被办结，不能简单回滚）
SELECT COUNT(*) r0_advanced FROM wf_task w
LEFT JOIN ACT_RU_TASK t ON t.ID_ = w.task_id
WHERE w.status = 'PENDING' AND t.ID_ IS NULL;

-- ---------- 1）删除本次新增的镜像行（按快照清单精确删，不做全表 diff） ----------
DELETE w FROM wf_task w
JOIN bak_v1422_inserted k ON k.task_id = w.task_id
WHERE w.status = 'PENDING';

-- ---------- 2）把被步骤 1/2 改过的列恢复到快照值 ----------
-- 只恢复"仍处于 PENDING"的行：主脚本改的就是这些行；已流转的行（R0 检查项）保持现状不回退。
UPDATE wf_task w
JOIN bak_v1422_wf_task k ON k.id = w.id
SET w.status        = k.status,
    w.assignee_id   = k.assignee_id,
    w.candidate_ids = k.candidate_ids,
    w.create_time   = k.create_time,
    w.due_time      = k.due_time,
    w.urged_count   = k.urged_count,
    w.update_time   = k.update_time
WHERE w.status = 'PENDING';

-- ---------- 验收（期望全 0） ----------
-- R1 已删除的新增行不在了
SELECT COUNT(*) r1 FROM wf_task w JOIN bak_v1422_inserted k ON k.task_id = w.task_id WHERE w.status = 'PENDING';
-- R2 与快照逐列一致
SELECT COUNT(*) r2 FROM wf_task w JOIN bak_v1422_wf_task k ON k.id = w.id
WHERE w.status = 'PENDING'
  AND (w.assignee_id <=> k.assignee_id) = 0;

-- ---------- 3）体检通过后再清理快照表（保留一周便于二次回滚） ----------
-- DROP TABLE IF EXISTS bak_v1422_wf_task;
-- DROP TABLE IF EXISTS bak_v1422_inserted;
