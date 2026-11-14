-- ============================================================================
-- V1.6.107  质检退回通知模板
--
-- 背景：质检「退回整改」(op_quality.result = 'BACK') 与驳回同属「单据被退回去、
--       但人未必知道」的路径。此前只落质检记录与信用扣分、不发通知，单据停在
--       「等整改」上没人动。本次补一条触发点模板。
--
-- 配套代码（同批次，非本脚本范围）：
--   触发点常量 NotifyTriggers.EXP_QUALITY_BACK
--   SharingEnhanceService.notifyQualityBack() —— result=BACK 时按本模板通知申请人
--
-- 幂等：INSERT IGNORE，重复执行不新增行、不覆盖业务已改过的文案。
-- 回滚：V1.6.107_20261112_exp_quality_back_template_rollback.sql
-- ============================================================================

INSERT IGNORE INTO `sys_notify_template`
  (`tenant_id`, `trigger_code`, `trigger_name`, `msg_type`, `title_tpl`, `content_tpl`,
   `link_tpl`, `enabled`, `is_builtin`, `remark`, `create_time`, `update_time`, `del_flag`)
VALUES
  (1, 'EXP_QUALITY_BACK', '报销单质检退回整改', 'ALERT',
   '单据 ${billNo} 质检退回，请整改后重提',
   '您的《${title}》（${billNo}）在质检中被退回整改，得分 ${score}。退回原因：${comment}。'
   '整改后可在「我的单据」重新提交。',
   '/expense/my?billNo=${billNo}',
   1, 1,
   '变量：${billNo} 单号 / ${title} 事由 / ${billType} 单据类型 / ${score} 质检得分 / ${comment} 退回原因 / ${applyUser} 申请人',
   NOW(), NOW(), 0);

-- ---------- 校验 ----------
-- 预期：模板 1 行、enabled=1
SELECT CONCAT('templates=', COUNT(*), '|enabled=', SUM(enabled))
  FROM `sys_notify_template` WHERE del_flag = 0 AND trigger_code = 'EXP_QUALITY_BACK';
