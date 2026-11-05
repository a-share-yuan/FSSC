-- =====================================================================
-- V1.6.03 Flowable 引擎租户回填（，破坏性窗口脚本）
-- 依赖：无
-- 可重复执行：是（只回填 TENANT_ID_ 为空的旧行，已打租户的行不动）
-- 影响表：21 张 act_*/flw_* 引擎表的 TENANT_ID_ 列
--
--  执行顺序（缺一不可，见 docs/部署运维.md §7.3）：
--     ① 停服（含看门狗）
--     ② 执行本脚本
--     ③ 换新 jar 起服
--     ④ 冒烟：新建一笔报销走到办结 + 待办/已办列表可见
--   先迁后码：旧包按无租户查定义，查不到已回填的定义 → 提单全断；
--   先码后迁：新包按租户查定义，历史定义仍是空租户 → 同样断。
--
--  执行前必须确认在办/历史行数。若 act_ru_execution / act_ru_task 有在办实例，
--   建议窗口内跑完再回填；act_hi_* 有历史时回填同样安全（只改租户列）。
-- =====================================================================

-- ① 设定目标租户 id（单租户现网＝1；多租户现网请按下方 ② 的清单逐租户执行）
SET @tenant_id := 1;

-- ② 回填前取证：这些表里 TENANT_ID_ 为空的旧行数（本机现盘除 act_re_* 外多为 0）
SELECT 'BEFORE' AS phase, 'act_re_deployment' AS tbl, COUNT(*) AS empty_rows FROM act_re_deployment WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = ''
UNION ALL SELECT 'BEFORE','act_re_procdef', COUNT(*) FROM act_re_procdef WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = ''
UNION ALL SELECT 'BEFORE','act_re_model', COUNT(*) FROM act_re_model WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = ''
UNION ALL SELECT 'BEFORE','act_ru_execution', COUNT(*) FROM act_ru_execution WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = ''
UNION ALL SELECT 'BEFORE','act_ru_task', COUNT(*) FROM act_ru_task WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = ''
UNION ALL SELECT 'BEFORE','act_hi_procinst', COUNT(*) FROM act_hi_procinst WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = ''
UNION ALL SELECT 'BEFORE','act_hi_taskinst', COUNT(*) FROM act_hi_taskinst WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = ''
UNION ALL SELECT 'BEFORE','act_hi_actinst', COUNT(*) FROM act_hi_actinst WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = ''
UNION ALL SELECT 'BEFORE','act_hi_tsk_log', COUNT(*) FROM act_hi_tsk_log WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';

-- ③ 回填（21 张表；只改空租户行，重复执行安全）
UPDATE act_re_deployment      SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_re_procdef         SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_re_model           SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_ru_execution       SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_ru_task            SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_ru_actinst         SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_ru_event_subscr    SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_ru_job             SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_ru_timer_job       SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_ru_suspended_job   SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_ru_deadletter_job  SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_ru_external_job    SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_ru_history_job     SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_hi_procinst        SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_hi_taskinst        SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_hi_actinst         SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_hi_tsk_log         SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE act_id_user            SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE flw_channel_definition  SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE flw_event_definition    SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
UPDATE flw_event_deployment    SET TENANT_ID_ = @tenant_id WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';

-- ④ 回填后取证：应全为 0
SELECT 'AFTER' AS phase, 'act_re_deployment' AS tbl, COUNT(*) AS empty_rows FROM act_re_deployment WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = ''
UNION ALL SELECT 'AFTER','act_re_procdef', COUNT(*) FROM act_re_procdef WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = ''
UNION ALL SELECT 'AFTER','act_ru_execution', COUNT(*) FROM act_ru_execution WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = ''
UNION ALL SELECT 'AFTER','act_ru_task', COUNT(*) FROM act_ru_task WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = ''
UNION ALL SELECT 'AFTER','act_hi_procinst', COUNT(*) FROM act_hi_procinst WHERE TENANT_ID_ IS NULL OR TENANT_ID_ = '';
-- 期望：AFTER 各行 empty_rows = 0

-- ⑤ 定义版本去重：同一 key 若因历史全局部署留下多条相同定义，latestVersion 只会取最新一条，
--    此处只做"可见性"提示，不删任何行（删定义影响历史实例回放）。
SELECT key_ AS proc_key, TENANT_ID_ AS tenant, COUNT(*) AS versions
FROM act_re_procdef GROUP BY key_, TENANT_ID_ HAVING COUNT(*) > 1 ORDER BY versions DESC;
