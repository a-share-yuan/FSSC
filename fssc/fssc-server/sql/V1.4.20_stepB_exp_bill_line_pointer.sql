-- =====================================================================
-- V1.4.20 step B —— 业务事实表 `exp_bill_line` 写入（用户 09-22 已就同类改动批准：「要修」）
-- ---------------------------------------------------------------------
-- 与 V1.4.20 主脚本**同批**执行：核销/释放读 `exp_bill_line.budget_item_id`
--   （`ExpenseWfEventListener:281/517/751`）。主脚本把流水挪到目标行后，
--   若行指针仍留 97958，则该单在 97958 净占用为 0 → C2 判"无占用可核"整体跳过
--   → 目标行上的占用永久悬挂、永不转执行；驳回还会在 97958 产生重复负记。
-- 目标行＝主脚本步骤 2 的同一映射（代码 `matchItem` 取行结果），不另立规则。
-- 只改 `del_flag=0` 且当前确实指向 97958 的行 → 命中 11 行（每单 1 行；5831/5839/5840 的第 2 行是已删除行，不动）。
-- 去重：已是目标行的不重复写。
-- 执行方式：带 utf8mb4 连接（`SET NAMES utf8mb4;` 或 `--default-character-set=utf8mb4`）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS bak_v1420_bill_line AS
SELECT l.id, l.bill_id, l.line_no, l.item_id, l.budget_item_id, l.amount
FROM exp_bill_line l WHERE l.bill_id IN (5831,5839,5840,5841,5842,5843,5844,5845,5846,5847,5860);

-- 9 张 333 行政办公费 → 97954；5846(403) → 98508；5860(439) → 98526
UPDATE exp_bill_line l
JOIN (SELECT 5831 AS bill_id, 97954 AS target_item_id UNION ALL
      SELECT 5839, 97954 UNION ALL SELECT 5840, 97954 UNION ALL SELECT 5841, 97954 UNION ALL
      SELECT 5842, 97954 UNION ALL SELECT 5843, 97954 UNION ALL SELECT 5844, 97954 UNION ALL
      SELECT 5845, 97954 UNION ALL SELECT 5847, 97954 UNION ALL
      SELECT 5846, 98508 UNION ALL
      SELECT 5860, 98526) m ON m.bill_id = l.bill_id
SET l.budget_item_id = m.target_item_id
WHERE l.del_flag = 0 AND l.budget_item_id = 97958;

-- ---------- 验收 ----------
-- C1 期望 0 行：这些单仍指向 97958
SELECT COUNT(*) AS c1_still_on_wrong_row FROM exp_bill_line
WHERE del_flag = 0 AND budget_item_id = 97958
  AND bill_id IN (5831,5839,5840,5841,5842,5843,5844,5845,5846,5847,5860);

-- C2 期望 11 行，且每行 budget_item_id 与"该行费用项在 company1/2026 的预算行"一致
SELECT l.id AS line_id, l.bill_id, l.line_no, l.item_id, l.budget_item_id, i.dim_key, l.amount
FROM exp_bill_line l JOIN bg_item i ON i.id = l.budget_item_id
WHERE l.del_flag = 0 AND l.bill_id IN (5831,5839,5840,5841,5842,5843,5844,5845,5846,5847,5860)
ORDER BY l.bill_id, l.line_no;

-- C3 关键一致性：每单的净占用必须**全部落在自己行指针指向的那一行**
--   期望 11 行，net_elsewhere 全为 0.00，net_at_ptr 等于行金额（审批通过时 C2 才核得到）
SELECT l.bill_id, l.id AS line_id, l.budget_item_id AS line_ptr, l.amount,
       (SELECT COALESCE(SUM(o.amount),0) FROM bg_occupy o
          WHERE o.biz_id=l.bill_id AND o.biz_type<>'INIT_CARRY' AND o.budget_item_id=l.budget_item_id) AS net_at_ptr,
       (SELECT COALESCE(SUM(o.amount),0) FROM bg_occupy o
          WHERE o.biz_id=l.bill_id AND o.biz_type<>'INIT_CARRY' AND o.budget_item_id<>l.budget_item_id) AS net_elsewhere
FROM exp_bill_line l
WHERE l.del_flag = 0 AND l.bill_id IN (5831,5839,5840,5841,5842,5843,5844,5845,5846,5847,5860)
ORDER BY l.bill_id, l.line_no;
