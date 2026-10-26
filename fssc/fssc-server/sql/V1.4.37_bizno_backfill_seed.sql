-- V1.4.37 批次3 C5 数据侧回填：wf_instance.biz_no 历史 NULL 行补齐（配套代码修复 = WfEngineFacade.BIZ_NO_TABLES）
-- 状态：<b>已备待批</b>——本轮为只读体检＋代码轨，未执行。执行前需业务特批（写的是流程台账表，不是业务单据表）。
-- 依据：代码修复只影响"以后起的流程"；已发生的 24+ 条实例的 biz_no 仍是 NULL，
--       待办/台账/大屏的单号列对这些历史行依旧空白。本包把规则一次性对齐。
-- 去重：WHERE 带 `biz_no IS NULL` ⇒ 二次执行 0 行变更；先建备份表 ⇒ rollback 可逐行还原。
-- 圈定规则（全部来自本轮实测，见 bpmn-repo/drafts/batch3/exec-backfill.log 第三节）：
--   EXP_CLAIM 5/5、EXP_LOAN 8/8、EXP_PAY 2/2、INVOICE_MATCH 9/9、BAD_DEBT_PROV 2/2、
--   BAD_DEBT_CONFIRM 4/4、RECEIVABLE_COLLECT 2/2 的 biz_no 恒 NULL（biz_id 已确证落在对应 id 空间）。
--   （实例数按 wf_instance 行计，同一 biz_id 多次起流会各占一行。）

-- ① 备份被改动的行（只备份将要命中的行，便于精确回滚）
CREATE TABLE IF NOT EXISTS wf_instance_bak_V1437_20260923 AS
SELECT w.id, w.biz_type, w.biz_id, w.biz_no
FROM wf_instance w
WHERE w.biz_no IS NULL
  AND (
       (w.biz_type IN ('EXP_CLAIM','EXP_LOAN','EXP_PAY','INVOICE_MATCH')
          AND EXISTS (SELECT 1 FROM exp_bill b WHERE b.id = w.biz_id))
    OR (w.biz_type IN ('BAD_DEBT_PROV','BAD_DEBT_CONFIRM')
          AND EXISTS (SELECT 1 FROM rc_bad_debt d WHERE d.id = w.biz_id))
    OR (w.biz_type = 'RECEIVABLE_COLLECT'
          AND EXISTS (SELECT 1 FROM rc_receivable r WHERE r.id = w.biz_id))
  );

SELECT '待回填行数' k, COUNT(*) c FROM wf_instance_bak_V1437_20260923 WHERE biz_no IS NULL;

-- ② 报销族四条链 → exp_bill.bill_no
UPDATE wf_instance w JOIN exp_bill b ON b.id = w.biz_id
   SET w.biz_no = b.bill_no
 WHERE w.biz_no IS NULL AND w.biz_type IN ('EXP_CLAIM','EXP_LOAN','EXP_PAY','INVOICE_MATCH');

-- ③ 坏账两条链 → rc_bad_debt.period（该表无单号列，basis_no 实测 4/4 NULL；规则同 AR_RECON）
UPDATE wf_instance w JOIN rc_bad_debt d ON d.id = w.biz_id
   SET w.biz_no = d.period
 WHERE w.biz_no IS NULL AND w.biz_type IN ('BAD_DEBT_PROV','BAD_DEBT_CONFIRM');

-- ④ 催收链 → rc_receivable.bill_no（bizId 是应收单 id，非催收待办 id）
UPDATE wf_instance w JOIN rc_receivable r ON r.id = w.biz_id
   SET w.biz_no = r.bill_no
 WHERE w.biz_no IS NULL AND w.biz_type = 'RECEIVABLE_COLLECT';

-- ⑤ 复核：目标域改后应全部非空（备份表里原本为 NULL 的行数 = 被 UPDATE 的行数）
SELECT w.biz_type, COUNT(*) rows_all, SUM(w.biz_no IS NULL) still_null
FROM wf_instance w
WHERE w.biz_type IN ('EXP_CLAIM','EXP_LOAN','EXP_PAY','INVOICE_MATCH','BAD_DEBT_PROV','BAD_DEBT_CONFIRM','RECEIVABLE_COLLECT')
GROUP BY w.biz_type;
