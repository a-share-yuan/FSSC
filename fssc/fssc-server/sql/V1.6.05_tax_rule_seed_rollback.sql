-- =====================================================================
-- 回滚 V1.6.05（税金计提规则种子）
-- 只删本脚本灌入的三条（按 remark 前缀识别），不误删运营自行维护的规则。
-- =====================================================================
SET @tenant_id := 1;
DELETE FROM tax_provision_rule
 WHERE tenant_id = @tenant_id
   AND remark LIKE '种子：%';
SELECT 'V1.6.05 rollback done' AS result;
