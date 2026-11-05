-- =====================================================================
-- V1.6.18  运输费预算行补开 + 孤儿预算映射清理（D-V4R-1103-07）
-- 背景：结算明细选「运输费」（md_expense_item.item_id=496）时「预算科目」带不出来——
--       496 在 bg_item 里一条 2026 年度的预算行都没有，且 md_item_budget_map 有 208 条
--       映射指向已被删除的 bg_item 行（孤儿）。同理 331 劳动保护费的 2026 映射
--       （budget_item_id=98563）也已悬空。
-- 本增量：① 备份全部孤儿映射（可回滚）；② 为 496／331 补 2026 年度·公司1·部门空 预算行；
--       ③ 把两条映射改指新行；④ 其余孤儿映射置 del_flag=1（不删行，留审计痕迹）。
-- 幂等：补行按 (company_id, budget_year, item_id, dept_id is null) 判在；
--       清理只作用于「改指之后仍然悬空」的行，重复执行第二次命中 0 行。
-- 回滚：V1.6.18_budget_item_496_orphan_map_rollback.sql
-- =====================================================================

-- ① 备份孤儿映射（只建一次；已存在则跳过，避免覆盖首次快照）
CREATE TABLE IF NOT EXISTS md_item_budget_map_bak20261104 AS
SELECT m.*
FROM md_item_budget_map m
LEFT JOIN bg_item b ON b.id = m.budget_item_id AND b.del_flag = 0
WHERE m.del_flag = 0 AND m.status = 'NORMAL' AND b.id IS NULL;

-- ② 补预算行：运输费 496（2026 / 公司1 / 部门空）
INSERT INTO bg_item (tenant_id, scheme_id, company_id, budget_year, budget_period, dept_id, item_id,
                     dim_key, total_amount, adjust_amount, occupy_amount, used_amount, freeze,
                     currency_code, remark, create_time)
SELECT 1, 101, 1, 2026, 0, NULL, 496,
       '2026-COMPANY1-DEPTnull-ITEM496', 100000.00, 0, 0, 0, 0,
       'CNY', '主数据补行：运输费 2026 年度预算（D-V4R-1103-07）', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM bg_item
                  WHERE company_id = 1 AND budget_year = 2026 AND item_id = 496
                    AND dept_id IS NULL AND del_flag = 0);

-- ② 补预算行：劳动保护费 331（原映射指向的 98563 已不存在）
INSERT INTO bg_item (tenant_id, scheme_id, company_id, budget_year, budget_period, dept_id, item_id,
                     dim_key, total_amount, adjust_amount, occupy_amount, used_amount, freeze,
                     currency_code, remark, create_time)
SELECT 1, 101, 1, 2026, 0, NULL, 331,
       '2026-COMPANY1-DEPTnull-ITEM331', 200000.00, 0, 0, 0, 0,
       'CNY', '主数据补行：劳动保护费 2026 年度预算（D-V4R-1103-07）', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM bg_item
                  WHERE company_id = 1 AND budget_year = 2026 AND item_id = 331
                    AND dept_id IS NULL AND del_flag = 0);

-- ③ 映射改指新行（只动这两个费用项，不动其它 206 条）
UPDATE md_item_budget_map m
JOIN bg_item b ON b.dim_key = '2026-COMPANY1-DEPTnull-ITEM496' AND b.del_flag = 0
SET m.budget_item_id = b.id
WHERE m.item_id = 496 AND m.del_flag = 0 AND m.budget_item_id <> b.id;

UPDATE md_item_budget_map m
JOIN bg_item b ON b.dim_key = '2026-COMPANY1-DEPTnull-ITEM331' AND b.del_flag = 0
SET m.budget_item_id = b.id
WHERE m.item_id = 331 AND m.del_flag = 0 AND m.budget_item_id <> b.id;

-- ④ 其余孤儿映射置失效（保留行与备注，便于审计与回滚）
UPDATE md_item_budget_map m
LEFT JOIN bg_item b ON b.id = m.budget_item_id AND b.del_flag = 0
SET m.del_flag = 1,
    m.remark = CONCAT(IFNULL(m.remark, ''), ' | 2026-11-04 孤儿映射清理（D-V4R-1103-07）')
WHERE m.del_flag = 0 AND b.id IS NULL;
