-- =====================================================================
-- V1.4.21  同主体·同年度·**跨费用项「已用侧」错记**的冲销重做（审计文档 §8.13 / 挂账 #31）
-- ---------------------------------------------------------------------
-- 事实（09-22 只读勘查，归档 `自动化测试/FSSC基础数据重整_跨费用项已用错记清单_20260922.sql` + 同名 `_输出`）：
--   10 张**已最终状态**单据（9 张 CLAIM/APPROVED + 1 张 PAY/PAID）的 13 笔 WRITE_OFF 核销，合计
--   **691,513.15**，全部把 `used_amount` 记在 `bg_item 97958` = `2026-COMPANY1-DEPT1001-ITEM330`（职工福利）上，
--   而单据明细费用项是 333 行政办公费 / 336 招待费 / 457 材料费 / 439 办公设施维修。
--   实测：97958 上**有流水支撑的已用 100% 属于别的费用项**（`wo_own = 0.00`），
--         本行自身真实已用为 0；余 125,592.88 是无任何核销流水的初始化直记（不属本次范围，也不动）。
--   每单在 97958 的净额已是 0（OCCUPY 与 WRITE_OFF 相抵）→ **无悬挂占用**，错的只是"已用记在谁头上"。
--
-- 与 V1.4.19 / V1.4.20 的差别：那两批改的是**在途占用**（净额非 0），本批改的是**已核销的已用**。
--   已用不参与不变式 I1（`occupy_amount == Σ 流水`），所以可以用"四环节"精确搬移而不惊动占用规则。
--
-- 四环节（每单 4 条新流水，两两净额 0 → I1 在两张行上都不被破坏）：
--   ① 97958  `OCCUPY`   −X   占用冲销
--   ② 97958  `WRITE_OFF` +X   核销反向（本行不再背这笔已用）
--   ③ 目标行 `OCCUPY`   +X   占用重做
--   ④ 目标行 `WRITE_OFF` −X   核销重做（已用改记到本行头上）
--   X 一律由账本自身派生（`bak_v1421_map`，排除 `V1.4.21` 环节 → 重复执行值稳定）。
--   粒度＝**单据**：`bg_occupy.biz_line_id` 全库 0% 填充（84 笔流水全 NULL，缺陷 #32），
--   单据粒度已是本库可追溯下限；5854(3×1,000)、5859(1,200+573,452.15) 各并为一条 X。
--
-- 目标行＝与部署后 `BudgetService.matchItem` 同一规则（部门精确 > 年度就近 > period 离 0 近 > 年份新 > dim_key）：
--   97954 2026-COMPANY1-DEPT1001-ITEM333 行政办公费   ← 5818/5829/5854/5859/5867   587,552.15
--   97957 2026-COMPANY1-DEPT1001-ITEM336 招待费       ← 5819/5837/5838              18,001.00
--   98490 2026-COMPANY1-ITEM457          材料费       ← 5825                        80,000.00  （**额度 0**）
--   98526 2026-COMPANY1-ITEM439          办公设施维修 ← 5861                         5,960.00  （**额度 0**）
--   用户 09-22 定案：保持叶子行，**不改挂有额度的父级行**（97956 差旅 / 97961 修理费均不接），
--   `total_amount` 一律不增（**不编额度**），超支由 WARN 方案记录放行。
--
-- used_amount 用"快照 + 显式差额"赋值（不是累加），故重复执行结果一致；
--   **不**按 Σ|WRITE_OFF| 重算：全库有 635 行 / ¥24,611,523.68 的已用本就是直记无流水，重算会清掉别行的种子数。
--
-- 必须与 `V1.4.21_stepB_exp_bill_line_pointer.sql` **同批执行**（用户 09-22 批复「A + C」）：
--   这 10 单的 13 个明细行 `budget_item_id` 现在仍全指 97958，核销/释放/反核销读行指针
--   （`ExpenseWfEventListener:281/517/751`）→ 不改指针则将来任何撤销动作又打在错行上。
-- 执行方式：**必须带 utf8mb4 连接**
--   `{ echo "SET NAMES utf8mb4;"; cat 本文件 stepB文件; } | mysql --force --comments --default-character-set=utf8mb4 ...`
--   否则中文 CONCAT 与 utf8mb4_0900_ai_ci 列冲突 → ERROR 1271（V1.4.19 实测坑）。
-- =====================================================================

