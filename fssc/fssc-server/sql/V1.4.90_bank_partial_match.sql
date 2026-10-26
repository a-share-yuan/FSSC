-- =============================================================================
-- V1.4.90 银企对账：部分勾对金额写入数据库 + 银行余额规则修正（D-G5-04 / D-G5-06）
-- =============================================================================
-- 目的：修两个实测坐实的「调节表金额算错」缺陷。
--
-- 【D-G5-04 认领不回写收款单 ⇒ 未达项虚增】
--   实测：收款单 SK202610201235521 金额 146,900，其中 65,540 已与银行流水
--   BS20261020001 认领勾对（bank_reconcile.match_status=CLAIMED、流水 MATCHED），
--   但 rc_receipt.bank_matched 仍为 0 ⇒ 调节表 ENT_IN 查询（WHERE bank_matched=0）
--   把整张 146,900 列为「企业已收银行未收」⇒ 未达项虚增 65,540。
--
-- 【关键约束：不能只用布尔位】
--   本例是「部分勾对」：146,900 只认领了 65,540。
--   若简单把 bank_matched 置 1 ⇒ 未认领的 81,360 会被误标为已勾对，
--   反而「漏掉」真实未达 ⇒ 由「虚增 65,540」变成「漏计 81,360」，更糟。
--   ⇒ 必须按「已勾对金额」而非「是否勾对」来算未达：
--       未达额 = rc_receipt.amount − bank_matched_amount
--   故本件新增 rc_receipt.bank_matched_amount DECIMAL(20,2)，并保留
--   bank_matched 作为「是否全额勾对」的派生布尔位（向后兼容既有读方）。
--
-- 【D-G5-06 银行余额规则：可空时不得回落为 0】
--   computeSheet 原写法：
--       bankBalance = stmts.isEmpty() ? account.getBalance() : nz(stmts.get(0).getBalance());
--   只要当月有流水就一律取流水 balance，**不再回落到账户维护余额**；
--   而银行流水导入的 balance 是可选字段（可为 NULL）⇒ nz(NULL)=0
--   ⇒ 调节表「银行余额」=0，实测调节差额 −2,464,354（账户维护余额 2,491,474）。
--   ⇒ 本件不改 Java（该处改为「取非空的最后一条流水 balance，全空才回落账户余额」，
--      属代码改动，见代码提交）；本 SQL 只做数据层准备：
--      ① 新增 bank_matched_amount 字段；
--      ② 历史回填：已勾对的收款单按 bank_reconcile 实际勾对金额回填，
--         避免老数据被误判为「全额未达」。
--
-- 【零破坏边界】
--   只加列 + 只回填，不删列、不改既有行语义；
--   bank_matched 保留（= bank_matched_amount >= amount 的派生值），
--   既有读方（调节表以外）不受影响。
--
-- 回滚脚本：V1.4.90_bank_partial_match_rollback.sql
-- =============================================================================

-- --------------------------------------------------------------------------
-- §1 新增「已勾对金额」列（部分勾对的核心）
-- --------------------------------------------------------------------------
SET @COL_EXISTS := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'fssc' AND TABLE_NAME = 'rc_receipt' AND COLUMN_NAME = 'bank_matched_amount'
);
SET @DDL := IF(@COL_EXISTS = 0,
  'ALTER TABLE fssc.rc_receipt ADD COLUMN bank_matched_amount DECIMAL(20,2) NULL COMMENT ''已与银行流水勾对金额（部分勾对按金额累计，非布尔）'' AFTER bank_matched',
  'SELECT 1');
PREPARE stmt FROM @DDL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- --------------------------------------------------------------------------
-- §2 历史回填：按 bank_reconcile 的实际勾对金额回填 bank_matched_amount
-- --------------------------------------------------------------------------
-- 认领环节：bank_reconcile.payment_no 存的是「应收单号」（rc_receivable.bill_no），
-- 而 rc_receipt.receivable_ids 存的是「应收单 id 列表」（逗号分隔），
-- 两者要经 rc_receivable 才能对上。
-- 这里按「流水勾对到的应收单 → 找出核销了该应收单的收款单」来归集。
UPDATE fssc.rc_receipt r
JOIN (
  SELECT rcp.id AS receipt_id,
         COALESCE(SUM(br.amount), 0) AS matched_amt
  FROM fssc.rc_receipt rcp
  JOIN fssc.rc_receivable rv
    ON FIND_IN_SET(CAST(rv.id AS CHAR), rcp.receivable_ids) > 0
  JOIN fssc.bank_reconcile br
    ON br.payment_no = rv.bill_no
   AND br.match_status = 'CLAIMED'
  GROUP BY rcp.id
) t ON t.receipt_id = r.id
SET r.bank_matched_amount = t.matched_amt
WHERE r.bank_matched_amount IS NULL;

-- 未被任何流水勾对的收款单：已勾对金额 = 0
UPDATE fssc.rc_receipt SET bank_matched_amount = 0 WHERE bank_matched_amount IS NULL;

-- 派生布尔位：全额勾对 ⇒ bank_matched=1；部分 ⇒ 仍为 0（保持「非全额即未勾完」语义）
UPDATE fssc.rc_receipt
SET bank_matched = CASE
      WHEN bank_matched_amount IS NOT NULL AND amount IS NOT NULL
       AND bank_matched_amount >= amount THEN 1
      ELSE 0
    END;

-- --------------------------------------------------------------------------
-- §3 自检：回填结果应满足「勾对金额 ≤ 收款金额」，且全额勾对数与布尔位一致
-- --------------------------------------------------------------------------
SELECT '§3 自检：超勾对（应为 0）' AS check_item, COUNT(*) AS bad_cnt
FROM fssc.rc_receipt
WHERE bank_matched_amount IS NOT NULL AND amount IS NOT NULL
  AND bank_matched_amount > amount;

SELECT '§3 自检：全额勾对但布尔位为 0（应为 0）' AS check_item, COUNT(*) AS bad_cnt
FROM fssc.rc_receipt
WHERE bank_matched_amount IS NOT NULL AND amount IS NOT NULL
  AND bank_matched_amount >= amount AND bank_matched <> 1;

SELECT '§3 自检：部分勾对金额分布' AS check_item,
       COUNT(*) AS receipt_cnt,
       COALESCE(SUM(bank_matched_amount), 0) AS total_matched,
       COALESCE(SUM(amount), 0) AS total_amount
FROM fssc.rc_receipt;
