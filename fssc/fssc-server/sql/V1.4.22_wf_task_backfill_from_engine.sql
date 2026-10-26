-- =====================================================================
-- V1.4.22  wf_task 双账补齐（P1-G22 双账打通的历史侧）
-- ---------------------------------------------------------------------
-- 病灶（09-22 只读勘查）：wf_task 镜像只在 start() 记了首节点，第 2 个节点起引擎有任务、视图没行。
--   SELECT COUNT(*) FROM ACT_RU_TASK;                        -- 7 条在办
--   其中 5 条在 wf_task 里查无 PENDING 行（2 条 Task_2 + 3 条历史 Task_1）
--   另有 3 条 PENDING 行的 assignee_id 与引擎 ASSIGNEE_ 不一致（镜像 3800 / 引擎 3805）
-- 影响面：按 wf_task PENDING 扫描的整条链路对后续节点全部失效——
--   节点级超时（WfTimeoutJob 读 model_key+node_key+create_time）、共享中心待办与看板
--   （RequisitionWorkbenchService.myTodo / getDashboard 用 assignee_id + candidate_ids LIKE）、
--   催办找人（handleNotify 按 assignee_id 发通知：单子在真人手里却催到别人）。
-- 代码侧同批修复（本次重打包生效）：recordTaskMirror 在三级解析收尾后补记、
--   start()/complete() 改按重查到的实时任务写镜像、claim/unclaim 双写办结。
--   本脚本只负责<b>历史</b>：已启动、还在跑的实例不会重走 start()，不补就一直缺。
-- 范围：只写 wf_task 一张表（视图/查询表，不碰 ACT_*、不碰业务单据表）。
-- 去重：INSERT 带 NOT EXISTS 守卫，UPDATE 带"值确实不同"守卫，可重复执行。
-- 执行：mysql --default-character-set=utf8mb4（V1.4.22 的中文标签按 utf8mb4 写入数据库）
-- =====================================================================

-- ---------- 步骤 0：快照（回滚依据） ----------
-- 0.1 现有 PENDING 行全量快照（步骤 2 的改值、步骤 3 的新增都以它为准回滚）
CREATE TABLE IF NOT EXISTS bak_v1422_wf_task AS
SELECT * FROM wf_task WHERE status = 'PENDING';

-- 0.2 本次将要新增的 task_id 清单（回滚＝按此清单删行，不做全表 diff）
CREATE TABLE IF NOT EXISTS bak_v1422_inserted AS
SELECT t.ID_ AS task_id
FROM ACT_RU_TASK t
JOIN wf_instance i ON i.proc_inst_id = t.PROC_INST_ID_
WHERE i.biz_type IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM wf_task w WHERE w.task_id = t.ID_ AND w.status = 'PENDING');

-- ---------- 步骤 1：清幽灵行（镜像 PENDING 但引擎侧任务早已办结） ----------
-- 现状实测 0 条；保留守卫语句是为了让脚本在"部署前跑一次、部署后再跑一次"都收敛。
UPDATE wf_task w
JOIN ACT_HI_TASKINST h ON h.ID_ = w.task_id AND h.END_TIME_ IS NOT NULL
SET w.status = 'DONE',
    w.update_time = NOW()
WHERE w.status = 'PENDING';

-- ---------- 步骤 2：镜像办理人对齐引擎 ----------
-- 数字型 ASSIGNEE_ 才回填（历史集成可能写角色码等非数字值，那些行按"未指派"处理）
UPDATE wf_task w
JOIN ACT_RU_TASK t ON t.ID_ = w.task_id
SET w.assignee_id = CASE WHEN t.ASSIGNEE_ REGEXP '^[0-9]+$' THEN CAST(t.ASSIGNEE_ AS UNSIGNED) ELSE NULL END,
    w.candidate_ids = CASE WHEN t.ASSIGNEE_ REGEXP '^[0-9]+$' THEN NULL ELSE w.candidate_ids END,
    w.update_time = NOW()
WHERE w.status = 'PENDING'
  AND COALESCE(w.assignee_id, -1)
      <> COALESCE(CASE WHEN t.ASSIGNEE_ REGEXP '^[0-9]+$' THEN CAST(t.ASSIGNEE_ AS UNSIGNED) ELSE NULL END, -1);

-- ---------- 步骤 3：补齐缺账（引擎在办、视图无 PENDING 行） ----------
INSERT INTO wf_task
    (tenant_id, task_id, proc_inst_id, model_key, node_key, node_name,
     biz_type, biz_id, biz_no, title, amount, assignee_id, candidate_ids,
     start_user_id, create_time, status, urged_count)
