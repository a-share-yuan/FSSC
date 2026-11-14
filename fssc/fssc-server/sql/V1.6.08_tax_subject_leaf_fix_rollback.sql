-- 回滚 V1.6.08
-- 只在没有凭证引用这两个新科目时执行；引用中删科目会让凭证行指向不存在的科目。
SET @tenant_id := 1;
UPDATE tax_provision_rule SET credit_subject_code='2221' WHERE tenant_id=@tenant_id AND tax_type='SURCHARGE';
DELETE FROM md_subject WHERE tenant_id=@tenant_id AND subject_code IN ('22210104','22210105')
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM op_voucher_line
        WHERE subject_code IN ('22210104','22210105')) x);
SELECT 'V1.6.08 rollback done' AS result;