-- ---------- 步骤 0：快照（回滚依据；只在首次执行时建立） ----------
CREATE TABLE IF NOT EXISTS bak_v1421_item AS
SELECT * FROM bg_item WHERE id IN (97958, 97954, 97957, 98490, 98526);

-- ---------- 步骤 0b：派生映射表（bill → 目标行，X 由账本自身算出） ----------
CREATE TABLE IF NOT EXISTS bak_v1421_map AS
SELECT x.biz_id, x.tenant_id, x.biz_no, x.biz_type, x.oper_user_id, m.target_item_id, x.amount
FROM (
  SELECT o.biz_id, MIN(o.tenant_id) AS tenant_id, MIN(o.biz_no) AS biz_no, MIN(o.biz_type) AS biz_type,
         MIN(o.oper_user_id) AS oper_user_id,
         COALESCE(SUM(CASE WHEN o.oper_type='WRITE_OFF' THEN -o.amount ELSE 0 END), 0) AS amount
  FROM bg_occupy o
  WHERE o.budget_item_id = 97958 AND o.biz_type <> 'INIT_CARRY'
    AND (o.remark IS NULL OR o.remark NOT LIKE 'V1.4.21%')
    AND o.biz_id IN (5818,5819,5825,5829,5837,5838,5854,5859,5861,5867)
  GROUP BY o.biz_id
) x
JOIN (SELECT 5818 AS biz_id, 97954 AS target_item_id UNION ALL
      SELECT 5829, 97954 UNION ALL SELECT 5854, 97954 UNION ALL SELECT 5859, 97954 UNION ALL SELECT 5867, 97954 UNION ALL
      SELECT 5819, 97957 UNION ALL SELECT 5837, 97957 UNION ALL SELECT 5838, 97957 UNION ALL
      SELECT 5825, 98490 UNION ALL
      SELECT 5861, 98526) m ON m.biz_id = x.biz_id;

-- ---------- 步骤 1：环节①②——错误行 97958 的占用冲销 + 核销反向 ----------
INSERT INTO bg_occupy
    (tenant_id, budget_item_id, oper_type, amount, biz_type, biz_id, biz_no, biz_line_id, oper_user_id, remark, create_time)
SELECT p.tenant_id, 97958, 'OCCUPY', -p.amount, p.biz_type, p.biz_id, p.biz_no, NULL, p.oper_user_id,
       CONCAT('V1.4.21 已用重记-占用冲销：单据 ', p.biz_no, ' 的 ', p.amount,
              ' 本行不再承接（明细费用项非 330 职工福利），与同单核销反向腿成对、净额 0；改记 ',
              p.target_item_id, '；非业务撤销'),
       NOW()
FROM bak_v1421_map p
WHERE NOT EXISTS (SELECT 1 FROM bg_occupy x WHERE x.budget_item_id = 97958 AND x.oper_type = 'OCCUPY'
                    AND x.biz_id = p.biz_id AND x.remark LIKE 'V1.4.21%');

INSERT INTO bg_occupy
    (tenant_id, budget_item_id, oper_type, amount, biz_type, biz_id, biz_no, biz_line_id, oper_user_id, remark, create_time)
SELECT p.tenant_id, 97958, 'WRITE_OFF', p.amount, p.biz_type, p.biz_id, p.biz_no, NULL, p.oper_user_id,
       CONCAT('V1.4.21 已用重记-核销反向：冲销本行原 WRITE_OFF -', p.amount,
              '（该核销属费用项 ', p.target_item_id, ' 的预算行），与占用冲销腿成对、净额 0'),
       NOW()
