-- ============================================================================
-- V1.6.107 回滚：停用质检退回通知模板
--
-- 默认只停用（enabled = 0）：配置留着，随时能再次启用。
-- 确要物理删除见文件末尾的可选语句（删掉后代码仍会调该触发点，只是查不到模板、静默不通知）。
-- ============================================================================

UPDATE `sys_notify_template`
   SET `enabled` = 0, `update_time` = NOW()
 WHERE `trigger_code` = 'EXP_QUALITY_BACK' AND `del_flag` = 0;

-- ---------- 校验：预期 enabled=0 ----------
SELECT CONCAT('enabled=', IFNULL(SUM(`enabled`), -1))
  FROM `sys_notify_template` WHERE del_flag = 0 AND trigger_code = 'EXP_QUALITY_BACK';

-- ---------- 可选：物理删除（确认不再需要时手动执行）----------
-- DELETE FROM `sys_notify_template` WHERE `trigger_code` = 'EXP_QUALITY_BACK';
