-- =====================================================================================
-- V1.4.16_错账清单  业务事实错账取证（**只读**，不写任何表）
-- 日期：2026-09-21    配套：V1.4.16_masterdata_realignment.sql（数据轨 seed）
-- -------------------------------------------------------------------------------------
-- 为什么只出清单不修数：
--   红线「所有数据保留」＝业务事实表（exp_bill / exp_bill_line / op_voucher / op_voucher_line …）
--   **零写入**，包括 REJECTED / DRAFT / VOID 等中间状态。已产生的错账按财会惯例走
--   **调整凭证**（更正分录 / 冲红重复制证），而不是 UPDATE 原单金额 ——
--   UPDATE 会让单据金额与已审批流、影像、发票、付款批次对不上，制造新的不可审计。
--   对标：SAP 凭证冲销一律用 Reverse Document（新凭证 + 冲销标记，原凭证不变，
--
-- 三类错账（2026-09-21 实测条数）：
--   A) 外币行按 1:1 折算      → 26 行（= 外币明细行的 100%，无一行做过真实折算）
--   B) 表头金额 ≠ Σ 明细金额  → 7 张
--   C) 单据回写了凭证号但凭证不存在 → 13 张（op_voucher 全库仅 2 张有效凭证）
--
-- 重复执行安全：全部为 SELECT。
-- =====================================================================================

-- ---------------------------------------------------------------- A) 外币 1:1 折算
-- 现象：exchange_rate 记 1、本位币 amount 直接等于原币 orig_amount，
--       导致 USD 573,452.15 按 CNY 573,452.15 入账（应约 CNY 4.09M 量级，按 2026-08 中间价 7.13）。
-- 影响面：制证（P0-C4 取价逻辑改造后重复制证会得到正确价，但**历史行仍是 1:1**）、
--         已审批单据的真实付款金额、报表本位币合计。
-- 处置建议：APPROVED/PAID 状态的 → 财务按 9 月牌价出调整凭证补差；
--           DRAFT/APPROVING 状态的 → 直接在单据页删除该外币行走正常录单，或由 C4 改造部署后重算。
SELECT b.bill_no, b.bill_type, b.status, b.company_id,
       l.id AS line_id, l.currency_code, l.exchange_rate, l.orig_amount, l.amount AS base_amount,
       ROUND(l.amount - l.orig_amount, 2) AS diff,
       r.rate AS ref_rate_202608, ROUND(l.orig_amount * r.rate, 2) AS expected_base_amount,
       ROUND(l.orig_amount * r.rate - l.amount, 2) AS under_stated_amount
FROM exp_bill_line l
JOIN exp_bill b ON b.id = l.bill_id AND b.del_flag = 0
LEFT JOIN md_exchange_rate r
       ON r.from_currency = l.currency_code AND r.to_currency = 'CNY' AND r.del_flag = 0
      AND r.effect_date = (SELECT MAX(x.effect_date) FROM md_exchange_rate x
                           WHERE x.from_currency = l.currency_code AND x.to_currency = 'CNY'
                             AND x.del_flag = 0 AND x.effect_date <= '2026-09-30')
WHERE l.currency_code NOT IN ('CNY', '') AND l.exchange_rate = 1
ORDER BY b.bill_no, l.id;

-- ---------------------------------------------------------------- B) 表头 ≠ Σ 明细
-- 现象：total_amount 与明细合计不一致（差额 −5,100 ~ +3,111），说明存在"改明细未重算表头"
--       或"改表头未同步明细"的路径（缺陷审计 B 系列 / A 系列同源）。
-- 处置建议：DRAFT/APPROVING → 编辑页重存即触发重算；APPROVED 及之后 → 差额走调整凭证，
--           并核对已生成的付款批次/凭证金额取的是哪一侧。
SELECT b.bill_no, b.bill_type, b.status, b.company_id, b.currency_code,
       b.total_amount, l.lines_sum, ROUND(b.total_amount - l.lines_sum, 2) AS diff,
       b.approved_amount, b.payable_amount, b.paid_amount, b.voucher_no
FROM exp_bill b
JOIN (SELECT bill_id, SUM(amount) lines_sum, COUNT(*) lines_cnt
      FROM exp_bill_line GROUP BY bill_id) l ON l.bill_id = b.id
WHERE b.del_flag = 0 AND b.total_amount <> l.lines_sum
ORDER BY ABS(b.total_amount - l.lines_sum) DESC;

-- ---------------------------------------------------------------- C) 孤儿凭证号
-- 现象：exp_bill.voucher_no / voucher_date 已回写，但 op_voucher 里没有该号（全库 op_voucher
--       仅 2 行、且都不指向这些单据）→ 单据显示"已制证"而账上无凭证，属**制证失败但号已占**。
--       这正是 P0-C5/D2（制证幂等查重 + 唯一键 V1.4.15）要防的形态；历史 13 张需重复制证。
-- 处置建议：部署 P0-C5 代码后由「补制证」入口重跑（V1.4.15 唯一键会拒绝重复活动凭证）；
--           凭证号如需保留原号，需在月结前由财务确认，否则新制凭证会取新号 → 原号作废留痕。
SELECT b.bill_no, b.bill_type, b.status, b.company_id, b.voucher_no, b.voucher_date,
       b.total_amount, b.approved_pay_amount, b.paid_amount
FROM exp_bill b
WHERE b.del_flag = 0 AND b.voucher_no IS NOT NULL AND b.voucher_no <> ''
  AND NOT EXISTS (SELECT 1 FROM op_voucher v WHERE v.voucher_no = b.voucher_no AND v.del_flag = 0)
ORDER BY b.voucher_no;

-- ---------------------------------------------------------------- D) 反向核对（应为 0，非 0 即清单口径本身有偏差）
SELECT 'A_fx_onetoone_lines' k, COUNT(*) v, 26 expect FROM exp_bill_line l JOIN exp_bill b ON b.id=l.bill_id
  WHERE b.del_flag=0 AND l.currency_code NOT IN ('CNY','') AND l.exchange_rate = 1
UNION ALL SELECT 'B_header_ne_sum', COUNT(*), 7 FROM exp_bill b JOIN (SELECT bill_id, SUM(amount) s FROM exp_bill_line GROUP BY bill_id) l ON l.bill_id=b.id
  WHERE b.del_flag=0 AND b.total_amount <> l.s
UNION ALL SELECT 'C_orphan_voucher_no', COUNT(*), 13 FROM exp_bill b
  WHERE b.del_flag=0 AND b.voucher_no IS NOT NULL AND b.voucher_no <> ''
    AND NOT EXISTS (SELECT 1 FROM op_voucher v WHERE v.voucher_no=b.voucher_no AND v.del_flag=0)
UNION ALL SELECT 'D_op_voucher_total', COUNT(*), 2 FROM op_voucher WHERE del_flag=0;
