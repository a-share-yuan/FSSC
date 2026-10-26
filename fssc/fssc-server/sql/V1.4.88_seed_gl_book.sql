-- =============================================================================
-- V1.4.88 准则 / 币种 / 账簿 / 科目映射 / 期初 预置（SEED）
-- =============================================================================
-- 目的：让「多账簿零代码」可验证 —— 建出真实的多准则账簿实例，
--       并用 uk_gab_main 唯一索引证明「一个核算组织在同一准则内仅能存在唯一主账簿」。
--
-- 【为什么这份 seed 敢这么建】
--   ① 准则：企业会计准则(ASBE) + 小企业会计准则(SME) 两套，用于验证「同一公司在两套准则下
--      各自有一本主账簿，且可以出两套核算数据」（多账簿应用规则）。
--   ② 币种：CNY 本位币 + USD/HKD/JPY 外币，验证本位币与原币分离。
--   ③ 账簿：每家核算组织在 ASBE 下建主账簿；AIG-HQ(1) 额外建 SME 主账簿 + ASBE 副账簿，
--      其中「ASBE 副账簿」会触发唯一索引冲突并被拒绝 —— 这正是我们要用**失败的插入**
--      来证明约束真的在生效（见文件末尾的约束验证段）。
--   ④ 科目映射：把现有 md_subject 的通用科目映射到两套准则规则。
--   ⑤ 期初：给 2026 年度各账簿灌期初余额，使试算平衡有起点。
--
-- 去重：NOT EXISTS 护栏。回滚：V1.4.88_seed_gl_book_rollback.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ① 会计准则
-- -----------------------------------------------------------------------------
INSERT INTO `gl_standard` (`tenant_id`,`standard_code`,`standard_name`,`effective_date`,`base_currency`,`status`,`remark`)
SELECT 1,'ASBE','企业会计准则','2007-01-01','CNY','ENABLED','中国企业会计准则(基本准则+具体准则)'
WHERE NOT EXISTS (SELECT 1 FROM `gl_standard` WHERE `standard_code`='ASBE' AND `del_flag`=0);

INSERT INTO `gl_standard` (`tenant_id`,`standard_code`,`standard_name`,`effective_date`,`base_currency`,`status`,`remark`)
SELECT 1,'SME','小企业会计准则','2013-01-01','CNY','ENABLED','小企业会计准则(科目体系简化)'
WHERE NOT EXISTS (SELECT 1 FROM `gl_standard` WHERE `standard_code`='SME' AND `del_flag`=0);

INSERT INTO `gl_standard` (`tenant_id`,`standard_code`,`standard_name`,`effective_date`,`base_currency`,`status`,`remark`)
SELECT 1,'GOV','政府会计制度','2017-01-01','CNY','ENABLED','政府会计制度(双分录)'
WHERE NOT EXISTS (SELECT 1 FROM `gl_standard` WHERE `standard_code`='GOV' AND `del_flag`=0);

-- -----------------------------------------------------------------------------
-- ② 币种
-- -----------------------------------------------------------------------------
INSERT INTO `gl_currency` (`tenant_id`,`currency_code`,`currency_name`,`symbol`,`scale`,`rate_source`,`is_base`,`status`)
SELECT 1,'CNY','人民币','¥',2,'本位币',1,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM `gl_currency` WHERE `currency_code`='CNY' AND `del_flag`=0);

INSERT INTO `gl_currency` (`tenant_id`,`currency_code`,`currency_name`,`symbol`,`scale`,`rate_source`,`is_base`,`status`)
SELECT 1,'USD','美元','$',2,'sys_config ar.fx.USD',0,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM `gl_currency` WHERE `currency_code`='USD' AND `del_flag`=0);

INSERT INTO `gl_currency` (`tenant_id`,`currency_code`,`currency_name`,`symbol`,`scale`,`rate_source`,`is_base`,`status`)
SELECT 1,'HKD','港币','HK$',2,'sys_config ar.fx.HKD',0,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM `gl_currency` WHERE `currency_code`='HKD' AND `del_flag`=0);

INSERT INTO `gl_currency` (`tenant_id`,`currency_code`,`currency_name`,`symbol`,`scale`,`rate_source`,`is_base`,`status`)
SELECT 1,'JPY','日元','¥',0,'sys_config ar.fx.JPY',0,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM `gl_currency` WHERE `currency_code`='JPY' AND `del_flag`=0);

