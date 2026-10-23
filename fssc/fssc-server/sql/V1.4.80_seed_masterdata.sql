-- =============================================================================
-- V1.4.80 造数前置补档：法人公司 / 客户主数据 / 会计科目明细（SEED）
-- =============================================================================
-- 目的：阶段⑦「造真实业务数据」的前置。本件只补**主数据底座**（组织、客户、科目），
--       不插任何业务单据。业务单据由 scripts/seed_biz_data.py 生成。
--
-- 【为什么必须先补】造数前实测三处硬缺口（2026-10-18）：
--   缺口① 已闭环：历史上 md_bank_account/md_project/gl_accounting_book 曾以 company_id
--   100/101/102/104 指向历史壳公司（外部法人），造成悬空外键；已在 2026-10-20 将归属
--   迁移至爱共享主体(id=1)并删除历史壳公司。本文件不再补录壳公司法人，造数须直接归属
--   爱共享 9 家(id=1/31~38)。
--   缺口② crm_customer 57 行**全部是 SUPPLIER**，CUSTOMER 类型 = 0
--          ⇒ AR 应收链路（rc_receivable/ar_statement/rc_collect_todo）没有交易对手。
--   缺口③ md_subject 缺应交增值税销项/进项、主营业务成本明细等
--          ⇒ 收入/税金/成本无法按明细科目落凭证，试算平衡做不出真数。
--
-- 【零行为影响边界】
--   · 本件**只 INSERT，不 UPDATE 任何既有行**。sys_company / crm_customer / md_subject
--     的现有行数与内容零改动。
--   · sys_company 用**显式 id**（100/101/102/104）插入，与 md_bank_account.company_id
--     现状逐字对齐 —— 这是「让悬空外键落地」，不是「改既有数据指向」。
--   · 科目只补明细层（subject_level=2），parent 挂到已有一级科目，不新增一级科目。
--
-- 【幂等】每段均以 NOT EXISTS 护栏；重复执行不产生重复行。
-- 回滚脚本：V1.4.80_seed_masterdata_rollback.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 段① 法人公司补档（已移除）
-- -----------------------------------------------------------------------------
-- 企业主体为「爱共享集团」（sys_company id=1/31~38 共 9 家）。历史上为接住
-- md_bank_account/md_project/gl_accounting_book 悬空外键而补录的历史壳公司
-- (id=100/101/102/104) 已在 2026-10-20 清理：从属数据归属迁至爱共享主体(id=1)，
-- 历史壳公司已删除。后续造数一律以爱共享公司 id 为归属，禁止写死 100/101/102/104。

-- 段② 客户主数据补档（12 家 CUSTOMER，AR 应收链路的交易对手）
-- 口径：customer_code 沿用既有 C2xxx 段（C2002/C2003 已在库），从 C2101 起顺延
-- -----------------------------------------------------------------------------
INSERT INTO `crm_customer`
  (`tenant_id`, `create_by`, `create_time`, `del_flag`, `customer_code`, `customer_name`,
   `customer_type`, `contact_name`, `contact_phone`, `address`, `credit_level`, `status`, `remark`)
SELECT 1, NULL, NOW(), 0, t.code, t.name, 'CUSTOMER', t.contact, t.phone, t.addr,
       t.credit, 'ACTIVE', '造数前置补档：AR 应收链路交易对手(V1.4.80)'
FROM (
  SELECT 'C2101' code, '环海重工股份有限公司' name, '王海涛' contact, '0535-8201234' phone, '山东省烟台市芝罘区飞跃路' addr, 'A' credit
  UNION ALL SELECT 'C2102', '恒瑞重工（江苏）有限公司',      '李文博', '025-58889900', '江苏省南京市江宁区',           'A'
  UNION ALL SELECT 'C2103', '蓝海船舶重工集团有限公司',      '张海宁', '0411-8477000',  '辽宁省大连市西岗区',           'B'
  UNION ALL SELECT 'C2104', '东海重工（集团）股份有限公司','刘振华','021-39780000', '上海市浦东新区东方路',         'A'
  UNION ALL SELECT 'C2105', '烟台精密铸造有限公司',  '陈志远', '0535-6666100',  '山东省烟台市福山区',           'B'
  UNION ALL SELECT 'C2106', '青岛海工科技有限公司',      '赵海峰', '0532-8556000',  '山东省青岛市黄岛区',           'B'
  UNION ALL SELECT 'C2107', '蓬莱海洋石油结构制造有限公司','孙国强','0535-5641000',  '山东省蓬莱市南王街道',         'A'
  UNION ALL SELECT 'C2108', '南方船舶机械（广东）有限公司','周建军','0755-27480000', '广东省深圳市宝安区',           'C'
  UNION ALL SELECT 'C2109', '江苏莱茵钢结构有限公司',      '吴建华','0512-66780000', '江苏省太仓市经济开发区',     'B'
  UNION ALL SELECT 'C2110', '现代交通科技有限公司',        '郑思远','021-33680000',  '上海市青浦区外青松公路',       'C'
  UNION ALL SELECT 'C2111', '北京筑联钢结构工程有限公司',      '冯国栋','010-67880000',  '北京市大兴区经济技术开发区',   'C'
  UNION ALL SELECT 'C2112', '广州海洋工程装备有限公司',    '梁晓峰','020-82660000',  '广东省广州市南沙区',           'C'
) t
WHERE NOT EXISTS (
  SELECT 1 FROM `crm_customer` c WHERE c.customer_code = t.code AND c.del_flag = 0
);

