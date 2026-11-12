-- =====================================================================
-- V1.6.90  2026-11-03 增量更新「一次性执行」合集
-- 生成方式：按序拼接 V1.6.00 / 01 / 02 / 04 / 05 / 06 / 07 / 08 / 09 / 10 / 11 / 12 / 13 / 14
--          （每个子脚本也可单独执行）
--
-- 适用：从 2026-11-02 版本升级到含「税务链 + 预算链 + 多租户 + 预算口径开关 + 权限修补」的版本
-- 特性：可重复执行（重复跑不会重复建表/建列/建菜单/灌种子/补科目/补租户列/重复删权限/重复授权/重复建规则/重复改说明）
--
--  不含 V1.6.03（Flowable 租户回填）——那一步必须在「停服窗口」单独执行：
--      停服 → V1.6.03 → 换新 jar 起服 → 冒烟（详见 docs/部署运维.md §7.3）
--   把 03 混进本合集会在业务运行期改引擎定义租户，导致在办实例与新建单双向失联。
--
--  权限类变更（11 / 12 / 13）执行后，相关用户需重新登录才生效（权限随登录令牌下发）。
--
-- 执行：mysql -h<host> -P3306 -u<user> -p <库名> < V1.6.90_20261103_all_in_one.sql
-- 回滚：按子脚本对应的 *_rollback.sql 逆序执行
-- =====================================================================

-- 如库名不是 fssc，请取消下一行注释并改成实际库名
-- USE `fssc`;


-- ---------------------------------------------------------------
-- >>> V1.6.00_20261103_helper.sql
-- ---------------------------------------------------------------

-- =====================================================================
-- V1.6.00 通用助手：可重复执行的加列/删列（MySQL 8 无 ADD COLUMN IF NOT EXISTS）
-- 本文件只建两个临时存储过程，不碰业务数据；V1.6.01 起各脚本首行都 CALL 它。
-- 执行顺序：必须先跑本文件。
-- =====================================================================

DROP PROCEDURE IF EXISTS fssc_add_column;
DROP PROCEDURE IF EXISTS fssc_drop_column;

DELIMITER $$
CREATE PROCEDURE fssc_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
  ) THEN
    SET @s = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$

CREATE PROCEDURE fssc_drop_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
  ) THEN
    SET @s = CONCAT('ALTER TABLE `', p_table, '` DROP COLUMN `', p_column, '`');
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;


-- ---------------------------------------------------------------
-- >>> V1.6.01_tax_provision_rule.sql
-- ---------------------------------------------------------------

-- =====================================================================
-- V1.6.01 税金计提规则表+ 申报性质+ 付款单税种
-- 依赖：V1.6.00（助手过程 fssc_add_column）
-- 可重复执行：是（建表 IF NOT EXISTS；加列走助手过程）
-- 影响表：tax_provision_rule（新建）/ tax_declaration / exp_bill
-- 说明：全部为「加列/建表」，不改既有列、不动数据，可在业务运行期执行。
-- =====================================================================

-- ---------- 1. 税金计提规则 ----------
CREATE TABLE IF NOT EXISTS `tax_provision_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `tax_type` varchar(32) NOT NULL COMMENT '税种码：VAT/SURCHARGE/INCOME/IIT/STAMP/PROPERTY/LAND/VEHICLE',
  `rule_name` varchar(100) NOT NULL COMMENT '规则名（展示用）',
  `base_source` varchar(20) NOT NULL DEFAULT 'MANUAL' COMMENT '基数来源：MANUAL手工录入/DECLARATION按已申报数汇总/INVOICE按发票池已认证进项税汇总',
  `base_amount` decimal(18,2) DEFAULT NULL COMMENT '手工基数来源时的缺省计税基数',
  `tax_rate` decimal(10,4) NOT NULL COMMENT '税率（百分数，13.00 表示 13%）',
  `debit_subject_code` varchar(64) DEFAULT NULL COMMENT '计提凭证借方科目码（6403/6801）',
  `credit_subject_code` varchar(64) DEFAULT NULL COMMENT '计提凭证贷方科目码（应交税费明细）',
  `declare_credit_subject_code` varchar(64) DEFAULT '2221' COMMENT '申报凭证贷方科目码（未交税金）',
  `pay_debit_subject_code` varchar(64) DEFAULT '2221' COMMENT '缴纳（付款）凭证借方科目码',
  `period_type` varchar(16) DEFAULT 'MONTHLY' COMMENT '纳税周期：MONTHLY/QUARTERLY/HALF_YEARLY/ANNUAL/PER_TIME',
  `declare_deadline_day` int DEFAULT '15' COMMENT '申报截止日（月内第几日）',
  `pay_deadline_day` int DEFAULT '15' COMMENT '缴款截止日（月内第几日）',
  `sort` int DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/DISABLED',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_tax_type` (`tenant_id`,`tax_type`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='税金计提规则（税种/基数来源/税率/借贷科目）';

-- ---------- 2. 申报性质 + 更正关联 ----------
CALL fssc_add_column('tax_declaration', 'declaration_kind',
  "`declaration_kind` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT '申报性质：NORMAL正常申报/ADJUST更正（调整）申报' AFTER `declare_type`");
CALL fssc_add_column('tax_declaration', 'related_id',
  "`related_id` bigint DEFAULT NULL COMMENT '更正申报关联的原申报单 id' AFTER `declaration_kind`");

UPDATE `tax_declaration` SET `declaration_kind` = 'NORMAL'
 WHERE `declaration_kind` IS NULL OR `declaration_kind` = '';

-- ---------- 3. 付款单「缴纳税种 / 税期」 ----------
-- 非空即声明本笔付款是缴税：付款批次出凭证时借方按税种取应交税费(2221)明细，而不是默认 2202 应付账款。
CALL fssc_add_column('exp_bill', 'tax_type',
  "`tax_type` varchar(32) DEFAULT NULL COMMENT '缴纳税种（仅对公付款的税款付款填）：非空即按税金付款出凭证' AFTER `delivery_status`");
CALL fssc_add_column('exp_bill', 'tax_period',
  "`tax_period` varchar(6) DEFAULT NULL COMMENT '缴纳税期 yyyyMM（与 tax_type 成对）' AFTER `tax_type`");

-- ---------- 4. 校验 ----------
SELECT 'V1.6.01' AS script,
       (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_provision_rule') AS tax_rule_table,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_declaration'
           AND COLUMN_NAME IN ('declaration_kind','related_id')) AS tax_decl_cols,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exp_bill'
           AND COLUMN_NAME IN ('tax_type','tax_period')) AS exp_bill_cols;
