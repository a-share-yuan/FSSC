-- =====================================================================
-- V1.4.19 回滚 —— C1 冲销重占撤销（主脚本 + step B 一并回到执行前状态）
-- 前提：期间这两单未发生核销/释放。若已发生，须先人工核对流水再回滚（见末尾 R4）。
-- 执行方式：带 utf8mb4 连接（`--default-character-set=utf8mb4`），否则 R1 的中文 LIKE 与客户端编码不一致。
-- =====================================================================

-- 1) 删除本次新增的 4 条流水（'V1.4.19' 标签为脚本独占）
DELETE FROM bg_occupy WHERE remark LIKE 'V1.4.19%' AND budget_item_id IN (98262, 97954);

-- 2) 派生列回到快照值
UPDATE bg_item i
JOIN bak_v1419_item k ON k.id = i.id
SET i.occupy_amount = k.occupy_amount,
    i.used_amount   = k.used_amount,
    i.total_amount  = k.total_amount,
    i.adjust_amount = k.adjust_amount;

-- 3) 明细行指针回到快照值（仅当执行过 step B）
UPDATE exp_bill_line l
JOIN bak_v1419_bill_line k ON k.id = l.id
SET l.budget_item_id = k.budget_item_id
WHERE l.budget_item_id <> k.budget_item_id;

-- ---------- 验收 ----------
-- R1 期望 0：标签流水已清空
SELECT COUNT(*) AS r1_tagged_flows FROM bg_occupy WHERE remark LIKE 'V1.4.19%';

-- R2 期望 98262 / 97954 的 occupy_amount 与快照一致（0 行差异）
SELECT i.id, i.occupy_amount AS now_val, k.occupy_amount AS snap_val
FROM bg_item i JOIN bak_v1419_item k ON k.id = i.id
WHERE i.occupy_amount <> k.occupy_amount;

-- R3 期望回到执行前：2 条错配流水（本脚本撤销后 C1 复现，属预期）
SELECT COUNT(*) AS r3_mismatch_flows
FROM bg_occupy o
JOIN bg_item i ON i.id = o.budget_item_id
JOIN exp_bill b ON b.id = o.biz_id
WHERE o.biz_type <> 'INIT_CARRY' AND (i.company_id <> b.company_id OR i.budget_year <> b.period_year);

-- R4 前置核对（回滚前手工看一眼）：这两单是否已被核销/释放
SELECT o.biz_no, o.oper_type, o.biz_type, SUM(o.amount) AS amt, COUNT(*) AS flows
FROM bg_occupy o WHERE o.biz_no IN ('BX202609210034', 'BX202609210035')
GROUP BY o.biz_no, o.oper_type, o.biz_type ORDER BY o.biz_no, o.oper_type;

-- 清理（确认不再需要时手工执行）
-- DROP TABLE bak_v1419_item;
-- DROP TABLE bak_v1419_bill_line;