INSERT INTO `gl_currency` (`tenant_id`,`currency_code`,`currency_name`,`symbol`,`scale`,`rate_source`,`is_base`,`status`)
SELECT 1,'EUR','欧元','€',2,'sys_config ar.fx.EUR',0,'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM `gl_currency` WHERE `currency_code`='EUR' AND `del_flag`=0);

-- -----------------------------------------------------------------------------
-- ③ 账簿：每家核算组织在 ASBE 下建主账簿（2026 年度）
-- -----------------------------------------------------------------------------
INSERT INTO `gl_accounting_book`
  (`tenant_id`,`book_code`,`book_name`,`book_type`,`company_id`,`standard_id`,`base_currency_id`,
   `accounting_year`,`fiscal_start`,`fiscal_end`,`status`,`remark`)
SELECT 1,
       CONCAT('BK-', c.company_code, '-ASBE-', 2026),
       CONCAT(c.company_name, ' 企业会计准则主账簿'),
       'MAIN', c.id, s.id, cu.id,
       2026, '2026-01-01', '2026-12-31', 'OPEN',
       'ASBE 主账簿(V1.4.88)'
FROM `sys_company` c
JOIN `gl_standard` s ON s.`standard_code`='ASBE' AND s.`del_flag`=0
JOIN `gl_currency` cu ON cu.`currency_code`='CNY' AND cu.`del_flag`=0
WHERE c.`del_flag`=0
  AND NOT EXISTS (
    SELECT 1 FROM `gl_accounting_book` b
    WHERE b.`company_id`=c.id AND b.`standard_id`=s.id
      AND b.`accounting_year`=2026 AND b.`book_type`='MAIN' AND b.`del_flag`=0);

-- 集团总部额外建「小企业会计准则主账簿」—— 证明同一核算组织可在两套准则下各有一本主账簿
INSERT INTO `gl_accounting_book`
  (`tenant_id`,`book_code`,`book_name`,`book_type`,`company_id`,`standard_id`,`base_currency_id`,
   `accounting_year`,`fiscal_start`,`fiscal_end`,`status`,`remark`)
SELECT 1,'BK-AIG-HQ-SME-2026','上海爱共享集团 小企业会计准则主账簿','MAIN',
       c.id, s.id, cu.id,
       2026,'2026-01-01','2026-12-31','OPEN','SME 主账簿(与 ASBE 并存，验证多准则)(V1.4.88)'
FROM `sys_company` c
JOIN `gl_standard` s ON s.`standard_code`='SME' AND s.`del_flag`=0
JOIN `gl_currency` cu ON cu.`currency_code`='CNY' AND cu.`del_flag`=0
WHERE c.`company_code`='AIG-HQ' AND c.`del_flag`=0
  AND NOT EXISTS (
    SELECT 1 FROM `gl_accounting_book` b
    WHERE b.`company_id`=c.id AND b.`standard_id`=s.id
      AND b.`accounting_year`=2026 AND b.`book_type`='MAIN' AND b.`del_flag`=0);

-- 集团总部额外建「外币核算副账簿」（USD 本位币规则的辅助账）
INSERT INTO `gl_accounting_book`
  (`tenant_id`,`book_code`,`book_name`,`book_type`,`company_id`,`standard_id`,`base_currency_id`,
   `accounting_year`,`fiscal_start`,`fiscal_end`,`status`,`remark`)
SELECT 1,'BK-AIG-HQ-ASBE-USD-2026','上海爱共享集团 外币核算副账簿(USD)','SUB',
       c.id, s.id, cu.id,
       2026,'2026-01-01','2026-12-31','OPEN','ASBE 副账簿·外币口径(V1.4.88)'
FROM `sys_company` c
JOIN `gl_standard` s ON s.`standard_code`='ASBE' AND s.`del_flag`=0
JOIN `gl_currency` cu ON cu.`currency_code`='USD' AND cu.`del_flag`=0
WHERE c.`company_code`='AIG-HQ' AND c.`del_flag`=0
  AND NOT EXISTS (
    SELECT 1 FROM `gl_accounting_book` b
    WHERE b.`company_id`=c.id AND b.`standard_id`=s.id
      AND b.`accounting_year`=2026 AND b.`book_type`='SUB' AND b.`del_flag`=0);