-- 期望：tax_rule_table=1, tax_decl_cols=2, exp_bill_cols=2


-- ---------------------------------------------------------------
-- >>> V1.6.02_budget_chain.sql
-- ---------------------------------------------------------------

-- =====================================================================
-- V1.6.02 预算链结构变更
-- 依赖：V1.6.00
-- 可重复执行：是
-- 影响表：bg_item / md_item_budget_map / md_gl_config
-- 说明：全部加列，不改既有列。存量行新列为 NULL/0，行为与改造前一致。
-- =====================================================================

-- ---------- 1. bg_item：预算类型 / 预算期间主数据 / 冻结 ----------
CALL fssc_add_column('bg_item', 'budget_type_id',
  "`budget_type_id` bigint DEFAULT NULL COMMENT '预算类型（md_budget_type.id）；空=历史行按常规预算处理' AFTER `project_id`");
CALL fssc_add_column('bg_item', 'budget_period_id',
  "`budget_period_id` bigint DEFAULT NULL COMMENT '预算期间（md_budget_period.id）；有起止日期时匹配优先于裸整数' AFTER `budget_type_id`");
CALL fssc_add_column('bg_item', 'freeze',
  "`freeze` tinyint NOT NULL DEFAULT '0' COMMENT '冻结标志：1=拒绝新增占用，释放/核销照常' AFTER `used_amount`");

-- 便于按类型/期间归集（「控制/取数/预警三处按 periodId 归集」）
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bg_item' AND INDEX_NAME = 'idx_tenant_period_id');
SET @s := IF(@idx = 0,
  'ALTER TABLE `bg_item` ADD KEY `idx_tenant_period_id` (`tenant_id`,`budget_period_id`)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. md_item_budget_map：费用项映射级控制档位 ----------
-- 为空＝沿用方案级 bg_scheme.control_type（存量 211 行全部为空，改造后行为不变）
CALL fssc_add_column('md_item_budget_map', 'control_type',
  "`control_type` varchar(20) DEFAULT NULL COMMENT '控制档位覆盖：HARD/SOFT/WARN/NONE；空=沿用方案级' AFTER `budget_dim_key`");

-- ---------- 3. md_gl_config：单据类型维度（，扩到薪酬族） ----------
-- 为空＝费用报销族（历史 3816 行全是这一族，靠 item_id 匹配）
CALL fssc_add_column('md_gl_config', 'doc_type',
  "`doc_type` varchar(32) DEFAULT NULL COMMENT '单据类型（GL_SALARY_PROV/GL_SALARY_PAY 等）；空=费用报销族' AFTER `direction`");

-- ---------- 4. 校验 ----------
SELECT 'V1.6.02' AS script,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bg_item'
           AND COLUMN_NAME IN ('budget_type_id','budget_period_id','freeze')) AS bg_item_cols,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'md_item_budget_map'
           AND COLUMN_NAME = 'control_type') AS map_cols,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'md_gl_config'
           AND COLUMN_NAME = 'doc_type') AS gl_cfg_cols;
-- 期望：3 / 1 / 1


-- ---------------------------------------------------------------
-- >>> V1.6.04_menu_tax_workbench.sql
-- ---------------------------------------------------------------

-- =====================================================================
-- V1.6.04 菜单：税务管理（ 工作区；进项认证页签已于 2026-11-03 去重删除，见 V1.6.11）
-- 依赖：无（前端路由 tax/workbench 已随包发布）
-- 可重复执行：是（按 path 判重）
-- 影响表：sys_menu / sys_role_menu
-- 说明：不新建目录树，挂在既有「总账/GL」目录 3064 下；权限沿用既有 gl:tax* 码，不新增权限点。
--        3064 是现网目录 id；若目标库 id 不同，请把下文的 @gl_parent 改成实际值：
--         SELECT id, menu_name FROM sys_menu WHERE menu_name LIKE '%总账%' AND menu_type='M';
-- =====================================================================

