-- 回滚 V1.6.18：① 恢复被置失效的孤儿映射；② 把 496／331 的映射指回原值；③ 删除本增量补的两条预算行
-- 顺序不能颠倒：先恢复 del_flag，再改指，最后删预算行（否则外键式关联会先失效）

-- ① 恢复孤儿映射（用备份表按 id 回填 del_flag 与 remark）
UPDATE md_item_budget_map m
JOIN md_item_budget_map_bak20261104 b ON b.id = m.id
SET m.del_flag = b.del_flag,
    m.remark = b.remark;

-- ② 映射指回原值（496→101371、331→98563，取自清理前实测读数）
UPDATE md_item_budget_map SET budget_item_id = 101371 WHERE item_id = 496 AND del_flag = 0;
UPDATE md_item_budget_map SET budget_item_id = 98563  WHERE item_id = 331 AND del_flag = 0;

-- ③ 删除本增量补的预算行（按 dim_key 点删，不碰既有行）
DELETE FROM bg_item WHERE dim_key = '2026-COMPANY1-DEPTnull-ITEM496' AND del_flag = 0;
DELETE FROM bg_item WHERE dim_key = '2026-COMPANY1-DEPTnull-ITEM331' AND del_flag = 0;
