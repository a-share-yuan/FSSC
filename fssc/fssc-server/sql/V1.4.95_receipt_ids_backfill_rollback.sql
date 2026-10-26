-- =============================================================================
-- V1.4.95 回滚：还原 rc_receipt.receivable_ids（缺陷 D-H3-03 历史回填的撤销件）
-- =============================================================================
-- 用途：撤销 V1.4.95_receipt_ids_backfill.sql 步骤 1 的那一条 UPDATE。
-- 前提：正向件步骤 0 的留底表 fssc.bak_rc_receipt_ids_v1495 仍在。
--       留底表已删 ⇒ 本件不可用（回填值无法与"本来就有值"的行区分，凭 receivable_ids
--       是否为空反推：正向件只改过当时为空的行，改完就不空了）。
-- 说明：还原的是**原值**（留底里存的就是当时那一瞬的 NULL／空串），不是"删掉列"。
--       代码侧（RcReceiptService.create 的回填环节）不受本件影响——本件只动历史行，
--       回滚之后新单仍会正常回填，两个环节互不遮蔽。
-- =============================================================================

-- 步骤 1R：按留底逐行还原原值（写死 id，留底里有几行就还原几行）
UPDATE fssc.rc_receipt r
  JOIN fssc.bak_rc_receipt_ids_v1495 b ON b.id = r.id
   SET r.receivable_ids = b.receivable_ids;

-- 步骤 2R 复点：应回到留底行数（2026-10-24 实测＝1）
-- SELECT COUNT(*) FROM fssc.rc_receipt r
--   JOIN fssc.bak_rc_receipt_ids_v1495 b ON b.id = r.id
--  WHERE (r.receivable_ids IS NULL AND b.receivable_ids IS NULL)
--     OR r.receivable_ids = b.receivable_ids;

-- 步骤 3R（可选）：确认还原无误后再删留底表；与步骤 1R 同批执行。
-- DROP TABLE IF EXISTS fssc.bak_rc_receipt_ids_v1495;
