-- =====================================================================
-- V1.6.22  2026-11-05  智能审单规则：权限码纠正 + 菜单归属 + 脏数据清理
--
-- 背景：审单规则页能打开、能增删改，但
--   ① 菜单借用了「费用政策查询」的 policy:list，与页面内 compliance:review:* 不是一套码；
--   ② 菜单挂在「系统管理」下，语义上属「共享运营」；
--   ③ 库里躺着 3 条「波次十四」测试规则（内容与 RR_AMOUNT_HIGH 完全相同），
--      另有一条 PRE-INV-DUPLICATE 引用了审单入参里根本不存在的 invoiceNumber，恒不命中。
--
-- 配套代码改动：createRule 缺省 action=MANUAL_REVIEW（原为空）、表单补全字段、接上启停开关。
-- 幂等：每条都带前置条件，重复执行影响 0 行。
-- 回滚：V1.6.22_review_rule_config_rollback.sql
-- =====================================================================

-- ① 权限码：policy:list → compliance:review:list（该菜单当前只授给内审经理/合规管理岗，
--    两角色均已持有 compliance:review:list，改码不掉权限）
UPDATE `sys_menu`
   SET `perms` = 'compliance:review:list',
       `update_time` = NOW()
 WHERE `id` = 3917
   AND `perms` = 'policy:list';

-- ② 归属：系统管理(3390) → 共享运营(105)
UPDATE `sys_menu`
   SET `parent_id` = 105,
       `sort` = 99,
       `update_time` = NOW()
 WHERE `id` = 3917
   AND `parent_id` = 3390;

-- ③ 清理波次十四测试规则（与 RR_AMOUNT_HIGH 同内容的克隆，本就 DISABLED）
DELETE FROM `op_review_rule`
 WHERE `rule_code` IN ('W14UNN7-REV', 'W14B6RO-RULE', 'W14B6RO-REV');

-- ④ PRE-INV-DUPLICATE：表达式引用的 invoiceNumber 不在 SharingService 组装的审单入参里，
--    永远不会命中（看着是「发票重复报销拦截」，实际空转）⇒ 先停用并留痕
UPDATE `op_review_rule`
   SET `status` = 'DISABLED',
       `remark` = CONCAT(IFNULL(`remark`, ''),
                         IF(IFNULL(`remark`, '') = '', '', ' | '),
                         '2026-11-05 停用：表达式引用的 invoiceNumber 不在审单入参中，恒不命中；重复发票拦截应走预检规则（precheck），待确认后再启'),
       `updated_at` = NOW()
 WHERE `rule_code` = 'PRE-INV-DUPLICATE'
   AND `status` = 'NORMAL';

-- 自检：菜单应为 compliance:review:list / parent 105；波次规则应为 0 行；PRE-INV 应 DISABLED
SELECT `id`, `menu_name`, `perms`, `parent_id`, `sort` FROM `sys_menu` WHERE `id` = 3917;
SELECT `rule_code`, `rule_name`, `action`, `status` FROM `op_review_rule` ORDER BY `priority`, `id`;