FROM bak_v1421_map p
WHERE NOT EXISTS (SELECT 1 FROM bg_occupy x WHERE x.budget_item_id = 97958 AND x.oper_type = 'WRITE_OFF'
                    AND x.biz_id = p.biz_id AND x.amount > 0 AND x.remark LIKE 'V1.4.21%');

-- ---------- 步骤 2：环节③④——目标行的占用重做 + 核销重做 ----------
INSERT INTO bg_occupy
    (tenant_id, budget_item_id, oper_type, amount, biz_type, biz_id, biz_no, biz_line_id, oper_user_id, remark, create_time)
SELECT p.tenant_id, p.target_item_id, 'OCCUPY', p.amount, p.biz_type, p.biz_id, p.biz_no, NULL, p.oper_user_id,
       CONCAT('V1.4.21 已用重记-占用重做：单据 ', p.biz_no, ' 按解析链(公司1/dept1001/2026/明细费用项)应归本行 ',
              p.amount, '，与 97958 的占用冲销成对；非本期新增业务'),
       NOW()
FROM bak_v1421_map p
WHERE NOT EXISTS (SELECT 1 FROM bg_occupy x WHERE x.budget_item_id = p.target_item_id AND x.oper_type = 'OCCUPY'
                    AND x.biz_id = p.biz_id AND x.remark LIKE 'V1.4.21%');

INSERT INTO bg_occupy
    (tenant_id, budget_item_id, oper_type, amount, biz_type, biz_id, biz_no, biz_line_id, oper_user_id, remark, create_time)
SELECT p.tenant_id, p.target_item_id, 'WRITE_OFF', -p.amount, p.biz_type, p.biz_id, p.biz_no, NULL, p.oper_user_id,
       CONCAT('V1.4.21 已用重记-核销重做：本行承接单据 ', p.biz_no, ' 已核销的 ', p.amount,
              '，与占用重做腿成对、净额 0；原误记 97958(职工福利)'),
       NOW()
FROM bak_v1421_map p
WHERE NOT EXISTS (SELECT 1 FROM bg_occupy x WHERE x.budget_item_id = p.target_item_id AND x.oper_type = 'WRITE_OFF'
                    AND x.biz_id = p.biz_id AND x.amount < 0 AND x.remark LIKE 'V1.4.21%');

-- ---------- 步骤 3：占用派生列按账本重算（I1；四环节两两净额 0，本步应当"不变"） ----------
UPDATE bg_item i
SET i.occupy_amount = (SELECT COALESCE(SUM(o.amount), 0) FROM bg_occupy o WHERE o.budget_item_id = i.id)
WHERE i.id IN (97958, 97954, 97957, 98490, 98526);

-- ---------- 步骤 4：已用派生列 = 快照原值 + 映射差额（绝对赋值，去重） ----------
UPDATE bg_item i
JOIN (SELECT r.row_id AS id, r.delta AS delta
      FROM (SELECT 97958 AS row_id, -SUM(amount) AS delta FROM bak_v1421_map
            UNION ALL
            SELECT target_item_id, SUM(amount) FROM bak_v1421_map GROUP BY target_item_id) r) d ON d.id = i.id
JOIN bak_v1421_item s ON s.id = i.id
SET i.used_amount = s.used_amount + d.delta;

-- =====================================================================
-- 验收
-- =====================================================================
-- A0 期望 10 行、Σ amount = 691,513.15（映射与账本自洽，否则后续全不成立）
SELECT COUNT(*) AS a0_rows, COALESCE(SUM(amount),0) AS a0_total FROM bak_v1421_map;

-- A1 期望 10 行、每单在 97958 的净额 0.00 且本单在 97958 的 Σ|核销| 净值 0（已用不再由本行背）
SELECT o.biz_id, MIN(o.biz_no) AS biz_no, SUM(o.amount) AS a1_net_on_97958,
       SUM(CASE WHEN o.oper_type='WRITE_OFF' THEN o.amount ELSE 0 END) AS a1_wo_net_on_97958
FROM bg_occupy o
WHERE o.budget_item_id = 97958 AND o.biz_type <> 'INIT_CARRY'
  AND o.biz_id IN (SELECT biz_id FROM bak_v1421_map)
