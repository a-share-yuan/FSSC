-- =====================================================================
-- 20261105_bg_item6_unfreeze.sql 的回滚
--
-- 把 bg_item.id=6（差旅费 / item_id=335）重新置回冻结态，用于复现问题或撤销修正。
-- 幂等：带 freeze = 0 条件，重复执行影响 0 行。
-- 回滚即恢复「事前申请被拦」的现象，仅在需要复现缺陷时使用。
-- =====================================================================

UPDATE `bg_item`
   SET `freeze`      = 1,
       `remark`      = '',
       `update_by`   = 1,
       `update_time` = NOW()
 WHERE `id` = 6
   AND `tenant_id` = 1
   AND `item_id` = 335
   AND `freeze` = 0;

SELECT `id`, `item_id`, `freeze`, `remark` FROM `bg_item` WHERE `id` = 6;
