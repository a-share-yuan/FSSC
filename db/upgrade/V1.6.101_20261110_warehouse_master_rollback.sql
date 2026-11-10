-- =====================================================================
-- V1.6.101 回滚：仓库主数据
--
-- 回滚后**存货领用/处置会再次不可用**（回到「请选择仓库」→ 400）——
--   这是变更前的真实状态，不是回滚引入的问题。
--
-- 回滚分四步，**第 ④ 步需人工确认后手动执行**（不自动 DROP，防止误删他人后加的仓）：
--   ① 清掉 md_inventory 里指向本脚本两个仓的引用（还原为 NULL）
--   ② 删种子行（WH-MAIN / WH-KS）
--   ③ 检查是否还有其它仓 —— 有则停下，人工确认
--   ④ 确认无残留后，自行取消最后一行的注释执行 DROP
-- =====================================================================

-- ① 还原引用：只清「指向本脚本建的那两个仓」的引用，不碰其它来源的赋值
UPDATE md_inventory
SET warehouse_id = NULL
WHERE warehouse_id IN (
    SELECT id FROM md_warehouse WHERE tenant_id = 1 AND warehouse_code IN ('WH-MAIN', 'WH-KS')
);

-- ② 删种子行
DELETE FROM md_warehouse WHERE tenant_id = 1 AND warehouse_code IN ('WH-MAIN', 'WH-KS');

-- ③ 检查残留：期望 0；若 > 0 说明库里有他人自建的仓 ⇒ **不要执行第 ④ 步**
SELECT COUNT(*) AS 剩余仓库数_期望0_非0请人工确认
FROM md_warehouse;

-- ④ 删表（仅在第 ③ 步返回 0 时，手工取消下面注释后执行）
-- DROP TABLE IF EXISTS md_warehouse;

-- =====================================================================
-- 回滚后自检（应回到变更前）
--   SELECT COUNT(*) FROM md_inventory WHERE warehouse_id IS NOT NULL;   -- 期望 0
--   SHOW TABLES LIKE 'md_warehouse';                                     -- 期望空
-- =====================================================================