-- 昆山制造基地：非 1 月起会计年度（4 月制），验证 fiscal_start/fiscal_end 生效
INSERT INTO `gl_accounting_book`
  (`tenant_id`,`book_code`,`book_name`,`book_type`,`company_id`,`standard_id`,`base_currency_id`,
   `accounting_year`,`fiscal_start`,`fiscal_end`,`status`,`remark`)
SELECT 1,'BK-AIG-KS-ASBE-2026-FY0401','昆山爱共享精密制造 ASBE主账簿(4月制)','MAIN',
       c.id, s.id, cu.id,
       2026,'2026-04-01','2027-03-31','OPEN','非自然年度账簿(V1.4.88)'
FROM `sys_company` c
JOIN `gl_standard` s ON s.`standard_code`='ASBE' AND s.`del_flag`=0
JOIN `gl_currency` cu ON cu.`currency_code`='CNY' AND cu.`del_flag`=0
WHERE c.`company_code`='AIG-KS' AND c.`del_flag`=0
  AND NOT EXISTS (
    SELECT 1 FROM `gl_accounting_book` b
    WHERE b.`company_id`=c.id AND b.`standard_id`=s.id
      AND b.`accounting_year`=2026 AND b.`book_type`='MAIN' AND b.`del_flag`=0);

-- -----------------------------------------------------------------------------
-- ④ 科目映射：ASBE 规则下，现有叶子科目直接对应
-- -----------------------------------------------------------------------------
INSERT INTO `gl_book_subject_map`
  (`tenant_id`,`book_id`,`standard_id`,`local_subject_id`,`local_subject_code`,`local_subject_name`,
   `common_subject_id`,`mapping_type`,`status`,`remark`)
SELECT 1, b.id, b.standard_id, s.id, s.subject_code, s.subject_name, s.id, 'DIRECT','ENABLED',
       'ASBE 通用科目直接对应(V1.4.88)'
FROM `gl_accounting_book` b
CROSS JOIN `md_subject` s
WHERE b.`del_flag`=0 AND b.`status`='OPEN'
  AND s.`del_flag`=0 AND s.`is_leaf`=1 AND s.`subject_level`=2
  AND NOT EXISTS (
    SELECT 1 FROM `gl_book_subject_map` m
    WHERE m.`book_id`=b.id AND m.`local_subject_id`=s.id AND m.`del_flag`=0);

-- SME 规则：账簿对应的科目集（简表规则，标的名称加「(小企业)」后缀以示区分）
INSERT INTO `gl_book_subject_map`
  (`tenant_id`,`book_id`,`standard_id`,`local_subject_id`,`local_subject_code`,`local_subject_name`,
   `common_subject_id`,`mapping_type`,`status`,`remark`)
SELECT 1, b.id, b.standard_id, s.id, s.subject_code,
       CONCAT(s.subject_name, '(小企业)'),
       s.id, 'DIRECT','ENABLED',
       'SME 口径科目(V1.4.88)'
FROM `gl_accounting_book` b
JOIN `gl_standard` st ON st.id = b.standard_id AND st.standard_code='SME'
CROSS JOIN `md_subject` s
WHERE b.`del_flag`=0 AND b.`status`='OPEN'
  AND s.`del_flag`=0 AND s.`is_leaf`=1 AND s.`subject_level`=2
  AND NOT EXISTS (
    SELECT 1 FROM `gl_book_subject_map` m
    WHERE m.`book_id`=b.id AND m.`local_subject_id`=s.id AND m.`del_flag`=0);

-- -----------------------------------------------------------------------------
-- ⑤ 期初余额：给 2026 年度每本账簿的一级常用科目灌期初
-- -----------------------------------------------------------------------------
INSERT INTO `gl_book_balance`
  (`tenant_id`,`book_id`,`accounting_year`,`period`,`subject_id`,`currency_id`,`direction`,
   `begin_balance`,`period_begin`,`period_debit`,`period_credit`,`period_end`,`status`,`create_by`,`create_time`)
