-- =====================================================================
-- 回滚 V1.6.03（Flowable 租户回填）
--  只在「包也回退到旧版」时执行：新包按租户查定义，把这批定义退回空租户会让新包查不到定义。
-- 顺序：停服 → 本脚本 → 换回旧 jar → 起服
-- =====================================================================
SET @tenant_id := 1;

UPDATE act_re_deployment      SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_re_procdef         SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_re_model           SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_ru_execution       SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_ru_task            SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_ru_actinst         SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_ru_event_subscr    SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_ru_job             SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_ru_timer_job       SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_ru_suspended_job   SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_ru_deadletter_job  SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_ru_external_job    SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_ru_history_job     SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_hi_procinst        SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_hi_taskinst        SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_hi_actinst         SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_hi_tsk_log         SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE act_id_user            SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE flw_channel_definition  SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE flw_event_definition    SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
UPDATE flw_event_deployment    SET TENANT_ID_ = '' WHERE TENANT_ID_ = @tenant_id;
SELECT 'V1.6.03 rollback done' AS result;
