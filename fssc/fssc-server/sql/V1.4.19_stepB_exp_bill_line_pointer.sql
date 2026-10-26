-- =====================================================================
-- V1.4.19 step B ——  业务事实表写入，超出本轮「主数据/规则表可写」授权，需单独确认后才执行
-- ---------------------------------------------------------------------
-- 为什么必须有这一步：审批通过/驳回时的核销与释放读的是 exp_bill_line.budget_item_id
--   （ExpenseWfEventListener:281/517/751）。V1.4.19 主脚本把流水改到 97954 后，
--   若行指针仍留 98262，则该单在 98262 上净占用为 0 → C2 判"无占用可核"整体跳过
--   → 97954 上这 9,000 永久悬挂为占用、永不转执行；驳回场景还会在 98262 上产生重复负记。
-- 代码轨部署后**新提交**的单据已由 `ExpenseBillService:608-618` 自动回写正确行；
-- 本脚本只处理部署前既有的这 2 条历史行。
-- =====================================================================

-- 快照（回滚依据）
CREATE TABLE IF NOT EXISTS bak_v1419_bill_line AS
SELECT id, bill_id, line_no, item_id, budget_item_id, amount
FROM exp_bill_line WHERE id IN (8433, 8434);

-- 改指正确预算行（去重：已是 97954 则不重复写）
UPDATE exp_bill_line
SET budget_item_id = 97954
WHERE id IN (8433, 8434) AND budget_item_id = 98262;

-- 验收：B1 期望 0 行（不存在"行指针指向异主体/异年度预算行"）
SELECT COUNT(*) AS b1_line_ptr_mismatch
FROM exp_bill_line l
JOIN exp_bill b ON b.id = l.bill_id
JOIN bg_item i ON i.id = l.budget_item_id
WHERE l.id IN (8433, 8434)
  AND (i.company_id <> b.company_id OR i.budget_year <> b.period_year OR i.item_id <> l.item_id);

-- 验收：B2 期望 8433/8434 的 budget_item_id = 97954
SELECT id, bill_id, line_no, item_id, budget_item_id, amount
FROM exp_bill_line WHERE id IN (8433, 8434);