SET @gl_parent := (SELECT id FROM sys_menu WHERE id = 3064);
SET @gl_parent := IFNULL(@gl_parent,
  (SELECT id FROM (SELECT id FROM sys_menu WHERE menu_type = 'M' AND menu_name LIKE '%总账%' LIMIT 1) t));

-- ① 主菜单（C 型页面）
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon, feature_code,
                      terminal, sort, visible, status, create_time, del_flag)
SELECT @gl_parent, '税务管理', 'C', '/tax/workbench', 'tax/workbench/index',
       'gl:taxprov:list,gl:taxdecl:list,gl:taxrisk:list',
       'Money', NULL, 'PC', 6, 1, 1, NOW(), 0
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE path = '/tax/workbench') x);

SET @tax_menu_id := (SELECT id FROM sys_menu WHERE path = '/tax/workbench' ORDER BY id LIMIT 1);

-- ② 页签按钮（F 型）：计提规则配置复用 gl:taxprov:provision，申报/认证/风险复用既有码，
--    这里只补「计提规则」这一颗入口（其余按钮行 3098/3442~3455 已在，不重复造）
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon, feature_code,
                      terminal, sort, visible, status, create_time, del_flag)
SELECT @tax_menu_id, '计提规则配置', 'F', NULL, NULL, 'gl:taxprov:provision', NULL, NULL,
       'PC', 1, 1, 1, NOW(), 0
 WHERE @tax_menu_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu
                                   WHERE parent_id = @tax_menu_id AND menu_name = '计提规则配置') y);

-- ③ 授权：凡是已授予「GL专项流程(3074)」或任一涉税按钮的角色，一并授予新菜单，
--    否则新页面只有管理员看得见（"有页面无入口"的另一种形态）
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT DISTINCT rm.tenant_id, rm.role_id, @tax_menu_id, 1, NOW()
  FROM sys_role_menu rm
 WHERE @tax_menu_id IS NOT NULL
   AND rm.menu_id IN (3074, 3098, 3442, 3448, 3454)
   AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_menu
                                   WHERE role_id = rm.role_id AND menu_id = @tax_menu_id) z);

-- ④ 校验
SELECT 'V1.6.04' AS script, @tax_menu_id AS tax_menu_id,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = @tax_menu_id) AS granted_roles;
-- 期望：tax_menu_id 非空；granted_roles ≥ 1


-- ---------------------------------------------------------------
-- >>> V1.6.05_tax_rule_seed.sql
-- ---------------------------------------------------------------

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


-- ---------------------------------------------------------------
-- >>> V1.6.06_platform_cleanup_and_view.sql
-- ---------------------------------------------------------------

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


-- ---------------------------------------------------------------
-- >>> V1.6.07_perm_budget_issue.sql
-- ---------------------------------------------------------------

-- =====================================================================
-- V1.6.07 权限点补登记：budget:scheme:issue
-- 依赖：V1.6.00（无表结构变更）
-- 可重复执行：是
-- 影响表：sys_menu / sys_role_menu
-- 说明：免审直下达（DRAFT → ISSUED）原与「提交审批」共用 budget:scheme:update，
--       有编辑权的人可以完全绕过审批链。本脚本把该动作独立成 budget:scheme:issue。
--        刻意「不自动授权给任何人」：这是一条旁路权限，默认无人持有才叫收紧；
--         初始化导入场景由管理员在【角色管理】按岗位勾选，脚本不替业务做这个决定。
--         超级管理员（is_admin=1）按既有通配规则仍可用，用于运维兜底。
-- =====================================================================

SET @budget_menu := (SELECT id FROM sys_menu WHERE path = '/budget/workbench' ORDER BY id LIMIT 1);
SET @budget_menu := IFNULL(@budget_menu,
  (SELECT id FROM (SELECT id FROM sys_menu WHERE menu_name = '预算管理工作台' AND menu_type = 'C' LIMIT 1) t));

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon, feature_code,
                      terminal, sort, visible, status, create_time, del_flag)
SELECT @budget_menu, '预算方案免审下达', 'F', NULL, NULL, 'budget:scheme:issue', NULL, NULL,
       'PC', 9, 1, 1, NOW(), 0
 WHERE @budget_menu IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu
                                   WHERE perms = 'budget:scheme:issue' AND menu_type = 'F') x);

SET @issue_perm_id := (SELECT id FROM sys_menu WHERE perms = 'budget:scheme:issue' AND menu_type = 'F' ORDER BY id LIMIT 1);

-- 授权：默认不授权（见上方说明）。如需给初始化导入岗开放，取消下一段注释并指定 role_id：
-- INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
-- SELECT 1, <role_id>, @issue_perm_id, 1, NOW()
--  WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_menu
--                    WHERE role_id = <role_id> AND menu_id = @issue_perm_id) z);

SELECT 'V1.6.07' AS script, @issue_perm_id AS issue_perm_menu_id,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = @issue_perm_id) AS granted_roles;
-- 期望：issue_perm_menu_id 非空；granted_roles ≥ 1


-- ---------------------------------------------------------------
-- >>> V1.6.08_tax_subject_leaf_fix.sql
-- ---------------------------------------------------------------

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