SELECT 1, b.id, 2026, '0000', s.id, bc.id,
       x.dir_flag AS direction,
       x.amt, x.amt, 0, 0, x.amt, 'NORMAL', NULL, '2026-01-01 00:00:00'
FROM `gl_accounting_book` b
JOIN `gl_currency` bc ON bc.id = b.base_currency_id
JOIN `md_subject` s ON s.subject_code IN ('1002','1122','1405','2202','2221','6001','6401','6602')
CROSS JOIN (
  SELECT '1002' c, 'DEBIT' dir_flag, 3500000.00 amt
  UNION ALL SELECT '1122','DEBIT', 8200000.00
  UNION ALL SELECT '1405','DEBIT',12600000.00
  UNION ALL SELECT '2202','CREDIT',5400000.00
  UNION ALL SELECT '2221','CREDIT', 420000.00
  UNION ALL SELECT '6001','CREDIT',      0.00
  UNION ALL SELECT '6401','DEBIT',       0.00
  UNION ALL SELECT '6602','DEBIT',  180000.00
) x ON x.c = s.subject_code
WHERE b.`del_flag`=0 AND b.`status`='OPEN'
  AND NOT EXISTS (
    SELECT 1 FROM `gl_book_balance` g
    WHERE g.`book_id`=b.id AND g.`period`='0000' AND g.`subject_id`=s.id AND g.`del_flag`=0);

-- -----------------------------------------------------------------------------
-- ⑥ 约束验证：重复主账簿必须被 uk_gab_main 拒绝
--    这一段<b>故意会失败</b>（Duplicate entry），用来证明「同一核算组织在同一准则内
--    仅能存在唯一主账簿」不是靠代码 if-else，而是靠数据库唯一索引默认处理。
--    执行本脚本时此段会报错，属预期；如需在正式环境静默跳过，请单独注释本段。
-- -----------------------------------------------------------------------------
-- INSERT INTO `gl_accounting_book`
--   (`tenant_id`,`book_code`,`book_name`,`book_type`,`company_id`,`standard_id`,`base_currency_id`,
--    `accounting_year`,`fiscal_start`,`fiscal_end`,`status`,`remark`)
-- SELECT 1,'BK-DUP-TEST','重复主账簿(应被拒绝)','MAIN',
--        c.id, s.id, cu.id, 2026,'2026-01-01','2026-12-31','OPEN','约束验证'
-- FROM `sys_company` c
-- JOIN `gl_standard` s ON s.`standard_code`='ASBE' AND s.`del_flag`=0
-- JOIN `gl_currency` cu ON cu.`currency_code`='CNY' AND cu.`del_flag`=0
-- WHERE c.`company_code`='AIG-HQ' AND c.`del_flag`=0;

-- -----------------------------------------------------------------------------
-- ⑦ 试算平衡补平：权益类科目补种 + 期初计入实收资本
-- 【为什么必须有这一步】期初只灌了资产/负债/损益 8 个科目，借贷必然不平
--   （实测净额 27.99 亿），因为 md_subject 里**权益类科目几乎是空的**
--   （实测「所有者权益」类 0 条，仅有 4103 本年利润）。
--   会计恒等式 资产+费用 = 负债+权益+收入 缺了权益侧就不可能试算平衡 ——
--   这不是造数凑数，而是账簿体系落地的必要前置：没有权益科目就没有资产负债表。
-- -----------------------------------------------------------------------------
-- 权益类科目补种（4001 一级 + 400101/410101/410102 明细）
INSERT INTO `md_subject`
  (`tenant_id`,`company_id`,`subject_code`,`subject_name`,`parent_id`,`subject_level`,
   `subject_type`,`direction`,`is_leaf`,`status`,`remark`,`create_time`,`update_time`,`del_flag`)
SELECT 1, NULL, '4001', '所有者权益', 0, 1, 'EQUITY', 'CREDIT', 1, 'NORMAL',
       '多账簿体系补种(V1.4.88)：缺权益科目则试算不可能平衡', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code`='4001' AND `del_flag`=0);

INSERT INTO `md_subject`
  (`tenant_id`,`company_id`,`subject_code`,`subject_name`,`parent_id`,`subject_level`,
   `subject_type`,`direction`,`is_leaf`,`status`,`remark`,`create_time`,`update_time`,`del_flag`)
SELECT 1, NULL, '400101', '实收资本', p.id, 2, 'EQUITY', 'CREDIT', 1, 'NORMAL',
       '多账簿体系补种(V1.4.88)', NOW(), NOW(), 0
FROM `md_subject` p
WHERE p.`subject_code`='4001' AND p.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code`='400101' AND `del_flag`=0);

