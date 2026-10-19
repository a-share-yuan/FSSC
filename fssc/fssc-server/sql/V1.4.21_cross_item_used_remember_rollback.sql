-- =====================================================================
-- V1.4.21  回滚（先跑本文件，再跑 V1.4.21_stepB_..._rollback.sql；或直接按下方顺序一次性执行）
-- ---------------------------------------------------------------------
-- 顺序：① 删本批新增的 40 条流水（按 remark 标签，历史流水一条不动）
--       ② bg_item 的 occupy_amount / used_amount 从 `bak_v1421_item` 快照还原
--       ③ 校验回到执行前基线
--       ④ （业务表指针）从 `bak_v1421_bill_line` 还原 —— 见 stepB 回滚段
-- 执行方式同主脚本：输入流首行加 `SET NAMES utf8mb4;`。
-- =====================================================================

-- ---------- ① 删除本批流水 ----------
DELETE FROM bg_occupy WHERE remark LIKE 'V1.4.21%';

-- ---------- ② 还原 bg_item 两个派生列（其余列不动；total_amount 本批从未改过） ----------
UPDATE bg_item i
JOIN bak_v1421_item s ON s.id = i.id
SET i.occupy_amount = s.occupy_amount,
    i.used_amount   = s.used_amount;

-- ---------- ③ 还原校验 ----------
-- R1 期望 0：残留的 V1.4.21 流水
SELECT COUNT(*) AS r1_leftover_flows FROM bg_occupy WHERE remark LIKE 'V1.4.21%';

-- R2 期望 0 行：五行的 occupy/used 与快照不一致
SELECT i.id, i.dim_key, i.occupy_amount AS now_occupy, s.occupy_amount AS snap_occupy,
       i.used_amount AS now_used, s.used_amount AS snap_used
FROM bg_item i JOIN bak_v1421_item s ON s.id = i.id
WHERE i.occupy_amount <> s.occupy_amount OR i.used_amount <> s.used_amount;

-- R3 期望回到执行前基线：used 817,106.03 / 56,770.90 / 97,984.30 / 0 / 0
SELECT id, dim_key, occupy_amount, used_amount FROM bg_item
WHERE id IN (97958, 97954, 97957, 98490, 98526) ORDER BY id;

-- R4 期望 691,513.15 / 10 单 / 13 笔：已用侧错记重新出现（＝回滚成功的正面证据）
SELECT COALESCE(SUM(-o.amount),0) AS r4_used_mis_total, COUNT(DISTINCT o.biz_id) AS r4_bills, COUNT(*) AS r4_flows
FROM bg_occupy o JOIN bg_item i ON i.id=o.budget_item_id JOIN exp_bill b ON b.id=o.biz_id AND b.del_flag=0
WHERE o.oper_type='WRITE_OFF' AND o.biz_type<>'INIT_CARRY' AND o.amount < 0
  AND NOT EXISTS (SELECT 1 FROM exp_bill_line l2 JOIN md_expense_item le2 ON le2.id=l2.item_id
                  WHERE l2.bill_id=b.id AND l2.del_flag=0
                    AND (i.item_id=l2.item_id OR FIND_IN_SET(i.item_id, le2.ancestors)));

-- R5 期望 0：不变式 I1 全库差异行
SELECT COUNT(*) AS r5_i1_broken
FROM bg_item i LEFT JOIN (SELECT budget_item_id, SUM(amount) s FROM bg_occupy GROUP BY budget_item_id) f
       ON f.budget_item_id = i.id
WHERE i.del_flag = 0 AND i.occupy_amount <> COALESCE(f.s, 0);

-- ---------- ④ 业务表指针还原（V1.4.21_stepB 的回滚段） ----------
UPDATE exp_bill_line l
JOIN bak_v1421_bill_line s ON s.id = l.id
SET l.budget_item_id = s.budget_item_id;

-- R6 期望 13 行、r6_back_to_97958 = 13（这 10 单的明细行全部回到 97958）
SELECT l.bill_id, COUNT(*) AS r6_lines,
       SUM(CASE WHEN l.budget_item_id = 97958 THEN 1 ELSE 0 END) AS r6_back_to_97958
FROM exp_bill_line l
WHERE l.del_flag = 0 AND l.bill_id IN (5818,5819,5825,5829,5837,5838,5854,5859,5861,5867)
GROUP BY l.bill_id ORDER BY l.bill_id;

-- ---------- 确认无误后可手工清理快照（默认保留，便于二次核对） ----------
-- DROP TABLE IF EXISTS bak_v1421_bill_line;
-- DROP TABLE IF EXISTS bak_v1421_map;
-- DROP TABLE IF EXISTS bak_v1421_item;
