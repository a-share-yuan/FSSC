-- =====================================================================
-- V1.4.19  C1 跨主体跨年度占用冲销重占（4.9 数据止血清单第 1 项）
-- ---------------------------------------------------------------------
-- 事实（09-22 只读勘查，输出 自动化测试/_c1_probe_out.txt）：
--   BX202609210034  单据 company 1 / dept 1001 / 期间 2026 / 明细费用项 333，占用 4,000
--   BX202609210035  同上，占用 5,000
--   两笔都记在 bg_item 98262 = 2025-COMPANY34-DEPT1401-ITEM330（异主体 + 异年度 + 异费用项）
--   病灶：重整前 md_item_budget_map 211 条坍缩指向 4 个 bg_item 主键（V1.4.16 已修，但这两笔是历史产生）
-- 目标行：bg_item 97954 = 2026-COMPANY1-DEPT1001-ITEM333（ACTIVE/WARN，总额 1,137,400，
--   当前占用 980）—— 与运行实例新解析链（部门精确 > 年度就近）一致，即代码部署后同类单据会落到的行。
--
-- 范围：本文件只写 **bg_occupy（流水）+ bg_item（派生列）**，均属本轮授权的主数据/规则表。
--   必须与 `V1.4.19_stepB_exp_bill_line_pointer.sql`（业务表指针改指）**同批执行**：
--   只跑本文件时，审批通过会按 exp_bill_line.budget_item_id=98262 核销，而该单在 98262 的净占用已被我冲成 0
--   → C2 判"无占用可核"整体跳过 → 97954 上这 9,000 永久悬挂为占用、永不转执行；
--   若单据被驳回，还会在 98262 上再叠一笔 −4,000/−5,000 的重复释放（release 无夹紧为已知挂账项）。
-- 去重：所有写入均带 NOT EXISTS 标签守卫（remark 前缀 'V1.4.19'），可重复执行。
-- =====================================================================

-- ---------- 步骤 0：快照（回滚依据） ----------
CREATE TABLE IF NOT EXISTS bak_v1419_item AS
SELECT * FROM bg_item WHERE id IN (98262, 97954);

-- ---------- 步骤 1：错误行按原占用逐笔负记（冲销） ----------
INSERT INTO bg_occupy
    (tenant_id, budget_item_id, oper_type, amount, biz_type, biz_id, biz_no, biz_line_id, oper_user_id, remark, create_time)
SELECT o.tenant_id, 98262, 'RELEASE', -o.amount, o.biz_type, o.biz_id, o.biz_no, o.biz_line_id, 1,
       CONCAT('V1.4.19 C1 跨主体冲销：单据 ', o.biz_no, ' 属公司1/2026 费用项333，原占用误记在本行(公司34/2025/费用项330) ',
              o.amount, '，按预算调整-冲销重占口径负记；非业务驳回'),
       NOW()
FROM bg_occupy o
WHERE o.budget_item_id = 98262 AND o.oper_type = 'OCCUPY' AND o.biz_type <> 'INIT_CARRY'
  AND NOT EXISTS (SELECT 1 FROM bg_occupy x
                  WHERE x.budget_item_id = 98262 AND x.oper_type = 'RELEASE'
                    AND x.biz_id = o.biz_id AND x.biz_type = o.biz_type AND x.remark LIKE 'V1.4.19%');

-- ---------- 步骤 2：正确行按单据逐笔重占 ----------
INSERT INTO bg_occupy
    (tenant_id, budget_item_id, oper_type, amount, biz_type, biz_id, biz_no, biz_line_id, oper_user_id, remark, create_time)
SELECT o.tenant_id, 97954, 'OCCUPY', o.amount, o.biz_type, o.biz_id, o.biz_no, o.biz_line_id, 1,
       CONCAT('V1.4.19 C1 跨主体重占：单据 ', o.biz_no, ' 按解析链(公司1/dept1001/2026/费用项333)改占本行 ',
              o.amount, '，与步骤 1 的冲销成对；非本期新增业务'),
       NOW()
