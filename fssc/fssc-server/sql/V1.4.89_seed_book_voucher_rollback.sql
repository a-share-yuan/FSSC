-- =============================================================================
-- V1.4.89 阶段⑤ 凭证入账簿 —— 回滚
-- =============================================================================
-- 只删投影数据，不动 op_voucher / op_voucher_line（投影是只读加工）。
-- 倒序：分录 → 凭证。
DELETE FROM `gl_book_voucher_line`
WHERE `book_voucher_id` IN (
  SELECT `id` FROM `gl_book_voucher` WHERE `remark` LIKE '阶段⑤投影%'
);

DELETE FROM `gl_book_voucher`
WHERE `remark` LIKE '阶段⑤投影%';

-- SME 科目口径改回 ASBE 通用口径（V1.4.88 建账时是 ASBE 口径）
UPDATE `gl_book_subject_map` m
JOIN `gl_accounting_book` b ON b.id = m.book_id
JOIN `gl_standard` s ON s.id = b.standard_id
JOIN `md_subject` ms ON ms.id = m.local_subject_id
SET m.local_subject_name = ms.subject_name,
    m.remark = 'ASBE 通用科目直接对应(V1.4.88)'
WHERE s.standard_code = 'SME' AND m.del_flag = 0;
