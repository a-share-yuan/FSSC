-- =====================================================================================
-- V1.4.16  基础数据重整 · 规则/主数据层（seed）
-- 日期：2026-09-21    执行人：变更窗口负责人（本脚本由整改会话交付，不自动执行）
-- -------------------------------------------------------------------------------------
-- 参考依据（名词级取证，见审计文档附录 A / 第七章）：
--   预算＝按「组织 / 期间 / 费用类型（科目）/ 项目」多维设定额度，单据提交时校验可用余额，
--   控制方式分「刚性（拦截）/ 柔性（预警放行）」两级。
--   据此本脚本做的不是"补几行数据"，而是把预算控制面按主体×期间真正立起来。
--
-- 范围与红线：
--   只写主数据/规则表：bg_scheme、bg_item、md_item_budget_map、md_period、md_exchange_rate
--   业务事实表（exp_bill / exp_bill_line / op_voucher / rc_* / inv_* …）**零写入**；
--   已产生的错账（外币 1:1、表头≠Σ明细、孤儿凭证号）一律走调整凭证，见 V1.4.16_错账清单.sql。
--
-- 执行顺序硬约束（不可调换）：
--   步骤 1（建 ACTIVE/WARN 方案）必须先于 步骤 3（建 0 额度预算行）。
--   原因：BudgetService.controlTypeOf() 在「方案缺失 / status 不在 ACTIVE,ISSUED / control_type 空」
--   三种情况下**默认处理返回 HARD**（BudgetService:403-416）。若先建 0 额度行，任何占用都算超预算，
--   在 HARD 默认处理下会直接拦死提交。当前 bg_scheme 实有 0 行而 1324 条 bg_item 的 scheme_id 全部悬空，
--   正是这一默认处理在生效 —— 步骤 1 同时把全局控制强度从"隐式 HARD"收敛为"显式 WARN"。
--
-- 去重：全部语句可重复执行（INSERT ... WHERE NOT EXISTS / UPDATE 带标签）。
-- 回滚：V1.4.16_rollback.sql（步骤 0 建快照表，UPDATE 项按快照恢复，INSERT 项按标签删除）。
-- 备份：自动化测试/backup/fssc_masterdata_20260921_1950.sql（mysqldump，最后防线）。
-- =====================================================================================

-- ------------------------------------------------------------------ 0. 快照（供回滚）
CREATE TABLE IF NOT EXISTS bak_v1416_bg_item_scheme AS
SELECT id, scheme_id FROM bg_item;

CREATE TABLE IF NOT EXISTS bak_v1416_item_budget_map AS
SELECT id, item_id, budget_item_id, budget_dim_key, status, remark FROM md_item_budget_map;

-- md_period 没有 remark 列，无法用文字标签识别本次新增行 → 用建档前主键快照作回滚依据
CREATE TABLE IF NOT EXISTS bak_v1416_period_before AS
SELECT id FROM md_period;

START TRANSACTION;

-- ------------------------------------------------------------------ 1. 预算方案建档
-- 每个法人主体 × 每个预算年度一条方案；控制方式一律 WARN（柔性预警），
-- 待额度下达并完成演练后由业务按公司升级为 SOFT/HARD（属经营决策，不由本脚本代决）。
INSERT INTO bg_scheme
    (tenant_id, company_id, scheme_code, scheme_name, budget_year, period_type, dimension,
     control_type, carry_over, status, remark, issue_time, issue_by, create_by, create_time, del_flag)
SELECT c.tenant_id, c.id,
       CONCAT('BUD-', c.id, '-', y.yr),
       CONCAT(c.company_name, ' ', y.yr, '年度费用预算方案'),
       y.yr, 'MONTH', 'COMPANY,DEPT,ITEM',
       'WARN', 0, 'ACTIVE',
       'V1.4.16 重整建档：柔性预警（WARN），额度下达并演练后按公司升级 SOFT/HARD',
       NOW(), 1, 1, NOW(), 0
FROM sys_company c
CROSS JOIN (SELECT 2025 yr UNION ALL SELECT 2026 UNION ALL SELECT 2027) y
WHERE c.del_flag = 0
  AND NOT EXISTS (SELECT 1 FROM bg_scheme s
                  WHERE s.company_id = c.id AND s.budget_year = y.yr AND s.del_flag = 0);

-- 悬空 scheme_id 回填（1324 条 bg_item 现指向不存在的方案 → 触发 HARD 默认处理）
UPDATE bg_item b
JOIN bg_scheme s ON s.company_id = b.company_id AND s.budget_year = b.budget_year AND s.del_flag = 0
SET b.scheme_id = s.id
WHERE b.del_flag = 0 AND b.scheme_id <> s.id;

