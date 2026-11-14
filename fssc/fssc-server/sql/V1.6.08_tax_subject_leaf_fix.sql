-- =====================================================================
-- V1.6.08 补两个末级科目 + 修正税金规则科目
-- 依赖：V1.6.01（tax_provision_rule）、V1.6.05（规则种子）
-- 可重复执行：是（科目按 (tenant_id, subject_code) 判重）
-- 影响表：md_subject / tax_provision_rule
--
-- 背景（E2E 实证）：计提凭证原先把贷方写死成 2221，而 2221 是父科目（下设 4 个明细），
-- 制证时被 PostableSubjectResolver 拒绝——"科目 2221 应交税费 不是末级科目，无法制证"，
-- 结果是计提单永远停在 APPROVED（已审批未入账），一张税金凭证都出不来。
-- 本脚本补两个末级科目，并把规则里的科目指到末级：
--   · 22210104 应交税费-未交增值税        → 申报贷方 / 缴税付款借方
--   · 22210105 应交税费-城建税及附加      → 附加税计提贷方
-- =====================================================================

SET @tenant_id := 1;

-- ---------- 1. 补末级科目 ----------
INSERT INTO md_subject
  (tenant_id, company_id, subject_code, subject_name, parent_id, subject_level, subject_type,
   direction, is_leaf, status, remark, create_by, create_time, del_flag)
SELECT @tenant_id, NULL, '22210104', '应交税费-未交增值税', 1592, 2, 'LIABILITY',
       'CREDIT', 1, 'NORMAL', 'V1.6.08 补：申报/缴税环节的未交税金明细（末级，可制证）', 1, NOW(), 0
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM md_subject
                   WHERE tenant_id = @tenant_id AND subject_code = '22210104') x);

INSERT INTO md_subject
  (tenant_id, company_id, subject_code, subject_name, parent_id, subject_level, subject_type,
   direction, is_leaf, status, remark, create_by, create_time, del_flag)
SELECT @tenant_id, NULL, '22210105', '应交税费-城建税及附加', 1592, 2, 'LIABILITY',
       'CREDIT', 1, 'NORMAL', 'V1.6.08 补：附加税计提贷方明细（末级，可制证）', 1, NOW(), 0
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM md_subject
                   WHERE tenant_id = @tenant_id AND subject_code = '22210105') x);

-- 父科目 2221 因新增明细仍为非末级，保持 is_leaf=0（不改成 1，否则制证会选到父科目）

-- ---------- 2. 修正规则科目到末级 ----------
UPDATE tax_provision_rule
   SET credit_subject_code = '22210101',
       declare_credit_subject_code = '22210104',
       pay_debit_subject_code = '22210104'
 WHERE tenant_id = @tenant_id AND tax_type = 'VAT';

UPDATE tax_provision_rule
   SET credit_subject_code = '22210105',
       declare_credit_subject_code = '22210104',
       pay_debit_subject_code = '22210104'
 WHERE tenant_id = @tenant_id AND tax_type = 'SURCHARGE';

UPDATE tax_provision_rule
   SET credit_subject_code = '22210201',
       declare_credit_subject_code = '22210104',
       pay_debit_subject_code = '22210104'
 WHERE tenant_id = @tenant_id AND tax_type = 'INCOME';

-- 已有规则行若科目为空，按税种兜底（不改用户已配置的非空值）
UPDATE tax_provision_rule
   SET credit_subject_code = CASE tax_type
                                WHEN 'VAT' THEN '22210101'
                                WHEN 'SURCHARGE' THEN '22210105'
                                WHEN 'INCOME' THEN '22210201'
                                ELSE credit_subject_code END
 WHERE tenant_id = @tenant_id AND (credit_subject_code IS NULL OR credit_subject_code = '');

-- ---------- 3. 校验 ----------
SELECT 'V1.6.08' AS script,
       (SELECT COUNT(*) FROM md_subject WHERE tenant_id = @tenant_id
          AND subject_code IN ('22210104','22210105') AND is_leaf = 1) AS new_leaf_subjects,
       (SELECT COUNT(*) FROM tax_provision_rule WHERE tenant_id = @tenant_id
          AND (credit_subject_code = '2221' OR credit_subject_code IS NULL)) AS rules_pointing_to_parent;
-- 期望：new_leaf_subjects=2，rules_pointing_to_parent=0