-- ---------------------------------------------------------------
-- >>> V1.6.09_accrual_budget_occupy.sql
-- ---------------------------------------------------------------

-- =====================================================================
-- V1.6.09 预提/待摊纳入预算「申请占用」（预算影响类型三值之一）
--
-- 背景：整改前 ACCRUAL/DEFER 记账单与预算零关联，而合同反而占预算——与在册手册相反。
--       预算影响类型按三类划分：
--         ① 申请占用（对预算数扣减，参与控制检查，费用尚未实际执行）
--         ② 预算执行（费用实际发生）
--         ③ 预算冲回（冲回/负数，只影响可用预算数）
--       实际执行 = 申请占用 − 申请冲回 + 预算执行 − 执行冲回；
--       可用预算 = 调整后预算数 − 实际执行。预提落在 ①，冲销预提落在 ③。
--
-- 依赖：V1.6.00（fssc_add_column 助手过程）
-- 可重复执行：是
-- 影响表：gl_journal_entry（只加列 + 一次历史行定性回填，不改金额、不改状态）
-- =====================================================================

-- ① 预算维度入参：费用项目 + 承担部门（预提单上由制单人选择，用于定位 bg_item 行）
CALL fssc_add_column('gl_journal_entry', 'expense_item_id',
    '`expense_item_id` bigint DEFAULT NULL COMMENT ''费用项目 md_expense_item.id（预算维度来源）''');
CALL fssc_add_column('gl_journal_entry', 'dept_id',
    '`dept_id` bigint DEFAULT NULL COMMENT ''承担部门 sys_dept.id（预算维度来源）''');

-- ② 占用结果：解析到的预算明细 + 占用状态（NONE/OCCUPIED/RELEASED）
--    budget_item_id 落库而非每次重算——主数据调整后仍能找回"当初占的是哪条预算"，释放才不会跑偏。
CALL fssc_add_column('gl_journal_entry', 'budget_item_id',
    '`budget_item_id` bigint DEFAULT NULL COMMENT ''实际占用到的预算明细 bg_item.id''');
CALL fssc_add_column('gl_journal_entry', 'budget_occupy_status',
    '`budget_occupy_status` varchar(16) DEFAULT NULL COMMENT ''预算占用状态：OCCUPIED/RELEASED/NONE/null''');

-- ③ 历史行定性：升级前的 ACCRUAL/DEFER 从未占用过预算，标 NONE 而不是留 NULL。
--    留 NULL 会与「MANUAL 等本就不涉预算的类型」混为一谈，报表上分不清"没占"和"不涉及"。
UPDATE gl_journal_entry
   SET budget_occupy_status = 'NONE'
 WHERE entry_type IN ('ACCRUAL', 'DEFER')
   AND budget_occupy_status IS NULL;

SELECT 'V1.6.09 完成：gl_journal_entry 已具备预提/待摊预算占用字段' AS result;


-- ---------------------------------------------------------------
-- >>> V1.6.10_tenant_columns_closeout.sql
-- ---------------------------------------------------------------

-- =====================================================================
-- V1.6.10 三张「缺 tenant_id」表收口（ / 清库窗任务）
--
-- 处置原则不是"一律补列"，而是按表的语义分两类：
--   ① 业务表 —— 必须补 tenant_id，让 MyBatis-Plus 拦截器统一接管；
--   ② 全局运维表 —— 补列反而错（一天一行会变成一租户一天一行），登记进白名单即可。
--
-- 分表结论：
--   so_order_line        业务表（销售订单行）→ 补列 + 由 so_order 回填 + 索引
--   wf_model_bpmn_backup 业务附属表（流程模型 BPMN 备份）→ 补列 + 由 wf_model 回填 + 索引
--   op_stability_log     全局运维表（稳定性日快照，StabilityLogJob 每日一行）→ 不补列，
--                        登记进 FsscTenantLineHandler.GLOBAL_DEF_TABLES 白名单（同 op_job_heartbeat）
--
-- 依赖：V1.6.00（fssc_add_column 助手过程）
-- 可重复执行：是
-- =====================================================================

-- ① so_order_line：补列（默认 1 只为满足 NOT NULL；紧接着按父单真实租户回填）
CALL fssc_add_column('so_order_line', 'tenant_id',
    '`tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户ID（由 so_order 回填）''');

UPDATE so_order_line l
  JOIN so_order o ON o.id = l.so_id
   SET l.tenant_id = o.tenant_id
 WHERE l.tenant_id <> o.tenant_id;

-- ② wf_model_bpmn_backup：同上，父单是 wf_model
CALL fssc_add_column('wf_model_bpmn_backup', 'tenant_id',
    '`tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户ID（由 wf_model 回填）''');

UPDATE wf_model_bpmn_backup b
  JOIN wf_model m ON m.id = b.model_id
   SET b.tenant_id = m.tenant_id
 WHERE b.tenant_id <> m.tenant_id;

-- ③ 索引：拦截器给每条 MP 查询都追加 tenant_id 谓词，无索引会退化成全表扫
SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'so_order_line'
                    AND INDEX_NAME = 'idx_so_order_line_tenant');