-- ------------------------------------------------------------------ 3. 预算明细建档（发生面驱动）
-- 只给「确实有 CLAIM/PAY/APPLY 单据发生过」的费用项建主体级年度预算行（budget_period=0 年度行，
-- BudgetService.matchItem 排序：部门精确 > 期间离 0 近 > 年份新，故年度行即默认处理行）。
-- total_amount 给 0 且标注"待下达"：不编额度；WARN 方案下超限只记录放行，不拦业务。
INSERT INTO bg_item
    (tenant_id, scheme_id, company_id, budget_year, budget_period, dept_id, item_id, project_id,
     dim_key, total_amount, adjust_amount, occupy_amount, used_amount, currency_code,
     remark, create_by, create_time, del_flag)
SELECT sc.tenant_id, sc.id, sc.company_id, sc.budget_year, 0, NULL, x.item_id, NULL,
       CONCAT(sc.budget_year, '-COMPANY', sc.company_id, '-ITEM', x.item_id),
       0.00, 0.00, 0.00, 0.00, 'CNY',
       'V1.4.16 重整建档：额度待下达（total=0，WARN 下超限放行留痕）',
       1, NOW(), 0
FROM bg_scheme sc
CROSS JOIN (SELECT DISTINCT l.item_id
            FROM exp_bill_line l
            JOIN exp_bill b ON b.id = l.bill_id AND b.bill_type IN ('CLAIM', 'PAY', 'APPLY')
            JOIN md_expense_item e ON e.id = l.item_id AND e.del_flag = 0) x
WHERE sc.status = 'ACTIVE' AND sc.del_flag = 0 AND sc.budget_year IN (2026, 2027)
  AND NOT EXISTS (SELECT 1 FROM bg_item i
                  WHERE i.item_id = x.item_id AND i.company_id = sc.company_id
                    AND i.budget_year = sc.budget_year AND i.budget_period = 0
                    AND i.dept_id IS NULL AND i.del_flag = 0);

-- ------------------------------------------------------------------ 4. 费用项→预算映射纠正
-- 现状：211 条映射全部坍缩指向 4 条 bg_item 主键（id 19/20/21/22，均属 company 33 / dept 1311）
--   → 任一公司任一费用项的默认处理占用都会记到昆山主体的预算线上。
-- 该表结构上只有单值 budget_item_id，无法表达主体维度（真解是 P1 的组织化映射规则表）。
-- 过渡规则：改指向「同费用项在集团本部(company 1) 的主体级年度行」；无对应行则置 NULL，
--   让 resolveOccupyItem 回退到按单据主体 matchItem —— 单值不再跨主体误占（跨主体它本也会拒绝）。
UPDATE md_item_budget_map m
LEFT JOIN bg_item b ON b.item_id = m.item_id AND b.company_id = 1
     AND b.budget_year = 2026 AND b.budget_period = 0 AND b.dept_id IS NULL AND b.del_flag = 0
SET m.budget_item_id = b.id,
    m.budget_dim_key = b.dim_key,
    m.remark = 'V1.4.16 重整：原坍缩指向跨主体单值，改归集集团本部同费用项年度行（无行则置空，由单据主体解析）'
WHERE m.del_flag = 0;

-- ------------------------------------------------------------------ 5. 会计期间建档
-- 现状仅 5 行（且 PeriodService.assertOpen 对"未建档"是放行 = fail-open）。先补齐建档，
-- 是后续把开关账收紧成 fail-closed（4.6）的前提；否则一收紧就拦死所有过账。
-- 状态一律 NORMAL（非 CLOSED 即可过账）；**已存在的行不动**，尤其 co1 2026-11 的 CLOSED 是演练关账事实。
-- 注：md_period 无 remark 列，本次新增行只能靠步骤 0 的 bak_v1416_period_before 主键快照识别/回滚。
INSERT INTO md_period
    (tenant_id, company_id, period_year, period_month, begin_date, end_date, status,
     create_by, create_time, del_flag)
SELECT c.tenant_id, c.id, y.yr, mo.m,
       STR_TO_DATE(CONCAT(y.yr, '-', LPAD(mo.m, 2, '0'), '-01'), '%Y-%m-%d'),
       LAST_DAY(STR_TO_DATE(CONCAT(y.yr, '-', LPAD(mo.m, 2, '0'), '-01'), '%Y-%m-%d')),
       'NORMAL', 1, NOW(), 0
FROM sys_company c
CROSS JOIN (SELECT 2026 yr UNION ALL SELECT 2027) y
CROSS JOIN (SELECT 1 m UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
            UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
            UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) mo
WHERE c.del_flag = 0
  AND NOT EXISTS (SELECT 1 FROM md_period p
                  WHERE p.company_id = c.id AND p.period_year = y.yr AND p.period_month = mo.m
                    AND p.del_flag = 0);

