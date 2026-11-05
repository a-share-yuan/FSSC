-- =====================================================================
-- 2026-11-05  差旅费·BG202611 预算行解冻（演示环境数据修正）
--
-- 背景：bg_item.id=6（tenant 1 / company 1 / item_id=335 差旅费 / 2026 年 / BG202611）
--       在 11-03 的预算冻结演练里被置 freeze=1，演练结束后一直没有解冻。
--       前端预算页只提供「冻结」，没有「解冻」入口，这一行成为环境中唯一被冻结的预算行，
--       事前申请提交时被「预算行已冻结，拒绝新增占用」拦下（D-V5-1105-01）。
--
-- 影响面：仅这一行。freeze=1 表示「拒绝新增占用，释放/核销照常」，
--         已占用的 14800.00 不受影响，解冻只恢复新增占用能力。
--
-- 幂等：带 freeze = 1 条件，重复执行影响 0 行。
-- 回滚：20261105_bg_item6_unfreeze_rollback.sql
-- =====================================================================

UPDATE `bg_item`
   SET `freeze`      = 0,
       `remark`      = '[2026-11-05 F1真跑前解冻:遗留冻结行阻塞事前申请提交]',
       `update_by`   = 1,
       `update_time` = NOW()
 WHERE `id` = 6
   AND `tenant_id` = 1
   AND `item_id` = 335
   AND `freeze` = 1;

-- 执行后应返回 0 行；若有残留说明 id/item 对不上，先核对再重跑
SELECT `id`, `tenant_id`, `company_id`, `item_id`, `budget_year`, `budget_period`,
       `freeze`, `occupy_amount`, `used_amount`, `remark`
  FROM `bg_item`
 WHERE `id` = 6;

-- 环境级核对：本库不应再有任何冻结行
SELECT COUNT(*) AS frozen_rows FROM `bg_item` WHERE `freeze` = 1 AND `del_flag` = 0;
