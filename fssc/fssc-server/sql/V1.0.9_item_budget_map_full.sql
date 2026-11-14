-- =============================================================================
-- V1.0.9  md_item_budget_map 费用类型→预算科目 映射补全（FINAL-01）
-- 现状：仅 3 条种子映射(330/331/332，且 item_name 与 md_expense_item 实际名称不符：
--       330=职工福利、331=劳动保护费、332=劳务支出，并非 差旅/办公/招待)。
-- 可用预算科目(bg_item，tenant 1, scheme 1, company 1, dept 10003, 2026)：
--   id=19 dim=2026-COMPANY1-DEPT10003-ITEM334  综合预算(默认默认处理, 额度 99999999)
--   id=20 dim=2026-COMPANY1-DEPT10003-ITEM331  办公预算
--   id=21 dim=2026-COMPANY1-DEPT10003-ITEM330  差旅预算
--   id=22 dim=2026-COMPANY1-DEPT10003-ITEM332  招待预算
-- 映射原则(按 md_expense_item.item_name 关键词)：
--   含"差旅"→21 差旅预算；含"办公"→20 办公预算；含"招待"→22 招待预算；
--   含"薪酬/工资/税"等无专属预算科目 → 19 综合预算；其余默认 → 19 综合预算。
-- 去重：可重复执行(先纠正历史 3 条 item_name/指向，再补缺失映射)。
-- =============================================================================

-- 1) 纠正历史 3 条的 item_name 与映射指向(原种子把 330/331/332 标成 差旅/办公/招待，
--    实际这三个 id 是 职工福利/劳动保护费/劳务支出，应归入综合预算 19)
UPDATE md_item_budget_map m
  JOIN md_expense_item e ON e.id = m.item_id
   SET m.item_name = e.item_name,
       m.budget_item_id = 19,
       m.budget_dim_key = '2026-COMPANY1-DEPT10003-ITEM334',
       m.remark = 'V1.0.9 按名称关键词重映射：默认→综合预算(19)',
       m.update_time = NOW()
 WHERE m.item_id IN (330, 331, 332)
   AND NOT (e.item_name LIKE '%差旅%' OR e.item_name LIKE '%办公%' OR e.item_name LIKE '%招待%');

-- 2) 批量补全：所有尚未映射的费用类型(含 211 个全目录)按关键词映射
INSERT INTO md_item_budget_map
       (tenant_id, item_id, item_name, budget_item_id, budget_dim_key, status, remark, create_by, create_time, del_flag)
SELECT 1, e.id, e.item_name,
       CASE
         WHEN e.item_name LIKE '%差旅%' THEN 21
         WHEN e.item_name LIKE '%办公%' THEN 20
         WHEN e.item_name LIKE '%招待%' THEN 22
         ELSE 19
       END,
       CASE
         WHEN e.item_name LIKE '%差旅%' THEN '2026-COMPANY1-DEPT10003-ITEM330'
         WHEN e.item_name LIKE '%办公%' THEN '2026-COMPANY1-DEPT10003-ITEM331'
         WHEN e.item_name LIKE '%招待%' THEN '2026-COMPANY1-DEPT10003-ITEM332'
         ELSE '2026-COMPANY1-DEPT10003-ITEM334'
       END,
       'NORMAL',
       CONCAT('V1.0.9 自动映射:',
              CASE
                WHEN e.item_name LIKE '%差旅%' THEN '差旅→21'
                WHEN e.item_name LIKE '%办公%' THEN '办公→20'
                WHEN e.item_name LIKE '%招待%' THEN '招待→22'
                ELSE '默认→综合19'
              END),
       1, NOW(), 0
  FROM md_expense_item e
  LEFT JOIN md_item_budget_map m ON m.item_id = e.id AND m.del_flag = 0
 WHERE m.id IS NULL;

-- 3) 校验(执行后人工核对)：映射总数应 = md_expense_item 总数(211)
-- SELECT COUNT(*) AS map_total FROM md_item_budget_map WHERE del_flag=0;
-- SELECT budget_item_id, COUNT(*) FROM md_item_budget_map WHERE del_flag=0 GROUP BY budget_item_id;