-- ------------------------------------------------------------------ 6. 汇率续期（演示规则，不编真实牌价）
-- 现状 6 个币种对全部止于 2026-08-31。P0-C4 已改为「取生效日≤业务日的最近一条 + 查无价抛错」，
-- 本步把覆盖区间续到 2027-12，避免演示期间出现"无价可用"。
-- 数值沿用 2026-08 中间价，source 标记 ROLLFORWARD（**非真实牌价**），财务按月导入真实价后即被
-- ExchangeRateService.importMonthly 的 upsert（同 from/to/rate_type/effect_date）覆盖。
INSERT INTO md_exchange_rate
    (tenant_id, from_currency, to_currency, rate_type, rate, effect_date, expire_date,
     source, create_by, create_time, del_flag)
SELECT r.tenant_id, r.from_currency, r.to_currency, r.rate_type, r.rate,
       STR_TO_DATE(CONCAT(y.yr, '-', LPAD(mo.m, 2, '0'), '-01'), '%Y-%m-%d'),
       LAST_DAY(STR_TO_DATE(CONCAT(y.yr, '-', LPAD(mo.m, 2, '0'), '-01'), '%Y-%m-%d')),
       'ROLLFORWARD', 1, NOW(), 0
FROM (SELECT tenant_id, from_currency, to_currency, rate_type, rate
      FROM md_exchange_rate WHERE del_flag = 0 AND effect_date = '2026-08-01') r
CROSS JOIN (SELECT 2026 yr UNION ALL SELECT 2027) y
CROSS JOIN (SELECT 1 m UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
            UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
            UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) mo
WHERE NOT (y.yr = 2026 AND mo.m <= 8)
  AND NOT EXISTS (SELECT 1 FROM md_exchange_rate e
                  WHERE e.from_currency = r.from_currency AND e.to_currency = r.to_currency
                    AND e.rate_type = r.rate_type
                    AND e.effect_date = STR_TO_DATE(CONCAT(y.yr, '-', LPAD(mo.m, 2, '0'), '-01'), '%Y-%m-%d')
                    AND e.del_flag = 0);

COMMIT;

-- =====================================================================================
-- 执行后验收（只读，期望值见每行注释）
-- =====================================================================================
-- A) 方案建档：27 条 ACTIVE/WARN，覆盖 9 主体 × 3 年度        → 期望 27 / 27
-- B) bg_item 悬空 scheme_id：仍指向缺失或未生效方案的行数       → 期望 0
-- C) 发生面 7 个费用项 × 9 主体 的 2026 年度行齐备              → 期望 63
-- D) 映射跨主体坍缩：budget_item_id 指向非 company 1 的行数      → 期望 0
-- E) 期间建档：9 × 24 − 已存在                                  → 期望 ≥ 205
-- F) 汇率覆盖：2026-09-01 起每月每对有价                       → 期望 120 行
-- G) 控制强度实际值：controlTypeOf 默认处理 HARD 的预算行数          → 期望 0
SELECT 'A_scheme' k, COUNT(*) v, 27 expect FROM bg_scheme WHERE status='ACTIVE' AND control_type='WARN' AND del_flag=0
UNION ALL SELECT 'B_dangling_scheme', COUNT(*), 0 FROM bg_item b LEFT JOIN bg_scheme s ON s.id=b.scheme_id AND s.status IN ('ACTIVE','ISSUED') WHERE b.del_flag=0 AND s.id IS NULL
UNION ALL SELECT 'C_new_bg_item_2026', COUNT(*), 63 FROM bg_item WHERE del_flag=0 AND budget_year=2026 AND budget_period=0 AND dept_id IS NULL AND remark LIKE 'V1.4.16 重整建档%'
UNION ALL SELECT 'D_map_cross_subject', COUNT(*), 0 FROM md_item_budget_map m JOIN bg_item b ON b.id=m.budget_item_id WHERE m.del_flag=0 AND b.company_id<>1
UNION ALL SELECT 'E_periods', COUNT(*), 216 FROM md_period WHERE del_flag=0
UNION ALL SELECT 'E2_periods_new', COUNT(*), 211 FROM md_period p LEFT JOIN bak_v1416_period_before k ON k.id=p.id WHERE k.id IS NULL
UNION ALL SELECT 'F_rate_rollfwd', COUNT(*), 96 FROM md_exchange_rate WHERE source='ROLLFORWARD' AND del_flag=0
UNION ALL SELECT 'G_hard_fallback_rows', COUNT(*), 0 FROM bg_item b LEFT JOIN bg_scheme s ON s.id=b.scheme_id WHERE b.del_flag=0 AND (s.id IS NULL OR s.status NOT IN ('ACTIVE','ISSUED') OR s.control_type IS NULL OR s.control_type='');
