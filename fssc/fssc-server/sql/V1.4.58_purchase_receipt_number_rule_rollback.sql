-- ============================================================================
-- V1.4.58_purchase_receipt_number_rule_rollback.sql
-- 回滚对象：V1.4.58_purchase_receipt_number_rule_seed.sql（D-V4R-11 编号规则补播种）
-- 类型：注释态登记件。可执行行＝0。
--
-- 前置判断（回滚前必查，任一不满足则**不得**回滚）：
--   R0.1 已有按该规则取出的收货单号 ⇒ 规则删除后再次取号会重新 400，且历史号失去来源。
--        SELECT COUNT(*) AS used_cnt FROM fssc.po_receipt WHERE receipt_no REGEXP '^GRN[0-9]{8}[0-9]{4}$';
--        期望 0 才可回滚；>0 时保留规则（规则本身无害，删除只会把业务打回 400）。
--   R0.2 留底：SELECT id, tenant_id, rule_code FROM fssc.sys_number_rule WHERE rule_code='PURCHASE_RECEIPT';
--
-- 去重：WHERE 带 rule_code＋tenant_id 精确关联 ⇒ 二次执行 0 行变更。
--  不动 po_receipt（业务数据）； 不动其它规则行。
-- ============================================================================

-- R1 删除播种行（未执行；期望影响行数 1）
-- DELETE FROM fssc.sys_number_rule WHERE tenant_id = 1 AND rule_code = 'PURCHASE_RECEIPT';

-- R2 一并回收取号流水（未执行；该表无历史语义，行由本规则首次取号时才产生）
-- DELETE FROM fssc.sys_number_seq WHERE tenant_id = 1 AND rule_code = 'PURCHASE_RECEIPT';

-- R3 后置复点（期望均 0）
-- SELECT COUNT(*) AS rule_rows FROM fssc.sys_number_rule WHERE rule_code = 'PURCHASE_RECEIPT';
-- SELECT COUNT(*) AS seq_rows  FROM fssc.sys_number_seq  WHERE rule_code = 'PURCHASE_RECEIPT';
