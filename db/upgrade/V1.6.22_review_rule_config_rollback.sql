-- =====================================================================
-- V1.6.22 的回滚
--
-- 恢复：菜单权限码/归属、三条波次测试规则、PRE-INV-DUPLICATE 的启用状态与备注。
-- 说明：被删的三条规则内容与 RR_AMOUNT_HIGH 完全一致（同一份「金额超5万自动驳回」），
--       回滚按原字段还原，id 保留原值以免历史日志（op_review_log.rule_id）指向错行。
-- 幂等：全部带前置条件，重复执行影响 0 行。
-- =====================================================================

-- ① 菜单权限码与归属还原
UPDATE `sys_menu`
   SET `perms` = 'policy:list', `parent_id` = 3390, `sort` = NULL, `update_time` = NOW()
 WHERE `id` = 3917
   AND `perms` = 'compliance:review:list';

-- ② PRE-INV-DUPLICATE 恢复启用（备注里追加的停用说明保留，便于追溯）
UPDATE `op_review_rule`
   SET `status` = 'NORMAL', `updated_at` = NOW()
 WHERE `rule_code` = 'PRE-INV-DUPLICATE'
   AND `status` = 'DISABLED';

-- ③ 三条波次测试规则按原样还原
INSERT INTO `op_review_rule`
  (`id`, `tenant_id`, `rule_code`, `rule_name`, `rule_category`, `match_expression`, `action`,
   `priority`, `risk_level`, `status`, `remark`, `rule_order`, `related_expense_types`, `is_parallel`,
   `created_at`, `updated_at`, `del_flag`)
SELECT 5, 1, 'W14UNN7-REV', '波次十四审单-W14UNN7', 'AMOUNT', 'amount > 50000', 'AUTO_REJECT',
       1, 'HIGH', 'DISABLED',
       '单据金额超过5万元自动驳回（仅费用报销类；借款/还款/付款等资金类单据豁免，2026-09-09 UI实测修复）；2026-09-12 符合性整改停用：与 Visio EER04-03 金额分档设计冲突（>5万/10万/50万 应走基地总经理/财务总监/总裁分级审批，不应一刀切自动驳回）',
       NULL, 'CLAIM,CLAIM_TRAVEL,TRAVEL,TRAIN,PREPAY,SETTLE,APPLY', 0,
       '2026-10-08 01:01:58', '2026-10-27 12:40:00', 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `op_review_rule` WHERE `rule_code` = 'W14UNN7-REV');

INSERT INTO `op_review_rule`
  (`id`, `tenant_id`, `rule_code`, `rule_name`, `rule_category`, `match_expression`, `action`,
   `priority`, `risk_level`, `status`, `remark`, `rule_order`, `related_expense_types`, `is_parallel`,
   `created_at`, `updated_at`, `del_flag`)
SELECT 7, 1, 'W14B6RO-RULE', '波次十四预检B重建', 'AMOUNT', 'amount > 50000', 'AUTO_REJECT',
       1, 'HIGH', 'DISABLED',
       '单据金额超过5万元自动驳回（仅费用报销类；借款/还款/付款等资金类单据豁免，2026-09-09 UI实测修复）；2026-09-12 符合性整改停用：与 Visio EER04-03 金额分档设计冲突（>5万/10万/50万 应走基地总经理/财务总监/总裁分级审批，不应一刀切自动驳回）',
       0, 'CLAIM,CLAIM_TRAVEL,TRAVEL,TRAIN,PREPAY,SETTLE,APPLY', 0,
       '2026-09-06 15:42:22', '2026-10-08 01:01:58', 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `op_review_rule` WHERE `rule_code` = 'W14B6RO-RULE');

INSERT INTO `op_review_rule`
  (`id`, `tenant_id`, `rule_code`, `rule_name`, `rule_category`, `match_expression`, `action`,
   `priority`, `risk_level`, `status`, `remark`, `rule_order`, `related_expense_types`, `is_parallel`,
   `created_at`, `updated_at`, `del_flag`)
SELECT 8, 1, 'W14B6RO-REV', '波次十四审单B', 'AMOUNT', 'amount > 50000', 'AUTO_REJECT',
       1, 'HIGH', 'DISABLED',
       '单据金额超过5万元自动驳回（仅费用报销类；借款/还款/付款等资金类单据豁免，2026-09-09 UI实测修复）；2026-09-12 符合性整改停用：与 Visio EER04-03 金额分档设计冲突（>5万/10万/50万 应走基地总经理/财务总监/总裁分级审批，不应一刀切自动驳回）',
       0, 'CLAIM,CLAIM_TRAVEL,TRAVEL,TRAIN,PREPAY,SETTLE,APPLY', 0,
       '2026-10-08 01:11:43', '2026-10-27 12:40:02', 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `op_review_rule` WHERE `rule_code` = 'W14B6RO-REV');

SELECT `rule_code`, `status` FROM `op_review_rule` ORDER BY `id`;
