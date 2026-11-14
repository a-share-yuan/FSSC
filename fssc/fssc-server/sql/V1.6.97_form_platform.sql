-- =============================================================================
-- V1.6.97 可配置表单平台 —— 单据类型注册 + 通用单据运行库
-- =============================================================================
-- 目标：客户自行配置任意审批流程与表单，全程不改代码。
--
-- 三道配置即可跑通一张新单据：
--   ① md_form_config        表单头（form_code / 流程模型 key / 菜单入口 / 存储方式）
--   ② md_form_field         字段（控件类型 / 栅格 / 必录 / 锁定 / 选项）
--   ③ md_form_validation    校验规则（必录 / 范围 / 正则 / 跨字段）
--   ④ wf_process_config     biz_type → 流程模型 key（流程路由，已有表）
--
-- 本件做两件事：
--   · md_form_config 补「单据类型注册」所需列，使 model_key 成为起流唯一依据
--     （原先是前端按 bill_type 硬编码路由、后端 switch 写死 5 种费用类型）
--   · 新建 biz_form_bill，以 form_data JSON 承载任意字段，不再受宽表列约束
--
-- 与既有财务单据的关系：
--   store_mode='EXPENSE_WIDE' 的表单仍走 exp_bill 宽表与 ExpenseBillService，本件不改其行为；
--   只有 store_mode='JSON' 的表单由通用运行时接管。
--
-- 回滚：V1.6.97_form_platform_rollback.sql
-- 可重放：全部 DDL 走 information_schema 判断
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ① md_form_config 补列
-- -----------------------------------------------------------------------------
-- biz_type：流程路由键，对应 wf_process_config.biz_type；为空时运行时回退 form_code
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE md_form_config ADD COLUMN biz_type VARCHAR(50) NULL COMMENT ''流程路由键 wf_process_config.biz_type，空则取 form_code''',
  'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'biz_type');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- store_mode：JSON=通用运行库存 form_data；EXPENSE_WIDE=沿用 exp_bill 宽表
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE md_form_config ADD COLUMN store_mode VARCHAR(16) NOT NULL DEFAULT ''JSON'' COMMENT ''存储方式 JSON 通用运行库 / EXPENSE_WIDE 费用宽表''',
  'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'store_mode');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 菜单入口：登记后由运行时写入 sys_menu，path 默认 /form/{form_code}
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE md_form_config ADD COLUMN menu_path VARCHAR(100) NULL COMMENT ''发起页路径，空则 /form/{form_code}''',
  'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'menu_path');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE md_form_config ADD COLUMN menu_name VARCHAR(100) NULL COMMENT ''侧栏显示名，空则取 form_name''',
  'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'menu_name');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE md_form_config ADD COLUMN menu_parent_id BIGINT NULL COMMENT ''挂在哪个菜单目录下''',
  'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'menu_parent_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- title_expr：取哪个字段当流程标题（字段 key），空则 form_name + 申请人姓名
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE md_form_config ADD COLUMN title_expr VARCHAR(200) NULL COMMENT ''流程标题取值字段 key，空则 form_name+申请人''',
  'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'title_expr');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE md_form_config ADD COLUMN allow_draft TINYINT NOT NULL DEFAULT 1 COMMENT ''是否允许存草稿''',
  'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'md_form_config' AND column_name = 'allow_draft');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 既有费用类表单统一标为宽表存储，保持原链路不变（重放安全：按 form_code 前缀）
UPDATE md_form_config
   SET store_mode = 'EXPENSE_WIDE'
 WHERE del_flag = 0
   AND form_code LIKE 'EXP\_%'
   AND (store_mode IS NULL OR store_mode = 'JSON' OR store_mode = '');