GROUP BY o.biz_id ORDER BY o.biz_id;

-- A2 期望：每单在目标行净额 a2_net_on_target = 0.00，且 a2_wo_net_on_target = −a2_expected_used（已用落在本行）
SELECT o.biz_id, MIN(o.biz_no) AS biz_no, o.budget_item_id AS target_row,
       SUM(o.amount) AS a2_net_on_target,
       SUM(CASE WHEN o.oper_type='WRITE_OFF' THEN o.amount ELSE 0 END) AS a2_wo_net_on_target,
       p.amount AS a2_expected_used
FROM bg_occupy o
JOIN bak_v1421_map p ON p.biz_id = o.biz_id AND p.target_item_id = o.budget_item_id
WHERE o.biz_type <> 'INIT_CARRY'
GROUP BY o.biz_id, o.budget_item_id, p.amount ORDER BY a2_expected_used DESC;

-- A3 期望 0 行：全库仍有"净占用落在异费用项行"的单据（祖先链规则，与 §8.11 N3 同判据）
SELECT b.id AS bill_id, b.bill_no,
       (SELECT COALESCE(SUM(o.amount),0) FROM bg_occupy o JOIN bg_item i ON i.id=o.budget_item_id
          WHERE o.biz_id=b.id AND o.biz_type<>'INIT_CARRY'
            AND NOT EXISTS (SELECT 1 FROM exp_bill_line l JOIN md_expense_item le ON le.id=l.item_id
                            WHERE l.bill_id=b.id AND l.del_flag=0
                              AND (i.item_id=l.item_id OR FIND_IN_SET(i.item_id, le.ancestors)))) AS net_wrong_item
FROM exp_bill b
HAVING net_wrong_item <> 0;

-- A4a 期望 **0 行**：这 10 单在错误行 97958 上的**净核销**（=净已用）仍不为 0
--   判据规则＝**净额**，不是笔数：账本只增不改，原 13 笔负核销必须保留，
--     V1.4.21 环节② 只是在同一行加了一笔等值正核销把它们对消（成对流水是审计证据，不是垃圾）。
SELECT o.biz_id, o.biz_no,
       COALESCE(SUM(CASE WHEN o.oper_type='WRITE_OFF' THEN -o.amount ELSE 0 END),0) AS a4a_net_wo_on_97958,
       COALESCE(SUM(o.amount),0) AS a4a_net_all_on_97958
FROM bg_occupy o
WHERE o.budget_item_id = 97958 AND o.biz_type <> 'INIT_CARRY'
  AND o.biz_id IN (5818,5819,5825,5829,5837,5838,5854,5859,5861,5867)
GROUP BY o.biz_id, o.biz_no
HAVING a4a_net_wo_on_97958 <> 0 OR a4a_net_all_on_97958 <> 0;

-- A4b 期望 **0 行**：全库按"单据 × 预算行"净额规则，仍有净已用落在异费用项行
--   （这是 §8.13 清单真正的整改判据；A4c 的老笔数规则判据写在下面，仅留作信息项）
SELECT b.id AS bill_id, b.bill_no, b.status,
       (SELECT COALESCE(SUM(CASE WHEN o.oper_type='WRITE_OFF' THEN -o.amount ELSE 0 END),0)
          FROM bg_occupy o JOIN bg_item i ON i.id=o.budget_item_id
         WHERE o.biz_id=b.id AND o.biz_type<>'INIT_CARRY'
           AND NOT EXISTS (SELECT 1 FROM exp_bill_line l JOIN md_expense_item le ON le.id=l.item_id
                           WHERE l.bill_id=b.id AND l.del_flag=0
                             AND (i.item_id=l.item_id OR FIND_IN_SET(i.item_id, le.ancestors)))) AS a4b_net_wo_wrong_item
FROM exp_bill b
HAVING a4b_net_wo_wrong_item <> 0;