SET @s := IF(@has_idx = 0,
    'ALTER TABLE `so_order_line` ADD INDEX `idx_so_order_line_tenant` (`tenant_id`)',
    'DO 0');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_model_bpmn_backup'
                    AND INDEX_NAME = 'idx_wf_bpmn_backup_tenant');
SET @s := IF(@has_idx = 0,
    'ALTER TABLE `wf_model_bpmn_backup` ADD INDEX `idx_wf_bpmn_backup_tenant` (`tenant_id`, `model_key`)',
    'DO 0');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ④ op_stability_log 不建列：只做存在性断言（若历史环境曾误加列，此处不删，交人工判定）
SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.COLUMNS
                           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_stability_log'
                             AND COLUMN_NAME = 'tenant_id')
            THEN 'WARN: op_stability_log 已有 tenant_id（本脚本不改它，请确认是否历史误加）'
            ELSE 'OK: op_stability_log 保持全局运维表形态（已在 Java 白名单登记）'
       END AS op_stability_log_check;

SELECT 'V1.6.10 完成：so_order_line / wf_model_bpmn_backup 已补 tenant_id 并回填' AS result;


-- ---------------------------------------------------------------
-- >>> V1.6.11_drop_dup_tax_certify.sql
-- ---------------------------------------------------------------