SELECT i.tenant_id,
       t.ID_,
       t.PROC_INST_ID_,
       COALESCE(i.model_key, SUBSTRING_INDEX(t.PROC_DEF_ID_, ':', 1)),
       t.TASK_DEF_KEY_,
       t.NAME_,
       i.biz_type,
       i.biz_id,
       i.biz_no,
       i.title,
       i.amount,
       CASE WHEN t.ASSIGNEE_ REGEXP '^[0-9]+$' THEN CAST(t.ASSIGNEE_ AS UNSIGNED) ELSE NULL END,
       -- 与运行态同一规则：已指派只写 assignee_id，未指派（候选组态）才写候选人
       CASE WHEN t.ASSIGNEE_ REGEXP '^[0-9]+$' THEN NULL ELSE c.candidate_ids END,
       i.start_user_id,
       t.CREATE_TIME_,
       'PENDING',
       0
FROM ACT_RU_TASK t
JOIN wf_instance i ON i.proc_inst_id = t.PROC_INST_ID_
LEFT JOIN (
    -- 候选人 = 候选组角色的在职非 admin 持有人并集，逗号包裹 ,1,2,3,（myTodo 的 LIKE 匹配格式）。
    --  两点与运行态的已知差异，只影响<b>历史补齐</b>、不影响新单（新单由 recordTaskMirror 写）：
    --   ① 不做"角色范围 × 单据公司"过滤（SQL 侧拿不到 bizType→业务表的白名单规则），
    --      因此跨公司候选人会多看到一条镜像待办（审批工作台本来就按 Flowable 候选组给他们显示）；
    --   ② 按列宽 varchar(1000) 截到 60 人（COMMON_EMP 这类 151 人大角色全拼会溢出导致整行插不进去）。
    SELECT l.TASK_ID_ AS task_id,
           CONCAT(',', SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT su.id ORDER BY su.id SEPARATOR ','), ',', 60), ',') AS candidate_ids
    FROM ACT_RU_IDENTITYLINK l
    JOIN sys_role r ON r.role_code = l.GROUP_ AND r.status = 'NORMAL' AND r.del_flag = 0
    JOIN sys_user_role ur ON ur.role_id = r.id
    JOIN sys_user su ON su.id = ur.user_id AND su.status = 'NORMAL' AND su.del_flag = 0 AND su.is_admin = 0
    WHERE l.TYPE_ = 'candidate' AND l.GROUP_ IS NOT NULL
    GROUP BY l.TASK_ID_
) c ON c.task_id = t.ID_
WHERE i.biz_type IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM wf_task w WHERE w.task_id = t.ID_ AND w.status = 'PENDING');

-- ---------- 验收（期望全 0） ----------
-- V1 引擎在办但视图缺行
SELECT COUNT(*) v1_missing FROM ACT_RU_TASK t
WHERE NOT EXISTS (SELECT 1 FROM wf_task w WHERE w.task_id = t.ID_ AND w.status = 'PENDING');
-- V2 镜像与引擎办理人不一致
SELECT COUNT(*) v2_drift FROM wf_task w JOIN ACT_RU_TASK t ON t.ID_ = w.task_id
WHERE w.status = 'PENDING'
  AND COALESCE(w.assignee_id, -1)
      <> COALESCE(CASE WHEN t.ASSIGNEE_ REGEXP '^[0-9]+$' THEN CAST(t.ASSIGNEE_ AS UNSIGNED) ELSE NULL END, -1);
-- V3 幽灵 PENDING（引擎已无此任务）
SELECT COUNT(*) v3_ghost FROM wf_task w
LEFT JOIN ACT_RU_TASK t ON t.ID_ = w.task_id
WHERE w.status = 'PENDING' AND t.ID_ IS NULL;
-- V4 唯一键完好（不应出现同 task_id 多条 PENDING）
SELECT COUNT(*) v4_dup FROM (
  SELECT task_id FROM wf_task WHERE status = 'PENDING' GROUP BY task_id HAVING COUNT(*) > 1) x;

-- ---------- 说明：为什么不做 op_audit_task ↔ wf_task 合并 ----------
-- 共享中心任务池（TaskPool.vue / op_audit_task）是"审批通过之后"的作业派工链（chain_seq、
-- active_flag、sla_time），与审批待办（wf_task ← ACT_RU_TASK）不是同一个对象的两本账，
-- 而是两个阶段的两类任务。POOL 抢单能力本轮落在审批待办侧（Flowable 候选组 + claim/unclaim +
-- wf_task 镜像），两表合并改造按方案 §11.2 B 作为独立项目排期，不在本脚本范围内。