-- -----------------------------------------------------------------------------
-- ② 通用单据运行库
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_form_bill` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `form_code`     VARCHAR(50)  NOT NULL COMMENT '表单编码 md_form_config.form_code',
  `bill_no`       VARCHAR(64)  NULL COMMENT '单据编号（sys_number_rule 按 form_code 取号）',
  `title`         VARCHAR(200) NULL COMMENT '流程标题',
  `biz_type`      VARCHAR(50)  NULL COMMENT '起流时使用的 biz_type（快照，便于事后追溯）',
  `apply_user_id` BIGINT       NULL COMMENT '申请人',
  `apply_dept_id` BIGINT       NULL COMMENT '申请部门',
  `company_id`    BIGINT       NULL COMMENT '所属公司',
  `status`        VARCHAR(24)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED/CANCELED',
  `form_data`     JSON         NULL COMMENT '按 form_code 元数据渲染的字段键值',
  `proc_inst_id`  VARCHAR(64)  NULL COMMENT '流程实例ID',
  `current_node`  VARCHAR(200) NULL COMMENT '当前节点（冗余，便于列表展示）',
  `submit_time`   DATETIME     NULL COMMENT '提交时间',
  `finish_time`   DATETIME     NULL COMMENT '办结时间',
  `create_by`     BIGINT       NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`     BIGINT       NULL,
  `update_time`   DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_bfb_tenant_form` (`tenant_id`, `form_code`, `status`, `create_time`),
  KEY `idx_bfb_tenant_apply` (`tenant_id`, `apply_user_id`, `create_time`),
  KEY `idx_bfb_proc` (`proc_inst_id`),
  KEY `idx_bfb_billno` (`tenant_id`, `bill_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可配置表单通用单据（form_data JSON，新增表单不改表结构）';

-- -----------------------------------------------------------------------------
-- ③ 能力码：表单平台
-- -----------------------------------------------------------------------------
INSERT INTO pf_feature (feature_code, feature_name, module, sort, remark, create_time, del_flag)
SELECT 'FC_FORM_PLATFORM', '可配置表单平台', 'FORM', 95, '自定义单据类型、字段与校验规则，跟随流程配置生效', NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM pf_feature WHERE feature_code = 'FC_FORM_PLATFORM');

-- 三档能力集合都包含表单平台（档位差异只体现在业务域，不体现在配置能力）
INSERT INTO pf_package_feature (package_id, feature_code, enabled, create_time, del_flag)
SELECT p.id, 'FC_FORM_PLATFORM', 1, NOW(), 0
  FROM pf_package p
 WHERE p.package_code IN ('FC_BASIC', 'FC_STANDARD', 'FC_PRO')
   AND NOT EXISTS (SELECT 1 FROM pf_package_feature f
                    WHERE f.package_id = p.id AND f.feature_code = 'FC_FORM_PLATFORM');

-- -----------------------------------------------------------------------------
-- ④ 菜单
-- -----------------------------------------------------------------------------
-- 「表单设计」入口不在本脚本登记：该页面的组件尚未提供，先挂菜单会让用户点进
-- 一条不存在的路由（/form/:formCode 通配会把它当成 formCode=design 去查元数据并报错）。
-- 该入口随表单设计器页面一并启用，届时由「表单发布」流程统一写入 sys_menu。
--
-- 「我的申请」：员工自助入口，挂在流程中心下；不设权限码，数据面按申请人过滤
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon,
                      feature_code, terminal, sort, visible, status, create_time, del_flag)
SELECT 3550, '我的申请', 'C', '/form/my', 'form/FormBillList', NULL,
       'Tickets', 'FC_FORM_PLATFORM', 'PC', 90, 1, 1, NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/form/my');

-- 业务权限点：通用单据的查看/保存/提交（所有自定义表单共用同一组码，
-- 避免每加一张表单就要给每个角色重新授权一轮）
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon,
                      feature_code, terminal, sort, visible, status, create_time, del_flag)
SELECT 0, '通用单据保存', 'F', NULL, NULL, 'form:bill:save', NULL,
       'FC_FORM_PLATFORM', 'PC', 9001, 0, 1, NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'form:bill:save');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon,
                      feature_code, terminal, sort, visible, status, create_time, del_flag)
SELECT 0, '通用单据提交', 'F', NULL, NULL, 'form:bill:submit', NULL,
       'FC_FORM_PLATFORM', 'PC', 9002, 0, 1, NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'form:bill:submit');
