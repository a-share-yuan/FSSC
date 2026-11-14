-- =====================================================================
-- V1.6.05 税金计提规则种子
-- 依赖：V1.6.01（tax_provision_rule 表）
-- 可重复执行：是（uk_tenant_tax_type 判重，已存在不覆盖）
-- 前置：先跑 V1.6.08 补末级科目（否则 SURCHARGE 的 22210105 不存在）
-- 说明：服务端在表为空时有内置兜底（增值税13%/附加12%/所得税25%），本脚本只是把兜底"落成可见可改的行"，
--       让运营能在页面上改税率与科目，而不是只能改代码。
--       多租户：把 @tenant_id 改成目标租户后重跑一次即可逐租户灌入。
-- =====================================================================

SET @tenant_id := 1;

INSERT INTO tax_provision_rule
  (tenant_id, tax_type, rule_name, base_source, base_amount, tax_rate,
   debit_subject_code, credit_subject_code, declare_credit_subject_code, pay_debit_subject_code,
   period_type, declare_deadline_day, pay_deadline_day, sort, status, remark, create_by, create_time, del_flag)
SELECT t.tenant_id, t.tax_type, t.rule_name, t.base_source, t.base_amount, t.tax_rate,
       t.debit_subject_code, t.credit_subject_code, t.declare_credit_subject_code, t.pay_debit_subject_code,
       t.period_type, t.declare_deadline_day, t.pay_deadline_day, t.sort, t.status, t.remark,
       1, NOW(), 0
FROM (
  SELECT @tenant_id AS tenant_id, 'VAT' AS tax_type, '增值税-月度' AS rule_name,
         'MANUAL' AS base_source, 100000.00 AS base_amount, 13.00 AS tax_rate,
         '6403' AS debit_subject_code, '22210101' AS credit_subject_code,
         '22210104' AS declare_credit_subject_code, '22210104' AS pay_debit_subject_code,
         'MONTHLY' AS period_type, 15 AS declare_deadline_day, 15 AS pay_deadline_day,
         1 AS sort, 'NORMAL' AS status,
         '种子：与改造前写死常量同值；销项税额明细按 22210101' AS remark
  UNION ALL
  SELECT @tenant_id, 'SURCHARGE', '附加税-月度', 'MANUAL', 12000.00, 12.00,
         '6403', '22210105', '22210104', '22210104', 'MONTHLY', 15, 15, 2, 'NORMAL',
         '种子：附加税贷方用 22210105（末级），申报/缴税走 22210104 未交增值税'
  UNION ALL
  SELECT @tenant_id, 'INCOME', '企业所得税-季度', 'MANUAL', 50000.00, 25.00,
         '6801', '22210201', '22210104', '22210104', 'QUARTERLY', 15, 15, 3, 'NORMAL',
         '种子：企业所得税季报，贷方 22210201'
) t
WHERE NOT EXISTS (SELECT 1 FROM tax_provision_rule r
                   WHERE r.tenant_id = t.tenant_id AND r.tax_type = t.tax_type);

SELECT 'V1.6.05' AS script, COUNT(*) AS rule_rows FROM tax_provision_rule WHERE tenant_id = @tenant_id;
-- 期望：rule_rows ≥ 3
