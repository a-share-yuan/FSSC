-- =====================================================================
-- V1.6.23 的回滚
--
-- ① 删除本次铺底的信用档案（按 last_adjust_reason 精确识别，不误伤后来真正产生的记录）；
-- ② 删除三个控制参数（代码里参数缺失时缺省按"开 + C 级"，等同回到铺底前的行为，
--    但因为档案已删，canAutoApprove 仍返回 false）；
-- ③ 移除 RR_AMOUNT_HIGH 备注里的归档说明，规则状态保持停用不变。
-- 幂等：全部带前置条件。
-- =====================================================================

DELETE FROM `employee_credit`
 WHERE `last_adjust_reason` = '2026-11-05 信用铺底（初始 600 分 / C 级）'
   AND `total_submitted` = 0
   AND `total_approved` = 0
   AND `credit_score` = 600;

DELETE FROM `sys_config`
 WHERE `config_key` IN ('review.credit.auto_approve.enabled', 'review.credit.auto_approve.min_level',
                        'review.auto_pass.sample_rate')
   AND `tenant_id` = 1;

UPDATE `op_review_rule`
   SET `remark` = REPLACE(`remark`,
                          ' | 2026-11-05 归档：金额分级审批已在 CLAIM 流程的 Gw_16(>5万→基地财务经理)/Gw_9(>20万→基地总经理)/Gw_11(>50万→板块财务经理)/Gw_13(>500万→总裁) 实现；审单规则只做「过/驳/转人工」，不承担分档，本规则保持停用勿启用',
                          ''),
       `updated_at` = NOW()
 WHERE `rule_code` = 'RR_AMOUNT_HIGH';

SELECT COUNT(*) AS credit_rows FROM `employee_credit`;
SELECT COUNT(*) AS cfg_rows FROM `sys_config` WHERE `config_key` LIKE 'review.credit%';