-- -----------------------------------------------------------------------------
-- 段③ 会计科目明细补档（明细层，parent 挂既有���级科目）
-- 口径：科目编码/方向/科目类型 参照库内既有一级科目逐字沿用，不自造体系
-- -----------------------------------------------------------------------------
-- ③-1 应收账款明细（parent=1560 应收账款）
INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '112201', '应收账款-国内客户款', 1560, 2, 'ASSET', 'DEBIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '112201' AND `del_flag` = 0);

INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '112202', '应收账款-国外客户款', 1560, 2, 'ASSET', 'DEBIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '112202' AND `del_flag` = 0);

-- ③-2 银行存款明细（parent=967 银行存款）
INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '100201', '银行存款-基本户', 967, 2, 'ASSET', 'DEBIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '100201' AND `del_flag` = 0);

INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '100202', '银行存款-一般户', 967, 2, 'ASSET', 'DEBIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '100202' AND `del_flag` = 0);

-- ③-3 应付账款明细（parent=968 应付账款）
INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '220201', '应付账款-国内供应商', 968, 2, 'LIABILITY', 'CREDIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '220201' AND `del_flag` = 0);

-- ③-4 主营业务收入明细（parent=1584 主营业务收入）
INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '60010103', '主营业务收入-国内销售', 1584, 2, 'COST', 'CREDIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '60010103' AND `del_flag` = 0);

INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '60010104', '主营业务收入-国外销售', 1584, 2, 'COST', 'CREDIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '60010104' AND `del_flag` = 0);

-- ③-5 主营业务成本明细（parent=1585 主营业务成本）
INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '640101', '主营业务成本-国内销售', 1585, 2, 'COST', 'DEBIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '640101' AND `del_flag` = 0);

-- ③-6 应交增值税明细（parent=1592 应交税费）
INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '22210101', '应交税费-应交增值税（销项税额）', 1592, 2, 'LIABILITY', 'CREDIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '22210101' AND `del_flag` = 0);

INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '22210102', '应交税费-应交增值税（进项税额）', 1592, 2, 'LIABILITY', 'DEBIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '22210102' AND `del_flag` = 0);

INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '22210201', '应交税费-应交企业所得税', 1592, 2, 'LIABILITY', 'CREDIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '22210201' AND `del_flag` = 0);

-- ③-7 库存商品明细（parent=1580 库存商品）
INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '140501', '库存商品-原材料', 1580, 2, 'ASSET', 'DEBIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '140501' AND `del_flag` = 0);

INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '140502', '库存商品-产成品', 1580, 2, 'ASSET', 'DEBIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '140502' AND `del_flag` = 0);

-- ③-8 固定资产明细（parent 需先查 1601 在建工程/固定资产一级科目）
INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '660203', '管理费用-折旧费', 919, 2, 'COST', 'DEBIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '660203' AND `del_flag` = 0);

INSERT INTO `md_subject`
  (`tenant_id`, `company_id`, `subject_code`, `subject_name`, `parent_id`, `subject_level`,
   `subject_type`, `direction`, `is_leaf`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 1, '660204', '销售费用-差旅费', 919, 2, 'COST', 'DEBIT', 1, 'NORMAL',
       '造数前置补档(V1.4.80)', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `md_subject` WHERE `subject_code` = '660204' AND `del_flag` = 0);
