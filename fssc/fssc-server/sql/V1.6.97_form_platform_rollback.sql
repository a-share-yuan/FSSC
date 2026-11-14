-- =============================================================================
-- V1.6.97 回滚：撤销可配置表单平台的表结构与菜单/能力码
-- =============================================================================
-- 注意：回滚会删除 biz_form_bill（通用单据数据）。存量单据需先自行备份。

-- ① 菜单与权限点
-- 按 /form/ 前缀整体回收：运行期登记的客户表单菜单也挂在同一前缀下，
-- 逐条列举必然漏（客户配了几张只有库知道）。
DELETE FROM sys_role_menu
 WHERE menu_id IN (SELECT id FROM sys_menu
                    WHERE path LIKE '/form/%'
                       OR perms IN ('form:design:config', 'form:design:save', 'form:design:menu',
                                    'form:bill:save', 'form:bill:submit'));
DELETE FROM sys_menu
 WHERE path LIKE '/form/%'
    OR perms IN ('form:design:config', 'form:design:save', 'form:design:menu',
                 'form:bill:save', 'form:bill:submit');

-- ② 能力码
DELETE FROM pf_package_feature WHERE feature_code = 'FC_FORM_PLATFORM';
DELETE FROM pf_feature         WHERE feature_code = 'FC_FORM_PLATFORM';

-- ③ 运行库
DROP TABLE IF EXISTS `biz_form_bill`;

-- ④ md_form_config 补列（逐列判断，可重放）
SET @ddl := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE md_form_config DROP COLUMN biz_type', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'biz_type');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE md_form_config DROP COLUMN store_mode', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'store_mode');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE md_form_config DROP COLUMN menu_path', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'menu_path');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE md_form_config DROP COLUMN menu_name', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'menu_name');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE md_form_config DROP COLUMN menu_parent_id', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'menu_parent_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE md_form_config DROP COLUMN title_expr', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'title_expr');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE md_form_config DROP COLUMN allow_draft', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'allow_draft');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