FROM bg_occupy o
WHERE o.budget_item_id = 98262 AND o.oper_type = 'OCCUPY' AND o.biz_type <> 'INIT_CARRY'
  AND NOT EXISTS (SELECT 1 FROM bg_occupy x
                  WHERE x.budget_item_id = 97954 AND x.oper_type = 'OCCUPY'
                    AND x.biz_id = o.biz_id AND x.biz_type = o.biz_type AND x.remark LIKE 'V1.4.19%');

-- ---------- 步骤 3：派生列按账本重算（维持不变式 I1：occupy_amount = Σ 流水） ----------
--   只动本次涉及的两行，不用全表重算，避免覆盖并发会话此刻正在写入的其它行。
UPDATE bg_item i
SET i.occupy_amount = (SELECT COALESCE(SUM(o.amount), 0) FROM bg_occupy o WHERE o.budget_item_id = i.id)
WHERE i.id IN (98262, 97954);

-- ---------- 验收（期望值见注释；与 4.9 表第 1 项的验收规则一致） ----------
-- 执行方式：`{ echo "SET NAMES utf8mb4;"; cat 本文件 stepB文件; } | mysql --comments`
--   不带 SET NAMES 时客户端按 gbk 连接，步骤 1 的中文 CONCAT 会与 utf8mb4_0900_ai_ci 列冲突：
--   ERROR 1271 Illegal mix of collations for operation 'concat'（2026-09-22 实测）。
-- M1 跨主体/跨年度错配流水。**期望 4 行，不是 0 行**（首版注释写"期望 0"是错的，已按实测更正）：
--   账本只增不改，原始 2 条 OCCUPY(1293/1294) 必须保留作审计轨迹，加上新增 2 条 RELEASE(1789/1790) = 4。
--   → M1 只数流水条数，无法区分"成对冲销"与"真残留"，因此它**不是**判定项；判定项是 M2（净额）。
SELECT COUNT(*) AS m1_mismatch_flows
FROM bg_occupy o
JOIN bg_item i ON i.id = o.budget_item_id
JOIN exp_bill b ON b.id = o.biz_id
WHERE o.biz_type <> 'INIT_CARRY' AND (i.company_id <> b.company_id OR i.budget_year <> b.period_year);

-- M2 错误行 98262 上这两单的净占用（期望 0.00）
SELECT COALESCE(SUM(o.amount), 0) AS m2_wrong_row_net
FROM bg_occupy o WHERE o.budget_item_id = 98262 AND o.biz_type <> 'INIT_CARRY';

-- M3 正确行 97954 上这两单的净占用。**期望 9,980.00，不是 9,000.00**（首版注释算错了：
--   biz_no LIKE 'BX20260921003%' 同时命中 BX202609210038(单据 5880)，它在本行另有净占用 980
--   → 9,000 重占 + 980 既有 = 9,980。本单两笔的精确净额另测（见 8.10 验收表）＝ 4,000 + 5,000。
SELECT COALESCE(SUM(o.amount), 0) AS m3_right_row_net
FROM bg_occupy o WHERE o.budget_item_id = 97954 AND o.biz_type <> 'INIT_CARRY' AND o.biz_no LIKE 'BX20260921003%';

-- M4 不变式 I1 差异行（期望 0）
SELECT COUNT(*) AS m4_i1_broken
FROM bg_item i
JOIN (SELECT id FROM bg_item WHERE id IN (98262, 97954)) t ON t.id = i.id
WHERE i.del_flag = 0
  AND i.occupy_amount <> COALESCE((SELECT SUM(o.amount) FROM bg_occupy o WHERE o.budget_item_id = i.id), 0);

-- M5 两行终值（期望 98262 occupy_amount 0.00；97954 occupy_amount 9,980.00 = 原有 980 + 重占 9,000）
SELECT id, dim_key, total_amount, occupy_amount, used_amount
FROM bg_item WHERE id IN (98262, 97954);
