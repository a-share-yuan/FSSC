-- ================================================================
-- V1.4.95  历史修复：rc_receipt.receivable_ids 按明细行回填（缺陷 D-H3-03）
-- ----------------------------------------------------------------
-- 现象（2026-10-24 H3 真跑实测）：rc_receipt 共 1 行，receivable_ids 为 NULL，
--       而 rc_receipt_line 已有明细行 ⇒ 单头冗余列与明细不同源。
-- 影响：银企对账（BankReconcileService 的银行流水↔应收勾稽）与 LinkU 开放查询
--       （LinkuOpenQueryMapper 以 FIND_IN_SET(rr.id, r.receivable_ids) 反推公司）
--       仍读该列，NULL 行的这两条链恒空。
-- 代码侧已修：RcReceiptService.create() 在核销写入数据库后按明细顺序回填该列，
--       本脚本只补代码修复之前产生的历史行（去重：谓词限定"当前为空"的行）。
--
--  步骤 0 的留底表是回滚件的唯一前提：留底表不在，V1.4.95_receipt_ids_backfill_rollback.sql 就不可用。
-- ================================================================

-- 步骤 0：留底（把将被改写的那批行的原值整份存下来，与 V1.4.91 同一套路）
DROP TABLE IF EXISTS fssc.bak_rc_receipt_ids_v1495;
CREATE TABLE fssc.bak_rc_receipt_ids_v1495 AS
SELECT id, bill_no, receivable_ids, NOW() AS bak_time
  FROM fssc.rc_receipt
 WHERE receivable_ids IS NULL OR receivable_ids = '';

-- 步骤 0 复点：留底行数应＝"有明细却没 ids"的行数（2026-10-24 实测＝1）
-- SELECT COUNT(*) FROM fssc.bak_rc_receipt_ids_v1495;

-- 步骤 1：按明细顺序回填
UPDATE fssc.rc_receipt r
  JOIN (
        SELECT receipt_id, GROUP_CONCAT(receivable_id ORDER BY min_line_id) AS ids
          FROM (SELECT receipt_id, receivable_id, MIN(id) AS min_line_id
                  FROM fssc.rc_receipt_line
                 GROUP BY receipt_id, receivable_id) d
         GROUP BY receipt_id
       ) x ON x.receipt_id = r.id
   SET r.receivable_ids = x.ids
 WHERE r.receivable_ids IS NULL OR r.receivable_ids = '';

-- 步骤 2 复点（应为 0：不再有"有明细却没 ids"的收款单）：
-- SELECT COUNT(*) FROM fssc.rc_receipt r
--   JOIN fssc.rc_receipt_line l ON l.receipt_id=r.id
--  WHERE r.receivable_ids IS NULL OR r.receivable_ids='';

-- 回滚：见 V1.4.95_receipt_ids_backfill_rollback.sql（按留底表逐行还原原值，写死 id）。