-- A4c **信息项**（旧判据，实测必然非 0，勿据此判失败）：只数"负核销且行不匹配"的流水，
--     不改净额——V1.4.21 后仍会返回 691,513.15 / 10 单 / 13 笔，正是"原账未被抹掉"的正面证据
--     （回滚脚本 R4 用同一个数当"回到整改前"的期望值）。
SELECT COALESCE(SUM(-o.amount),0) AS a4c_used_mis_total, COUNT(DISTINCT o.biz_id) AS a4c_bills, COUNT(*) AS a4c_flows
FROM bg_occupy o JOIN bg_item i ON i.id=o.budget_item_id JOIN exp_bill b ON b.id=o.biz_id AND b.del_flag=0
WHERE o.oper_type='WRITE_OFF' AND o.biz_type<>'INIT_CARRY' AND o.amount < 0
  AND NOT EXISTS (SELECT 1 FROM exp_bill_line l2 JOIN md_expense_item le2 ON le2.id=l2.item_id
                  WHERE l2.bill_id=b.id AND l2.del_flag=0
                    AND (i.item_id=l2.item_id OR FIND_IN_SET(i.item_id, le2.ancestors)));

-- A5 期望 0：不变式 I1 全库差异行
SELECT COUNT(*) AS a5_i1_broken
FROM bg_item i LEFT JOIN (SELECT budget_item_id, SUM(amount) s FROM bg_occupy GROUP BY budget_item_id) f
       ON f.budget_item_id = i.id
WHERE i.del_flag = 0 AND i.occupy_amount <> COALESCE(f.s, 0);

-- A6 终值。期望（occupy 与执行前完全一致；used 按下表搬移；total_amount 一律未增）
--   97954 occupy 16,780.00   used 644,323.05   （56,770.90 + 587,552.15）执行率 56.65%
--   97957 occupy     0.00    used 115,985.30   （97,984.30 +  18,001.00）执行率 10.08%
--   97958 occupy 3,200.00    used 125,592.88   （817,106.03 − 691,513.15）只剩无流水支撑的种子已用
--   98490 occupy     0.00    used  80,000.00   （额度 0 → 负余额，WARN 记录，用户定案保持叶子行）
--   98526 occupy 1,573.47    used   5,960.00   （额度 0，同上）
SELECT id, dim_key, total_amount, adjust_amount, occupy_amount, used_amount,
       total_amount + adjust_amount - occupy_amount - used_amount AS remaining
FROM bg_item WHERE id IN (97958, 97954, 97957, 98490, 98526) ORDER BY id;

-- A7 新增流水条数。期望 40（10 单 × 4 环节），四类各 10 条、每类金额合计 691,513.15
SELECT oper_type, CASE WHEN amount > 0 THEN 'plus' ELSE 'minus' END AS sign_, COUNT(*) AS n, SUM(ABS(amount)) AS amt
FROM bg_occupy WHERE remark LIKE 'V1.4.21%' GROUP BY oper_type, sign_ ORDER BY oper_type, sign_;

-- ---------- 本批**未做**（勿混入本脚本验收） ----------
-- 1) 125,592.88 的"无流水种子已用"（97958 及全库 635 行同类）——归属不可判定，不动。
-- 2) 0 额度行（98490/98526）改挂有额度父级行——用户定案：保持叶子行记录。
-- 3) `bg_occupy.biz_line_id` 0% 填充（缺陷 #32）——历史流水不回补。
-- 4) `resolveOccupyItem` 的 `wantedItemId` 分支不校验费用项（BudgetService.java:303-310）——产品规则，未动码。
--      但它是 stepB B4=17 的复发通道：提交时用行上旧指针当 `wantedItemId`（ExpenseBillService.java:607），
--      同公司即原样返回 → 15 单 / 16 行（DRAFT 14 单 + VOID 1 单，零流水、当前无影响）一旦提交，
--      会在 97958 等错误行再占 34,911.00；另有 1 单指针跨公司，提交时会被 matchItem 重定位（自愈）。
--      详见 §8.14；收紧判据（同公司 **且** 费用项命中祖先链才尊重人工选择）是代码侧后续项，未随本批部署。
