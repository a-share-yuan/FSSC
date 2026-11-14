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
