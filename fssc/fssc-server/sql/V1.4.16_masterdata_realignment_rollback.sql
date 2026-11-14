-- =====================================================================================
-- V1.4.16  基础数据重整 · 规则/主数据层（rollback）
-- 与 V1.4.16_masterdata_realignment.sql 配对；依赖其步骤 0 建的快照表。
-- 最后防线：自动化测试/backup/fssc_masterdata_20260921_1950.sql（mysqldump）
-- 原则：本脚本只撤销"重整新增/改写"的配置，不撤销重整之后业务新产生的数据 ——
--       有占用流水的预算行、已被月结关闭的期间一律保留（宁可留残留，不可删业务）。
-- =====================================================================================

START TRANSACTION;

-- 1) 撤销本次建档的预算行（仅限仍无业务流水、金额为 0 的行）
DELETE i FROM bg_item i
LEFT JOIN bg_occupy o ON o.budget_item_id = i.id AND o.biz_type <> 'INIT_CARRY'
WHERE i.remark LIKE 'V1.4.16 重整建档%' AND o.id IS NULL;

-- 2) 还原 bg_item.scheme_id（含仍保留的行）
UPDATE bg_item b
JOIN bak_v1416_bg_item_scheme k ON k.id = b.id
SET b.scheme_id = k.scheme_id;

-- 3) 撤销本次新建的预算方案（仍被在用的预算行引用时不删）
DELETE s FROM bg_scheme s
LEFT JOIN bg_item i ON i.scheme_id = s.id AND i.del_flag = 0
WHERE s.remark LIKE 'V1.4.16 重整建档%' AND i.id IS NULL;

-- 4) 还原费用项→预算映射
UPDATE md_item_budget_map m
JOIN bak_v1416_item_budget_map k ON k.id = m.id
SET m.budget_item_id = k.budget_item_id,
    m.budget_dim_key = k.budget_dim_key,
    m.status         = k.status,
    m.remark         = k.remark;

-- 5) 撤销本次建档的会计期间（md_period 无 remark 列 → 按建档前主键快照识别；
--    只删仍为 NORMAL 的：已被月结关闭的期间属业务事实，保留）
DELETE p FROM md_period p
LEFT JOIN bak_v1416_period_before k ON k.id = p.id
WHERE k.id IS NULL AND p.status = 'NORMAL' AND p.period_year IN (2026, 2027);

-- 6) 撤销演示规则的汇率续期（ROLLFORWARD 为本次标记，真实牌价行不受影响）
DELETE FROM md_exchange_rate WHERE source = 'ROLLFORWARD';

COMMIT;

-- 快照表保留备查，确认无需二次回滚后由执行人手工清理：
-- DROP TABLE IF EXISTS bak_v1416_bg_item_scheme;
-- DROP TABLE IF EXISTS bak_v1416_item_budget_map;
-- DROP TABLE IF EXISTS bak_v1416_period_before;
