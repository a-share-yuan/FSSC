-- =====================================================================
-- V1.4.20  同主体·同年度·**跨费用项**占用冲销重占（8.11「新问题 2」，4.9-1 判据扩展）
-- ---------------------------------------------------------------------
-- 事实（09-22 只读勘查，归档 `自动化测试/FSSC基础数据重整_C1执行后残留扫描_20260922.sql` + 同名 `_输出`）：
--   11 张 APPROVING 报销单、净占用 **10,773.47**，全部误记在
--   `bg_item 97958` = `2026-COMPANY1-DEPT1001-ITEM330`（职工福利）上，而单据明细费用项是 333/403/439。
--   主体、年度都对，**只有费用项错**，故 4.9-1 的"跨主体/跨年度"判据没覆盖到；根因与 C1 同源
--   （V1.4.16 之前 `md_item_budget_map` 211 条坍缩指向少数主键）。
--   实测 97958 上的非结转流水**恰好只有**这 11 笔 OCCUPY（净额 10,773.47，另 3,200.00 是 V1.4.17 期初结转），
--   所以"逐笔负记"不会波及其它单据；已 APPROVED/PAID 的错项单净额已归零（占用→已用），本批**不动**（见文末）。
-- 目标行＝**部署后代码 `matchItem` 的取行结果**（部门精确 > 年度就近 > period 离 0 近 > 年份新 > dim_key），
--   用同一比较器在只读环境逐单模拟（`_t2` 检查，max 候选行 7 < Java 侧 limit 200 → 模拟与实现等价）：
--     9 张 item 333 行政办公费 → 97954 `2026-COMPANY1-DEPT1001-ITEM333`（ACTIVE/WARN，总额 1,137,400）
--     5846 item 403 国内出差-住宿费 → 98508 `2026-COMPANY1-ITEM403`（ACTIVE/WARN，**总额 0**）
--     5860 item 439 办公设施维修   → 98526 `2026-COMPANY1-ITEM439`（ACTIVE/WARN，**总额 0**）
--   选"与代码同一规则"而非"挑一条有额度的父级行"，是为了让历史数与今后同类单据落在同一行；
--   98508/98526 额度 0 只会在 WARN 下记录放行（`total_amount` 一律不增，**不编额度**）。
--
-- 范围：本文件只写 **bg_occupy（流水）+ bg_item（派生列）**。
--    必须与 `V1.4.20_stepB_exp_bill_line_pointer.sql`（业务表指针改指）**同批执行**，理由同 V1.4.19：
--   核销/释放读 `exp_bill_line.budget_item_id`（`ExpenseWfEventListener:281/517/751`），
--   只冲销不改指针 → 审批通过时该单在 97958 净占用已为 0 → C2 判"无占用可核"整体跳过
--   → 目标行上的占用永久悬挂、永不转执行；驳回还会在 97958 叠重复负记（release 无夹紧为已知挂账）。
-- 去重：所有写入带 `remark` 前缀 'V1.4.20' 的 NOT EXISTS 守卫，可重复执行。
-- 执行方式：**必须带 utf8mb4 连接**（`{ echo "SET NAMES utf8mb4;"; cat 本文件 stepB; } | mysql --comments`），
--   否则中文 CONCAT 与 utf8mb4_0900_ai_ci 列冲突 → ERROR 1271（V1.4.19 实测坑）。
-- =====================================================================

-- ---------- 步骤 0：快照（回滚依据） ----------
CREATE TABLE IF NOT EXISTS bak_v1420_item AS
SELECT * FROM bg_item WHERE id IN (97958, 97954, 98508, 98526);

-- ---------- 步骤 1：错误行 97958 按原占用逐笔负记 ----------
INSERT INTO bg_occupy
    (tenant_id, budget_item_id, oper_type, amount, biz_type, biz_id, biz_no, biz_line_id, oper_user_id, remark, create_time)
SELECT o.tenant_id, 97958, 'RELEASE', -o.amount, o.biz_type, o.biz_id, o.biz_no, o.biz_line_id, 1,
       CONCAT('V1.4.20 跨费用项冲销：单据 ', o.biz_no, ' 明细费用项非 330(职工福利)，原占用误记在本行 ',
              o.amount, '，按预算调整-冲销重占口径负记；非业务驳回'),
       NOW()
FROM bg_occupy o
WHERE o.budget_item_id = 97958 AND o.oper_type = 'OCCUPY' AND o.biz_type <> 'INIT_CARRY'
  AND o.biz_id IN (5831,5839,5840,5841,5842,5843,5844,5845,5846,5847,5860)
  AND NOT EXISTS (SELECT 1 FROM bg_occupy x
                  WHERE x.budget_item_id = 97958 AND x.oper_type = 'RELEASE'
                    AND x.biz_id = o.biz_id AND x.biz_type = o.biz_type AND x.remark LIKE 'V1.4.20%');

-- ---------- 步骤 2：按代码取行结果逐笔重占（biz_id → 目标行 映射显式列出，便于复核） ----------
INSERT INTO bg_occupy
    (tenant_id, budget_item_id, oper_type, amount, biz_type, biz_id, biz_no, biz_line_id, oper_user_id, remark, create_time)
