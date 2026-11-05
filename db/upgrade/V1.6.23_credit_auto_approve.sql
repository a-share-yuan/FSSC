-- =====================================================================
-- V1.6.23  2026-11-05  信用免审：员工信用铺底 + 两个控制参数
--
-- 背景：employee_credit 一直 0 行。建档只发生在「单据首次进入共享作业」
--       （DispatchService → EmployeeCreditService.onSubmitted → getOrCreate），
--       而 canAutoApprove 查不到档案直接返回 false ⇒ 第一张单永远走人工，
--       信用体系从零就断了，永远积累不起来。
--
-- 本增量：
--   ① 为全部在职、非管理员用户建档：初始 600 分 / C 级 / 免审额度 500（与代码里算好的口径一致）；
--   ② 写入控制参数：免审总开关、最低免审等级（缺省 B）、免审单抽检比例；
--   ③ 把 RR_AMOUNT_HIGH 的备注改成归档说明——金额分档已在 CLAIM 流程的
--      Gw_16/Gw_9/Gw_11/Gw_13（5万/20万/50万/500万）里实现，审单规则不再承担分档。
--
-- 幂等：建档带 NOT EXISTS；参数带 NOT EXISTS；备注更新带前置条件。
-- 回滚：V1.6.23_credit_auto_approve_rollback.sql
-- =====================================================================

-- ① 信用铺底（用 last_adjust_reason 打标，便于回滚精确识别本次铺的行）
INSERT INTO `employee_credit`
  (`tenant_id`, `user_id`, `user_name`, `credit_score`, `credit_level`, `auto_approve_limit`,
   `total_submitted`, `total_approved`, `total_rejected`, `violation_count`,
   `last_adjust_reason`, `create_time`, `update_time`, `del_flag`)
SELECT u.`tenant_id`,
       u.`id`,
       COALESCE(NULLIF(u.`nick_name`, ''), u.`user_name`),
       600,
       'C',
       500.00,
       0, 0, 0, 0,
       '2026-11-05 信用铺底（初始 600 分 / C 级）',
       NOW(), NOW(), 0
  FROM `sys_user` u
 WHERE u.`del_flag` = 0
   AND u.`status` = 'NORMAL'
   AND IFNULL(u.`is_admin`, 0) = 0
   AND NOT EXISTS (SELECT 1 FROM `employee_credit` c WHERE c.`user_id` = u.`id`);

-- ② 控制参数
--    ① 总开关：置 0 只累计信用、不再自动放行
INSERT INTO `sys_config`
  (`tenant_id`, `config_key`, `config_name`, `config_value`, `value_type`, `group_code`,
   `is_builtin`, `editable`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 'review.credit.auto_approve.enabled', '信用免审总开关', '1', 'BOOLEAN', 'COMMON',
       0, 1,
       '置 1=规则未给结论时，信用达标的单据可跳过人工审核；置 0=只累计信用不放行。读取方 EmployeeCreditService.canAutoApprove。',
       NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_config`
                    WHERE `config_key` = 'review.credit.auto_approve.enabled' AND `tenant_id` = 1);

--    ② 最低免审等级：A/B/C/D，缺省 B（新员工 600 分是 C 级，不免审）
INSERT INTO `sys_config`
  (`tenant_id`, `config_key`, `config_name`, `config_value`, `value_type`, `group_code`,
   `is_builtin`, `editable`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 'review.credit.auto_approve.min_level', '信用免审最低等级', 'B', 'STRING', 'COMMON',
       0, 1,
       '只有等级不低于该档才免审（A>B>C>D）。缺省 B：新员工 600 分（C 级）不免审，攒到 700 升 B 才有额度。非法值一律回落 C。',
       NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_config`
                    WHERE `config_key` = 'review.credit.auto_approve.min_level' AND `tenant_id` = 1);


--    ③ 兼容：早期执行过本脚本的库 min_level 是 'C'，此处抬到 'B'（对齐业界「C/D 一律人工」口径）
UPDATE `sys_config`
   SET `config_value` = 'B', `update_time` = NOW()
 WHERE `config_key` = 'review.credit.auto_approve.min_level'
   AND `tenant_id` = 1
   AND `config_value` = 'C';

--    ④ 免审单抽检比例：自动通过的单据按该比例转为人工复核（流程抽检）
INSERT INTO `sys_config`
  (`tenant_id`, `config_key`, `config_name`, `config_value`, `value_type`, `group_code`,
   `is_builtin`, `editable`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 'review.auto_pass.sample_rate', '免审单抽检比例', '0.10', 'NUMBER', 'COMMON',
       0, 1,
       '智能审单判定自动通过的单据，按该比例抽中转为人工复核（免审≠不查）。0=不抽检，1=全部转人工。读取方 SharingService。',
       NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_config`
                    WHERE `config_key` = 'review.auto_pass.sample_rate' AND `tenant_id` = 1);

-- ⑤ RR_AMOUNT_HIGH 归档说明（规则保持停用，不做分档改造）
UPDATE `op_review_rule`
   SET `remark` = CONCAT(IFNULL(`remark`, ''),
                         IF(IFNULL(`remark`, '') = '', '', ' | '),
                         '2026-11-05 归档：金额分级审批已在 CLAIM 流程的 Gw_16(>5万→基地财务经理)/Gw_9(>20万→基地总经理)/Gw_11(>50万→板块财务经理)/Gw_13(>500万→总裁) 实现；审单规则只做「过/驳/转人工」，不承担分档，本规则保持停用勿启用'),
       `updated_at` = NOW()
 WHERE `rule_code` = 'RR_AMOUNT_HIGH'
   AND `status` = 'DISABLED'
   AND IFNULL(`remark`, '') NOT LIKE '%2026-11-05 归档%';

-- 自检
SELECT `credit_level`, COUNT(*) AS users FROM `employee_credit` GROUP BY `credit_level`;
SELECT `config_key`, `config_value`, `value_type` FROM `sys_config` WHERE `config_key` LIKE 'review.credit%';
SELECT `rule_code`, `status`, LEFT(`remark`, 60) AS remark_head FROM `op_review_rule` WHERE `rule_code` = 'RR_AMOUNT_HIGH';
