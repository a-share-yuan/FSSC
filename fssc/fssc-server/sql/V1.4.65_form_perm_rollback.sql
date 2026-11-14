-- =============================================================================
-- V1.4.65 回滚：清空首批两节点的 form_perm，恢复"全库未配置 ⇒守卫恒fail-open"的原状
--
-- 影响面：仅 CLAIM/Task_7 与 CLAIM/Task_13 两行的 form_perm 置 NULL。
--         守卫随即回到 fail-open（不裁剪、不拦截），与本件执行前逐字一致。
--         前端 GET /wf/task-form-perm 对这两个节点返回空Map，界面恢复为全部可编辑。
-- =============================================================================

UPDATE fssc.wf_node_config
   SET form_perm = NULL,
       update_by = NULL,
       update_time = NOW()
 WHERE model_key = 'CLAIM'
   AND node_key IN ('Task_7', 'Task_13')
   AND del_flag = 0
   AND form_perm IS NOT NULL;

-- 执行后校验：期望 0
-- SELECT COUNT(*) AS nonempty FROM fssc.wf_node_config
--  WHERE form_perm IS NOT NULL AND TRIM(form_perm) <> '';