SELECT o.tenant_id, m.target_item_id, 'OCCUPY', o.amount, o.biz_type, o.biz_id, o.biz_no, o.biz_line_id, 1,
       CONCAT('V1.4.20 跨费用项重占：单据 ', o.biz_no, ' 按解析链(公司1/dept1001/2026/明细费用项)改占本行 ',
              o.amount, '，与 97958 的冲销成对；非本期新增业务'),
       NOW()
FROM bg_occupy o
JOIN (SELECT 5831 AS biz_id, 97954 AS target_item_id UNION ALL
      SELECT 5839, 97954 UNION ALL SELECT 5840, 97954 UNION ALL SELECT 5841, 97954 UNION ALL
      SELECT 5842, 97954 UNION ALL SELECT 5843, 97954 UNION ALL SELECT 5844, 97954 UNION ALL
      SELECT 5845, 97954 UNION ALL SELECT 5847, 97954 UNION ALL
      SELECT 5846, 98508 UNION ALL
      SELECT 5860, 98526) m ON m.biz_id = o.biz_id
WHERE o.budget_item_id = 97958 AND o.oper_type = 'OCCUPY' AND o.biz_type <> 'INIT_CARRY'
  AND NOT EXISTS (SELECT 1 FROM bg_occupy x
                  WHERE x.budget_item_id = m.target_item_id AND x.oper_type = 'OCCUPY'
                    AND x.biz_id = o.biz_id AND x.biz_type = o.biz_type AND x.remark LIKE 'V1.4.20%');

-- ---------- 步骤 3：派生列按账本重算（不变式 I1），只动本次涉及 4 行 ----------
UPDATE bg_item i
SET i.occupy_amount = (SELECT COALESCE(SUM(o.amount), 0) FROM bg_occupy o WHERE o.budget_item_id = i.id)
WHERE i.id IN (97958, 97954, 98508, 98526);

-- ---------- 验收（规则：净额，不是流水条数——账本只增不改，成对流水必须保留） ----------
-- N1 期望 0 行：这 11 张单在错误行 97958 上仍有净占用
SELECT o.biz_id, o.biz_no, COALESCE(SUM(o.amount), 0) AS net_on_97958
FROM bg_occupy o WHERE o.budget_item_id = 97958 AND o.biz_type <> 'INIT_CARRY'
  AND o.biz_id IN (5831,5839,5840,5841,5842,5843,5844,5845,5846,5847,5860)
GROUP BY o.biz_id, o.biz_no HAVING net_on_97958 <> 0;

-- N2 期望 11 行：每单在目标行上的净额 = 单据占用额（5846→2,400@98508、5860→1,573.47@98526，余 9 单合计 6,800@97954）
SELECT o.biz_id, o.biz_no, o.budget_item_id, SUM(o.amount) AS net_on_target
FROM bg_occupy o WHERE o.biz_type <> 'INIT_CARRY'
  AND o.biz_id IN (5831,5839,5840,5841,5842,5843,5844,5845,5846,5847,5860)
  AND o.budget_item_id IN (97954, 98508, 98526)
GROUP BY o.biz_id, o.biz_no, o.budget_item_id ORDER BY o.biz_id;

-- N3 期望 0：全库（祖先链规则）仍有"净占用落在异费用项行"的单据
SELECT b.id AS bill_id, b.bill_no,
       (SELECT COALESCE(SUM(o.amount),0) FROM bg_occupy o JOIN bg_item i ON i.id=o.budget_item_id
          WHERE o.biz_id=b.id AND o.biz_type<>'INIT_CARRY'
            AND NOT EXISTS (SELECT 1 FROM exp_bill_line l JOIN md_expense_item le ON le.id=l.item_id
                            WHERE l.bill_id=b.id AND l.del_flag=0
                              AND (i.item_id=l.item_id OR FIND_IN_SET(i.item_id, le.ancestors)))) AS net_wrong_item
FROM exp_bill b
HAVING net_wrong_item <> 0;

-- N4 期望 0：不变式 I1 全库差异行
SELECT COUNT(*) AS n4_i1_broken
FROM bg_item i LEFT JOIN (SELECT budget_item_id, SUM(amount) s FROM bg_occupy GROUP BY budget_item_id) f
       ON f.budget_item_id = i.id
WHERE i.del_flag = 0 AND i.occupy_amount <> COALESCE(f.s, 0);

-- N5 四行终值（期望 97958 occupy 3,200.00（仅剩期初结转）；97954 16,780.00；98508 2,400.00；98526 1,573.47）
SELECT id, dim_key, total_amount, occupy_amount, used_amount FROM bg_item
WHERE id IN (97958, 97954, 98508, 98526) ORDER BY id;

-- ---------- 本批**未做**（另计，勿混入本脚本验收） ----------
-- 97958 上还有 10 张已 APPROVED/PAID 的错项单，其占用已通过 WRITE_OFF 转入本行 `used_amount`（本行已用 817,106.03）。
-- 净额已归零 → 无"悬挂占用"，但**已用记在了别的费用项头上**。按用户红线（所有数据保留、错账走调整凭证），
-- 这部分只出清单交财务认定，不在本脚本移动。
