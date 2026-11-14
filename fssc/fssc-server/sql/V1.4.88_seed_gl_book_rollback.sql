-- =============================================================================
-- V1.4.88 回滚：准则 / 币种 / 账簿 / 科目映射 / 期初 预置
-- =============================================================================
DELETE g FROM `gl_book_balance` g
JOIN `gl_accounting_book` b ON b.id = g.book_id
WHERE b.`remark` LIKE '%V1.4.88%';

DELETE m FROM `gl_book_subject_map` m
JOIN `gl_accounting_book` b ON b.id = m.book_id
WHERE b.`remark` LIKE '%V1.4.88%';

DELETE FROM `gl_accounting_book` WHERE `remark` LIKE '%V1.4.88%';

DELETE FROM `gl_standard`
WHERE `standard_code` IN ('ASBE','SME','GOV') AND `remark` IS NOT NULL;

DELETE FROM `gl_currency`
WHERE `currency_code` IN ('CNY','USD','HKD','JPY','EUR') AND `rate_source` IS NOT NULL;

-- 权益科目补种（V1.4.88 第七段）
DELETE FROM `gl_book_balance`
WHERE `subject_id` IN (SELECT id FROM `md_subject` WHERE `subject_code` = '400101' AND `remark` LIKE '%V1.4.88%');

DELETE FROM `gl_book_subject_map` WHERE `remark` LIKE '%ASBE 通用科目直接对应(V1.4.88)%';

DELETE FROM `md_subject`
WHERE `subject_code` IN ('400101','410101','410102')
  AND `remark` LIKE '%多账簿体系补种(V1.4.88)%';

DELETE FROM `md_subject`
WHERE `subject_code` = '4001'
  AND `remark` LIKE '%多账簿体系补种%';
