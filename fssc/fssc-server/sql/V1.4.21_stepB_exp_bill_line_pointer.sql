-- =====================================================================
-- V1.4.21_stepB  `exp_bill_line.budget_item_id` 指针改指（**业务事实表**，用户 09-22 批复「A + C」）
-- ---------------------------------------------------------------------
-- 为什么必须与主脚本同批：核销/释放/反核销读的是行指针（`ExpenseWfEventListener:281/517/751`）。
--   本批 10 单的 13 个明细行现在仍全部指向 97958（职工福利）。只搬流水与 used 不改指针 →
--   将来任何撤销/反审核动作仍会打在 97958 上，把刚搬正的已用再次记错；
--   而"改了执行侧"与"指针没改"之间的不一致，正是 §8.11/§8.13 反复强调的中间态。
-- 范围：仅这 13 行；`AND l.budget_item_id = 97958` 守卫使重复执行成为空操作（去重）。
-- 执行方式：与主脚本同一会话连跑，且必须带 utf8mb4 连接（见主脚本头注释）。
-- =====================================================================

-- ---------- 步骤 0：快照 ----------
CREATE TABLE IF NOT EXISTS bak_v1421_bill_line AS
SELECT l.* FROM exp_bill_line l
WHERE l.del_flag = 0 AND l.bill_id IN (5818,5819,5825,5829,5837,5838,5854,5859,5861,5867);

-- ---------- 步骤 1：指针改指目标行（映射来自主脚本的 bak_v1421_map，单一事实来源） ----------
UPDATE exp_bill_line l
JOIN bak_v1421_map p ON p.biz_id = l.bill_id
SET l.budget_item_id = p.target_item_id
WHERE l.del_flag = 0 AND l.budget_item_id = 97958;

-- =====================================================================
-- 验收
-- =====================================================================
-- B1 期望 0 行：这 10 单仍有明细行指向 97958
SELECT l.bill_id, l.id AS line_id, l.line_no, l.item_id, l.amount, l.budget_item_id
FROM exp_bill_line l
WHERE l.del_flag = 0 AND l.budget_item_id = 97958
  AND l.bill_id IN (5818,5819,5825,5829,5837,5838,5854,5859,5861,5867);

-- B2 期望 13 行：指针 = 映射目标行，且**指针行的费用项 == 明细行费用项**（改完不能再错项）
SELECT l.bill_id, l.id AS line_id, l.line_no, l.item_id, le.item_name, l.amount,
       l.budget_item_id AS line_ptr, i.dim_key, i.item_id AS ptr_item,
       (i.item_id = l.item_id) AS b2_ptr_item_matches
FROM exp_bill_line l
JOIN bak_v1421_map p ON p.biz_id = l.bill_id
JOIN bg_item i ON i.id = l.budget_item_id
JOIN md_expense_item le ON le.id = l.item_id
WHERE l.del_flag = 0
ORDER BY l.bill_id, l.line_no;

-- B3 期望 13 行、b3_net_at_ptr = 0.00（占用与核销相抵）、b3_net_elsewhere = 0.00、
--    b3_used_at_ptr = −行金额合计（本单的已用确实落在指针行上）
SELECT l.bill_id, MIN(l.budget_item_id) AS line_ptr, SUM(l.amount) AS b3_line_amount,
       (SELECT COALESCE(SUM(o.amount),0) FROM bg_occupy o
         WHERE o.biz_id = l.bill_id AND o.biz_type<>'INIT_CARRY'
           AND o.budget_item_id = MIN(l.budget_item_id)) AS b3_net_at_ptr,
       (SELECT COALESCE(SUM(o.amount),0) FROM bg_occupy o
         WHERE o.biz_id = l.bill_id AND o.biz_type<>'INIT_CARRY'
           AND o.budget_item_id <> MIN(l.budget_item_id)) AS b3_net_elsewhere,
       (SELECT COALESCE(SUM(o.amount),0) FROM bg_occupy o
         WHERE o.biz_id = l.bill_id AND o.biz_type<>'INIT_CARRY' AND o.oper_type='WRITE_OFF'
           AND o.budget_item_id = MIN(l.budget_item_id)) AS b3_used_at_ptr
FROM exp_bill_line l
WHERE l.del_flag = 0 AND l.bill_id IN (SELECT biz_id FROM bak_v1421_map)
GROUP BY l.bill_id ORDER BY b3_line_amount DESC;

-- B4 期望 0：全库行指针指向"非本费用项且非祖先"的预算行（祖先链规则，与 §8.11 B1 同判据）。
--    若非 0 不代表本批失败，而是**新线索**：需按 §8.13 的无扇出规则复核后单独立项，勿直接下结论。
--    >>> 实测 09-22：**17 行 / 16 单 / 明细额 38,022.00，本批 13 行不在其中**（B2 已证 ptr_item==item_id）。
--        复核结论见 `自动化测试/FSSC基础数据重整_草稿错项指针残留勘查_20260922.sql` + 同名 `_输出` 与审计文档 §8.14：
--        全部是 DRAFT(15 单)/VOID(1 单)，且这 16 单在 `bg_occupy` **一笔流水都没有** → 当前零预算影响，
--        不是本批回归，也不是新的错账；真正的风险是"日后提交这些草稿"时指针被复用（见 §8.14 复发链路）。
SELECT COUNT(*) AS b4_ptr_wrong_item
FROM exp_bill_line l JOIN md_expense_item le ON le.id = l.item_id
JOIN exp_bill b ON b.id = l.bill_id AND b.del_flag = 0
JOIN bg_item i ON i.id = l.budget_item_id AND i.del_flag = 0
WHERE l.del_flag = 0
  AND NOT (i.item_id = l.item_id OR FIND_IN_SET(i.item_id, le.ancestors));
