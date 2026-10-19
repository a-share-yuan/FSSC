-- =====================================================
-- V1.0.6 外部接口统一配置
-- 1. 新建 ext_interface_config 表：发票验真/发票认证/OCR/AI模型等外部接口集中管理
-- 2. 预置种子数据（OCR/验真/认证/AI模型各一条默认配置）
-- =====================================================

CREATE TABLE IF NOT EXISTS `ext_interface_config` (
  `id`              bigint       NOT NULL AUTO_INCREMENT,
  `tenant_id`       bigint       NOT NULL COMMENT '租户ID',
  `category`        varchar(30)  NOT NULL COMMENT '接口分类: INVOICE_VERIFY发票验真/INVOICE_CERTIFY发票认证/OCR影像识别/AI_MODEL AI模型',
  `interface_name`  varchar(100) NOT NULL COMMENT '接口名称（如：百度发票验真）',
  `interface_code`  varchar(50)  NOT NULL COMMENT '接口编码（租户内唯一）',
  `provider`        varchar(50)  DEFAULT NULL COMMENT '服务商: BAIDU/TENCENT/ALIYUN/OPENAI/QIANFAN/CUSTOM',
  `api_url`         varchar(500) DEFAULT NULL COMMENT '接口请求地址',
  `api_key`         varchar(500) DEFAULT NULL COMMENT 'API Key / AppKey',
  `api_secret`      varchar(500) DEFAULT NULL COMMENT 'API Secret / SecretKey',
  `model_name`      varchar(100) DEFAULT NULL COMMENT '模型名称（AI模型专用，如 ERNIE-4.0-Turbo-8K）',
  `extra_config`    text         DEFAULT NULL COMMENT '附加配置JSON（如字段映射、超时、重试策略等）',
  `status`          varchar(20)  NOT NULL DEFAULT 'DISABLED' COMMENT 'ENABLED启用/DISABLED停用',
  `is_default`      int          NOT NULL DEFAULT 0 COMMENT '1=该分类下默认接口（同分类仅一个）',
  `remark`          varchar(500) DEFAULT NULL COMMENT '备注',
  `last_test_time`  datetime     DEFAULT NULL COMMENT '最后一次连接测试时间',
  `last_test_result` varchar(20) DEFAULT NULL COMMENT '最后一次测试结果: SUCCESS/FAIL',
  `last_test_msg`   varchar(500) DEFAULT NULL COMMENT '最后一次测试消息',
  `create_by`       varchar(50)  DEFAULT NULL,
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`       varchar(50)  DEFAULT NULL,
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`        int          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ext_iface_code` (`tenant_id`, `interface_code`, `del_flag`),
  KEY `idx_ext_iface_category` (`tenant_id`, `category`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部接口统一配置';

-- 种子数据：为 DEMO1 租户(tenant_id=1)预置 4 类默认配置（均为 DISABLED，管理员在页面启用并填写真实参数）
INSERT INTO `ext_interface_config` (`tenant_id`, `category`, `interface_name`, `interface_code`, `provider`, `api_url`, `model_name`, `extra_config`, `status`, `is_default`, `remark`)
VALUES
  (1, 'OCR',            'OCR影像识别通道',    'DEFAULT_OCR',       'CUSTOM', NULL, NULL, '{"channel":"mock"}', 'DISABLED', 1, '默认OCR通道，启用后在页面配置真实API地址与Token'),
  (1, 'INVOICE_VERIFY', '发票验真接口',       'DEFAULT_INVOICE_VERIFY', 'CUSTOM', NULL, NULL, '{}', 'DISABLED', 1, '发票验真接口，启用后定时验真任务将调用此接口'),
  (1, 'INVOICE_CERTIFY','发票认证勾选接口',   'DEFAULT_INVOICE_CERTIFY','CUSTOM', NULL, NULL, '{}', 'DISABLED', 1, '发票进项认证勾选接口，启用后认证操作将调用此接口'),
  (1, 'AI_MODEL',       'AI大模型接口',       'DEFAULT_AI_MODEL',  'QIANFAN', NULL, 'ERNIE-4.0-Turbo-8K', '{}', 'DISABLED', 1, 'AI大模型接口，用于智能审单/费用分类等AI增强功能');

-- 菜单注册（sys_menu 无 tenant_id 列，parent_id=3032 为"系统配置"目录）
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `sort`, `path`, `component`, `perms`, `menu_type`, `icon`, `visible`, `status`, `del_flag`)
VALUES ('外部接口配置', 3032, 13, 'system/ext-interface', 'system/ext-interface/index', 'system:extiface:list', 'C', 'Connection', 1, 1, 0);

-- 权限码（按钮级）
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `sort`, `path`, `component`, `perms`, `menu_type`, `icon`, `visible`, `status`, `del_flag`)
SELECT '接口查询', id, 1, '', '', 'system:extiface:query', 'F', '#', 1, 1, 0 FROM sys_menu WHERE perms='system:extiface:list' AND del_flag=0 LIMIT 1;
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `sort`, `path`, `component`, `perms`, `menu_type`, `icon`, `visible`, `status`, `del_flag`)
SELECT '接口新增', id, 2, '', '', 'system:extiface:create', 'F', '#', 1, 1, 0 FROM sys_menu WHERE perms='system:extiface:list' AND del_flag=0 LIMIT 1;
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `sort`, `path`, `component`, `perms`, `menu_type`, `icon`, `visible`, `status`, `del_flag`)
SELECT '接口修改', id, 3, '', '', 'system:extiface:update', 'F', '#', 1, 1, 0 FROM sys_menu WHERE perms='system:extiface:list' AND del_flag=0 LIMIT 1;
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `sort`, `path`, `component`, `perms`, `menu_type`, `icon`, `visible`, `status`, `del_flag`)
SELECT '接口删除', id, 4, '', '', 'system:extiface:delete', 'F', '#', 1, 1, 0 FROM sys_menu WHERE perms='system:extiface:list' AND del_flag=0 LIMIT 1;
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `sort`, `path`, `component`, `perms`, `menu_type`, `icon`, `visible`, `status`, `del_flag`)
SELECT '连接测试', id, 5, '', '', 'system:extiface:test', 'F', '#', 1, 1, 0 FROM sys_menu WHERE perms='system:extiface:list' AND del_flag=0 LIMIT 1;

-- 分配给 admin 角色(role_id=1, tenant_id=1)
INSERT INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT 1, 1, id FROM sys_menu WHERE perms LIKE 'system:extiface:%' AND del_flag=0;
