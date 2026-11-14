-- =====================================================================
-- V1.6.06 平台数据面清理 + 进项认证台账退役为视图
-- 依赖：V1.6.01
-- 可重复执行：是
-- 影响：pf_tenant_package（删孤儿行）/ 新建视图 v_tax_input_certify
-- =====================================================================

-- ---------- 1. 孤儿行取证（先看再删） ----------
SELECT 'ORPHAN' AS phase, tp.id, tp.ref_tenant_id, t.id AS matched_tenant
  FROM pf_tenant_package tp
  LEFT JOIN pf_tenant t ON t.id = tp.ref_tenant_id
 WHERE t.id IS NULL;

-- ---------- 2. 删孤儿行（ref_tenant_id 在 pf_tenant 中不存在） ----------
-- 这些行来自"只校单号唯一、不校租户存在"的历史写入（服务侧已在 V1.6.06 对应版本补校验）
DELETE tp FROM pf_tenant_package tp
  LEFT JOIN pf_tenant t ON t.id = tp.ref_tenant_id
 WHERE t.id IS NULL;

-- 同样口径检查 pf_order / pf_bill（现盘 0 行，先兜住）
DELETE o FROM pf_order o
  LEFT JOIN pf_tenant t ON t.id = o.ref_tenant_id
 WHERE o.ref_tenant_id IS NOT NULL AND t.id IS NULL;

DELETE b FROM pf_bill b
  LEFT JOIN pf_tenant t ON t.id = b.ref_tenant_id
 WHERE b.ref_tenant_id IS NOT NULL AND t.id IS NULL;

-- ---------- 3. 进项认证台账退役为视图 ----------
-- 独立台账表 tax_input_certify 自此只读（历史行仍可查）；认证的真相在 inv_invoice：
--   certify_status（NOT_CERTIFIED/CERTIFIED/REVERSED）、certify_period（税期）、
--   deduct_amount（可抵扣额）、certify_date、reverse_date。
-- 本视图把发票池投影成台账形状，报表/外部对账可直接查，不必再各写一份 SQL。
CREATE OR REPLACE VIEW `v_tax_input_certify` AS
SELECT
  i.id                                    AS id,
  i.tenant_id                             AS tenant_id,
  i.company_id                            AS company_id,
  i.id                                    AS invoice_id,
  i.invoice_no                            AS invoice_no,
  i.invoice_code                          AS invoice_code,
  i.invoice_kind                          AS invoice_type,
  i.seller_name                           AS supplier_name,
  i.seller_tax_no                         AS supplier_tax_no,
  i.invoice_date                          AS invoice_date,
  i.net_amount                            AS invoice_amount,
  i.tax_amount                            AS tax_amount,
  i.amount                                AS total_amount,
  CASE i.certify_status
    WHEN 'CERTIFIED' THEN 'CERTIFIED'
    WHEN 'REVERSED'  THEN 'TRANSFERRED'
    ELSE 'PENDING'
  END                                     AS certify_status,
  DATE(i.certify_date)                    AS certify_date,
  i.certify_period                        AS certify_period,
  CASE WHEN i.certify_status = 'CERTIFIED' THEN i.certify_period ELSE NULL END AS deduct_period,
  i.deduct_amount                         AS deduct_amount,
  i.reverse_date                          AS reverse_date
FROM inv_invoice i
WHERE i.direction = 'INPUT' AND i.del_flag = 0;

SELECT 'V1.6.06' AS script,
       (SELECT COUNT(*) FROM information_schema.VIEWS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'v_tax_input_certify') AS view_created;
-- 期望：view_created = 1