-- =====================================================================
-- V1.6.11 删除「税务侧重复的进项认证」（2026-11-03 功能去重）
--
-- 背景：进项认证的对象是发票池里的进项发票（inv_invoice），唯一写入口是
--       InvoiceService.certify / cancelCertify / transferOut（/api/tenant/invoice/certify/**，
--       页面在「发票管理 → 进项发票认证」，菜单 3075）。
--       税务工作区此前另做了一套 /api/tenant/tax/certify/** + 独立页面，
--       同一动作两个入口、两套权限码、两处状态口径 —— 重复且容易互相覆盖。
--       现已合并：税务侧页面 / 接口 / 服务全部删除，只保留发票池一套。
--
-- 本脚本只清理「已经没有人用的」权限码，不动任何业务数据：
--   · sys_menu 里 gl:taxcertify:* 六个功能点（3442~3447）
--   · 对应的 sys_role_menu 授权（现盘 18 行）
--
--  tax_input_certify 表保留不删：它是改造前的历史台账（现盘 0 行），
--   删表属不可逆动作，交清库窗单独确认；本脚本不碰它。
--
-- 可重复执行：是（按 perms 前缀删，删过即空）
-- =====================================================================

-- ① 先撤授权（外键意义上的顺序：先子表再主表）
DELETE rm FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE m.perms LIKE 'gl:taxcertify:%';

-- ② 再删权限点
DELETE FROM sys_menu WHERE perms LIKE 'gl:taxcertify:%';

-- ③ 菜单级 perms 串里也残留着这个码（如「税务管理」菜单的
--    'gl:taxprov:list,gl:taxdecl:list,gl:taxcertify:list,gl:taxrisk:list'），一并摘掉，
--    否则新环境从零装时菜单会引用一个不存在的权限码。
UPDATE sys_menu
   SET perms = TRIM(BOTH ',' FROM REPLACE(REPLACE(perms, 'gl:taxcertify:list,', ''), 'gl:taxcertify:list', ''))
 WHERE perms LIKE '%gl:taxcertify%';

-- ④ 校验：应当一行都不剩
SELECT '残余 gl:taxcertify 权限点' AS check_id, COUNT(*) AS cnt
  FROM sys_menu WHERE perms LIKE 'gl:taxcertify:%';

SELECT 'V1.6.11 完成：税务侧重复的进项认证权限点已清理（含菜单 perms 串）' AS result;


-- ---------------------------------------------------------------
-- >>> V1.6.12_inv_certify_perm_grant.sql
-- ---------------------------------------------------------------

-- =====================================================================
-- V1.6.12 进项认证导出权限补齐
--
-- 背景
--   进项认证的入口已从「税务工作台」收敛到「发票管理」工作台（同一张 inv_invoice，
--   唯一写入口是 /api/tenant/invoice/certify/**）。
--   发票认证页的「导出」按钮按 inv:export 门控，但库里从未定义过这个权限码，
--   v-permission 指令判定失败即把元素从 DOM 移除 —— 结果是非管理员永远看不到导出按钮。
--   （同类码其它域是有的：report:export / returns:pur:export / compliance:export，
--     本项目惯例是导出独立授权，故补码，而不是把按钮改成复用查询权。）
--
-- 本脚本做两件事
--   ① 补建 inv:export 权限点，挂发票管理下，与「进项发票认证-认证」并列。
--   ② 给「已经持有 inv:certify 的角色」补授 inv:export。
--      只补导出、不动认证权：能进认证页的人才有导出按钮，给没有认证权的人授导出是空授权；
--      而认证权属岗位分工（总账会计与税务岗不同），脚本不替业务扩权。
--
-- 幂等：重复执行不重复建点、不重复授权。
-- 生效：权限写入登录令牌，相关用户需重新登录。
-- 回滚：V1.6.12_inv_certify_perm_grant_rollback.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- ① 补建 inv:export 权限点
--    sys_menu.id 无自增，须显式取号；parent 取发票管理（perms=invoice:list 的 M 节点）。
-- ---------------------------------------------------------------------
SET @invoice_root_id := (
    SELECT id FROM sys_menu
     WHERE perms = 'invoice:list' AND menu_type = 'M' AND del_flag = 0
     ORDER BY id LIMIT 1
);

SET @export_menu_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_menu);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, terminal,
                      sort, visible, status, create_by, create_time, del_flag)
SELECT @export_menu_id,
       COALESCE(@invoice_root_id, 215),
       '进项发票认证-导出', 'F', 'inv:export', 'PC',
       9011, 1, 1, 1, NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM (SELECT id FROM sys_menu WHERE perms = 'inv:export' AND del_flag = 0) z
       );

-- ---------------------------------------------------------------------
-- ② 给「已持有 inv:certify 的角色」补授 inv:export
--    认证权载体按 del_flag=0 取（早期的独立菜单节点已软删，授给它取不到码）。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT DISTINCT rm.tenant_id, rm.role_id, t.id, 1, NOW()
  FROM sys_role_menu rm
  JOIN sys_menu src
    ON src.id = rm.menu_id
   AND src.del_flag = 0
   AND src.status = 1
   AND src.perms LIKE '%gl:taxprov:list%'
  JOIN sys_menu t
    ON t.id = (
        SELECT MIN(m2.id) FROM sys_menu m2
         WHERE m2.perms = 'inv:export' AND m2.del_flag = 0 AND m2.status = 1
       )
 WHERE EXISTS (
        SELECT 1 FROM (
            SELECT rm2.tenant_id, rm2.role_id
              FROM sys_role_menu rm2
              JOIN sys_menu m3
                ON m3.id = rm2.menu_id
               AND m3.del_flag = 0
               AND m3.status = 1
               AND m3.perms = 'inv:certify'
        ) c
         WHERE c.tenant_id = rm.tenant_id
           AND c.role_id  = rm.role_id
       )
   AND NOT EXISTS (
        SELECT 1 FROM (SELECT tenant_id, role_id, menu_id FROM sys_role_menu) x
         WHERE x.tenant_id = rm.tenant_id
           AND x.role_id  = rm.role_id
           AND x.menu_id  = t.id
       );

-- ---------------------------------------------------------------------
-- ③ 校验输出
-- ---------------------------------------------------------------------
SELECT '① inv:export 权限点' AS step, id, parent_id, menu_name, perms, status, del_flag
  FROM sys_menu WHERE perms = 'inv:export';

SELECT '② 税务工作区角色的发票权限覆盖' AS step,
       r.role_code, r.role_name,
       MAX(CASE WHEN m.perms = 'inv:list'    THEN 1 ELSE 0 END) AS has_list,
       MAX(CASE WHEN m.perms = 'inv:certify' THEN 1 ELSE 0 END) AS has_certify,
       MAX(CASE WHEN m.perms = 'inv:export'  THEN 1 ELSE 0 END) AS has_export
  FROM sys_role r
  JOIN sys_role_menu rm ON rm.role_id = r.id
  JOIN sys_menu m ON m.id = rm.menu_id AND m.del_flag = 0 AND m.status = 1
 WHERE r.id IN (
        SELECT DISTINCT rm2.role_id
          FROM sys_role_menu rm2
          JOIN sys_menu s2 ON s2.id = rm2.menu_id
         WHERE s2.perms LIKE '%gl:taxprov:list%' AND s2.del_flag = 0 AND s2.status = 1
       )
 GROUP BY r.id, r.role_code, r.role_name
 ORDER BY has_certify DESC, r.id;

-- 提示：has_certify = 0 的角色本次不会获得导出权（无认证页可进）。
--       若业务确认该岗位也需要做认证，请在【角色管理】勾选「进项发票认证-认证」，
--       脚本不代替业务做这个扩权决定。
SELECT '③ 未获导出权的角色（缺认证权，需业务确认）' AS step, r.role_code, r.role_name
  FROM sys_role r
 WHERE r.id IN (
        SELECT DISTINCT rm2.role_id
          FROM sys_role_menu rm2
          JOIN sys_menu s2 ON s2.id = rm2.menu_id
         WHERE s2.perms LIKE '%gl:taxprov:list%' AND s2.del_flag = 0 AND s2.status = 1
       )
   AND NOT EXISTS (
        SELECT 1 FROM sys_role_menu rm3
          JOIN sys_menu m3 ON m3.id = rm3.menu_id AND m3.del_flag = 0 AND m3.status = 1
         WHERE rm3.role_id = r.id AND m3.perms = 'inv:certify'
       );

SELECT 'V1.6.12 完成：进项认证导出权限已补齐（相关用户需重新登录生效）' AS result;


-- ---------------------------------------------------------------
-- >>> V1.6.13_supplier_approve_perm.sql
-- ---------------------------------------------------------------

-- =====================================================================
-- V1.6.13 供应商准入核准权限启用
--
-- 业务背景
--   供应商主数据是付款对象的唯一来源。新建供应商时状态写 PENDING，须经「状态位核准」
--   翻为 ACTIVE 后才可被业务单据引用 —— 这是一道准入控制：防止未经审核的供应商进入
--   付款链路（虚假供应商、关联方利益输送）。
--   会计与内控要求「经办与核准分离」：建档人不得同时核准自己建的档案。
--
-- 现存缺陷
--   核准权限点 md:supplier:approve 在库里是存在的，但：
--     ① 状态为停用（status=0），权限加载只认 status=1，等于该码不存在；
--     ② 挂在「总账核算」菜单下，与供应商主数据不在同一处，角色树里找不到。
--   结果：除管理员外无人能看到「核准」按钮，供应商只能停在 PENDING ——
--   要么业务卡死，要么管理员代劳，两种都让这道控制失效。
--
-- 本脚本
--   ① 启用该权限点，并归位到「系统主数据」菜单下；
--   ② 授予「财务经理」：该角色已有供应商查询权、但没有建档/修改权，
--      天然与建档岗（基地财务经理、数据配置管理员）分离；
--      若某环境该角色已持有建档权，脚本会跳过并输出，不制造自建自批；
--   ③ 新增互斥规则「供应商建档 ↔ 核准」，防止日后把两个权限并给同一角色。
--
-- 幂等：重复执行不重复建点、不重复授权、不重复插规则。
-- 生效：权限写入登录令牌，相关用户需重新登录。
-- 回滚：V1.6.13_supplier_approve_perm_rollback.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- ① 启用并归位权限点
-- ---------------------------------------------------------------------
SET @md_root_id := (
    SELECT id FROM sys_menu
     WHERE menu_name = '系统主数据' AND menu_type = 'M' AND del_flag = 0
     ORDER BY id LIMIT 1
);

UPDATE sys_menu
   SET status = 1,
       del_flag = 0,
       menu_type = 'F',
       parent_id = COALESCE(@md_root_id, parent_id),
       menu_name = '供应商状态核准'
 WHERE perms = 'md:supplier:approve';

-- 老库可能没有这一行，补建（显式取号，sys_menu.id 无自增）
SET @approve_menu_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_menu);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, terminal,
                      sort, visible, status, create_by, create_time, del_flag)
SELECT @approve_menu_id,
       COALESCE(@md_root_id, 3031),
       '供应商状态核准', 'F', 'md:supplier:approve', 'PC',
       56, 1, 1, 1, NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM (SELECT id FROM sys_menu WHERE perms = 'md:supplier:approve' AND del_flag = 0) z
       );

-- ---------------------------------------------------------------------
-- ② 授予「财务经理」
--    跳过已持有建档权（create/update/delete）的角色：给了就是自建自批。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT r.tenant_id, r.id, t.id, 1, NOW()
  FROM sys_role r
  JOIN sys_menu t
    ON t.perms = 'md:supplier:approve' AND t.status = 1 AND t.del_flag = 0
 WHERE r.role_code = 'FIN_MANAGER'
   AND r.status = 'NORMAL'
   AND r.del_flag = 0
   AND NOT EXISTS (
        SELECT 1 FROM (SELECT tenant_id, role_id, menu_id FROM sys_role_menu) x
         WHERE x.tenant_id = r.tenant_id AND x.role_id = r.id AND x.menu_id = t.id
       )
   AND NOT EXISTS (
        SELECT 1 FROM (
            SELECT rm2.tenant_id, rm2.role_id
              FROM sys_role_menu rm2
              JOIN sys_menu m2
                ON m2.id = rm2.menu_id
               AND m2.del_flag = 0
               AND m2.status = 1
               AND m2.perms IN ('md:supplier:create', 'md:supplier:update', 'md:supplier:delete')
        ) c
         WHERE c.tenant_id = r.tenant_id AND c.role_id = r.id
       );

-- ---------------------------------------------------------------------
-- ③ 互斥规则：供应商建档 ↔ 核准
--    供应商建档权与核准权落在同一角色，等于自己建、自己批，准入控制形同虚设。
-- ---------------------------------------------------------------------
INSERT INTO sod_perm_conflict (tenant_id, rule_code, rule_name, risk,
                               pattern_a, pattern_b, note, status, create_time)
SELECT NULL, 'SoD-24', '供应商建档↔核准', '高',
       '^md:supplier:(create|update|delete)$',
       '^md:supplier:approve$',
       '供应商是付款对象的唯一来源，建档与核准须分离；同角色兼任可自建虚假供应商并核准后付款',
       'ACTIVE', NOW()
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM (SELECT id FROM sod_perm_conflict WHERE rule_code = 'SoD-24' AND tenant_id IS NULL) z
       );

-- ---------------------------------------------------------------------
-- ④ 校验输出
-- ---------------------------------------------------------------------
SELECT '① 权限点' AS step, id, parent_id, menu_name, menu_type, perms, status, del_flag
  FROM sys_menu WHERE perms = 'md:supplier:approve';

SELECT '② 核准岗授权' AS step, r.tenant_id, r.role_code, r.role_name
  FROM sys_role r
  JOIN sys_role_menu rm ON rm.role_id = r.id
  JOIN sys_menu m ON m.id = rm.menu_id AND m.del_flag = 0 AND m.status = 1
 WHERE m.perms = 'md:supplier:approve'
 ORDER BY r.tenant_id, r.id;

SELECT '③ 互斥规则' AS step, rule_code, rule_name, risk, status
  FROM sod_perm_conflict WHERE rule_code = 'SoD-24';

-- 自建自批风险检查（应尽量为空）：同时持有建档权与核准权的角色
SELECT '④ 自建自批风险' AS step, r.role_code, r.role_name
  FROM sys_role r
 WHERE EXISTS (
        SELECT 1 FROM sys_role_menu rm JOIN sys_menu m ON m.id = rm.menu_id
         WHERE rm.role_id = r.id AND m.del_flag = 0 AND m.status = 1
           AND m.perms IN ('md:supplier:create', 'md:supplier:update', 'md:supplier:delete'))
   AND EXISTS (
        SELECT 1 FROM sys_role_menu rm JOIN sys_menu m ON m.id = rm.menu_id
         WHERE rm.role_id = r.id AND m.del_flag = 0 AND m.status = 1
           AND m.perms = 'md:supplier:approve');

SELECT 'V1.6.13 完成：供应商核准权限已启用（相关用户需重新登录生效）' AS result;


-- ---------------------------------------------------------------
-- >>> V1.6.14_config_remark_cleanup.sql
-- ---------------------------------------------------------------

-- =====================================================================
-- V1.6.14 参数配置说明文字订正
--
-- 背景
--   参数配置页直接展示 sys_config.remark。历史备注里夹带了内部迭代批次号
--   （「批次1②」「批次1⑤」「批次2端到端演练」）、内部裁决编号（「RA-F2a」）、
--   内部文档坐标（「V5 册零章 Z35」）。客户在参数配置页看到这些，既无法对应
--   到自己的需求，也不该看到内部工作方式。
--
-- 处理
--   仅订正说明文字，**不动任何配置值**，不影响运行行为。
--   保留：日期、技术原因、不可如何操作的告警——这些对运维有实际价值。
--
-- 幂等：重复执行结果一致（文本替换，改完再跑无变化）。
-- 回滚：V1.6.14_config_remark_cleanup_rollback.sql（恢复原文）
-- =====================================================================

-- ① 连办拦截开关
UPDATE sys_config
   SET remark = '值=1 表示对所有流程启用连办拦截；已按此口径补足人力（38 个角色全部满足「人数 ≥ 单流程最大节点数 + 1」），闸保持开启。'
 WHERE config_key = 'approve.deny_serial_same_handler';

-- ② 单据完整性总闸规则（保留为什么不能直接开硬闸的技术原因）
UPDATE sys_config
   SET remark = '2026-10-15 过渡配置：总闸键名已对齐代码（gate.enabled）并实测生效；规则收敛到 SUBMIT 档（提单层只告警不阻断）⇒ 不拦任何单据。不可直接改成 TR_PAY:CONFIRM=contract_or_po 开硬闸：PayBatchService 调 assertComplete 时 modelKey/bizType 硬编码 TR_PAY、billsOfBatch 会把员工报销/借款单一起捞进来（那类单据天然无合同无 PO）⇒ 会拦死报销付款。开硬闸前须先修适用范围。'
 WHERE config_key = 'bill.completeness.rules';

-- ③ 提单侧硬阻断
UPDATE sys_config
   SET remark = '默认 0＝提单侧只打标进复核队列不挡流程，硬阻断改由付款指令执行前把关（PayBatchService.execute→paymentBlockReason）。置 1 即恢复旧的提单硬拦截口径。'
 WHERE config_key = 'fraud.block_on_submit.enabled';

-- ④ 反欺诈影子模式
UPDATE sys_config
   SET remark = '演练期置 1，只记录不阻断，用于观察误拦率；演练结束必须改回 0，否则付款前刚性拦截也失效。'
 WHERE config_key = 'fraud.shadow_mode.enabled';

-- ⑤ 超时自动通过：金额上限
UPDATE sys_config
   SET remark = '白名单节点还须单据金额 ≤ 本值才允许自动通过。初值 5000 元，业务复核后可在参数配置页直接改。'
 WHERE config_key = 'wf.timeout.autopass.max_amount';

-- ⑥ 超时自动通过：节点白名单
UPDATE sys_config
   SET remark = 'AUTO_PASS 走白名单制、默认关。四条硬门槛同时成立才放行（代码 WfTimeoutJob.autoPassDenial）：①在本白名单内 ②单据无金额敞口或低于 max_amount ③节点非作业执行节点 ④非会签/并联多实例节点。逐节点由业务确认后填入，宁缺毋滥。'
 WHERE config_key = 'wf.timeout.autopass.nodes';

-- ⑦ 校验输出
SELECT config_key, LEFT(remark, 60) AS remark_head
  FROM sys_config
 WHERE config_key IN ('approve.deny_serial_same_handler', 'bill.completeness.rules',
                      'fraud.block_on_submit.enabled', 'fraud.shadow_mode.enabled',
                      'wf.timeout.autopass.max_amount', 'wf.timeout.autopass.nodes')
 ORDER BY config_key;

-- 残留检查：应为 0 行
SELECT '残留内部编号（应为 0 行）' AS step, config_key, LEFT(remark, 40) AS remark_head
  FROM sys_config
 WHERE remark REGEXP '批次[0-9]|RA-F[0-9]|V5 册|本轮|上一版';

SELECT 'V1.6.14 完成：参数说明文字已订正（未改任何配置值）' AS result;
