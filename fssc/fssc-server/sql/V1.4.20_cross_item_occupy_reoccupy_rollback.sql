-- =====================================================================
-- V1.4.20 回滚 —— 跨费用项冲销重占撤销（主脚本 + step B 一并回到执行前状态）
-- 前提：期间这 11 单未发生核销/释放。若已发生，须先人工核对流水再回滚（见末尾 R4）。
-- 执行方式：带 utf8mb4 连接（`--default-character-set=utf8mb4`），否则 R1 的中文 LIKE 与客户端编码不一致。
-- =====================================================================

-- 1) 删除本次新增的 22 条流水（'V1.4.20' 标签为本脚本独占）
DELETE FROM bg_occupy WHERE remark LIKE 'V1.4.20%'
  AND budget_item_id IN (97958, 97954, 98508, 98526);

-- 2) 派生列回到快照值
UPDATE bg_item i
JOIN bak_v1420_item k ON k.id = i.id
SET i.occupy_amount = k.occupy_amount,
    i.used_amount   = k.used_amount,
    i.total_amount  = k.total_amount,
    i.adjust_amount = k.adjust_amount;

-- 3) 明细行指针回到快照值（仅当执行过 step B）
UPDATE exp_bill_line l
JOIN bak_v1420_bill_line k ON k.id = l.id
SET l.budget_item_id = k.budget_item_id
WHERE l.budget_item_id <> k.budget_item_id;

-- ---------- 验收 ----------
-- R1 期望 0：标签流水已清空
SELECT COUNT(*) AS r1_tagged_flows FROM bg_occupy WHERE remark LIKE 'V1.4.20%';

-- R2 期望 0 行差异：四行 occupy_amount 与快照一致
SELECT i.id, i.occupy_amount AS now_val, k.occupy_amount AS snap_val
FROM bg_item i JOIN bak_v1420_item k ON k.id = i.id
WHERE i.occupy_amount <> k.occupy_amount;

-- R3 期望回到执行前：11 张单在 97958 上净占用 10,773.47（撤销后跨费用项错配复现，属预期）
SELECT COUNT(DISTINCT o.biz_id) AS r3_bills, COALESCE(SUM(o.amount),0) AS r3_net
FROM bg_occupy o WHERE o.budget_item_id = 97958 AND o.biz_type <> 'INIT_CARRY'
  AND o.biz_id IN (5831,5839,5840,5841,5842,5843,5844,5845,5846,5847,5860);

-- R4 前置核对（回滚前手工看一眼）：这 11 单是否已被核销/释放
SELECT o.biz_no, o.oper_type, SUM(o.amount) AS amt, COUNT(*) AS flows
FROM bg_occupy o WHERE o.biz_id IN (5831,5839,5840,5841,5842,5843,5844,5845,5846,5847,5860)
GROUP BY o.biz_no, o.oper_type ORDER BY o.biz_no, o.oper_type;

-- 清理（确认不再需要时手工执行）
-- DROP TABLE bak_v1420_item;
-- DROP TABLE bak_v1420_bill_line;