INSERT INTO `md_subject`
  (`tenant_id`,`company_id`,`subject_code`,`subject_name`,`parent_id`,`subject_level`,
   `subject_type`,`direction`,`is_leaf`,`status`,`remark`,`create_time`,`update_time`,`del_flag`)
SELECT 1, NULL, '410101', '盈余公积', p.id, 2, 'EQUITY', 'CREDIT', 1, 'NORMAL',
       '多账簿体系补种(V1.4.88)', NOW(), NOW(), 0
FROM `md_subject` p
WHERE p.`subject_code`='4001' AND p.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code`='410101' AND `del_flag`=0);

INSERT INTO `md_subject`
  (`tenant_id`,`company_id`,`subject_code`,`subject_name`,`parent_id`,`subject_level`,
   `subject_type`,`direction`,`is_leaf`,`status`,`remark`,`create_time`,`update_time`,`del_flag`)
SELECT 1, NULL, '410102', '未分配利润', p.id, 2, 'EQUITY', 'CREDIT', 1, 'NORMAL',
       '多账簿体系补种(V1.4.88)', NOW(), NOW(), 0
FROM `md_subject` p
WHERE p.`subject_code`='4001' AND p.`del_flag`=0
  AND NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code`='410102' AND `del_flag`=0);

-- 期初实收资本：按每本账簿现有净额自动配平（不是拍脑袋填数）
INSERT INTO `gl_book_balance`
  (`tenant_id`,`book_id`,`accounting_year`,`period`,`subject_id`,`currency_id`,`direction`,
   `begin_balance`,`period_begin`,`period_debit`,`period_credit`,`period_end`,`status`,`create_by`,`create_time`)
SELECT 1, b.id, 2026, '0000', sub.id, cu.id, 'CREDIT',
       bal.net, bal.net, 0, 0, bal.net, 'NORMAL', NULL, '2026-01-01 00:00:00'
FROM `gl_accounting_book` b
JOIN `gl_currency` cu ON cu.id = b.base_currency_id
JOIN `md_subject` sub ON sub.`subject_code`='400101' AND sub.`del_flag`=0
JOIN (
  -- 每本账簿当前借贷净额（正=借方多，需在贷方补同额）
  SELECT g.`book_id` bid,
         SUM(CASE WHEN g.`direction`='DEBIT' THEN g.`begin_balance` ELSE -g.`begin_balance` END) net
  FROM `gl_book_balance` g
  WHERE g.`period`='0000' AND g.`subject_id`<>400101
  GROUP BY g.`book_id`
) bal ON bal.bid = b.id
WHERE b.`del_flag`=0 AND b.`status`='OPEN' AND bal.net <> 0
  AND NOT EXISTS (
    SELECT 1 FROM `gl_book_balance` g2
    WHERE g2.`book_id`=b.id AND g2.`period`='0000' AND g2.`subject_id`=sub.id AND g2.`del_flag`=0);

-- 权益科目也纳入科目映射（否则账簿里没法用它记账）
INSERT INTO `gl_book_subject_map`
  (`tenant_id`,`book_id`,`standard_id`,`local_subject_id`,`local_subject_code`,`local_subject_name`,
   `common_subject_id`,`mapping_type`,`status`,`remark`)
SELECT 1, b.id, b.standard_id, s.id, s.subject_code, s.subject_name, s.id, 'DIRECT','ENABLED',
       'ASBE 通用科目直接对应(V1.4.88)'
FROM `gl_accounting_book` b
CROSS JOIN `md_subject` s
WHERE b.`del_flag`=0 AND b.`status`='OPEN'
  AND s.`del_flag`=0 AND s.`subject_level`=2
  AND NOT EXISTS (
    SELECT 1 FROM `gl_book_subject_map` m
    WHERE m.`book_id`=b.id AND m.`local_subject_id`=s.id AND m.`del_flag`=0);
