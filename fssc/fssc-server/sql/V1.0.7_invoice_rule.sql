-- =====================================================
-- V1.0.7 发票智能匹配规则
-- 报销发起时按发票「货物或应税劳务、服务名称」命中费用类型自动预填；
-- 命中才改，未命中时费用类型留空待填。
-- =====================================================

CREATE TABLE IF NOT EXISTS `md_invoice_rule` (
  `id`              bigint       NOT NULL AUTO_INCREMENT,
  `tenant_id`       bigint       NOT NULL COMMENT '租户ID',
  `keyword`         varchar(255) NOT NULL COMMENT '商品/服务名称关键词或词串（多个用 / 分隔，如：打车/出租/滴滴）',
  `match_type`      varchar(20)  NOT NULL DEFAULT 'CONTAINS' COMMENT '匹配方式: CONTAINS包含/EXACT精确',
  `expense_item_id` bigint       DEFAULT NULL COMMENT '命中后预填的费用类型 id (md_expense_item.id)',
  `item_name`       varchar(100) DEFAULT NULL COMMENT '费用类型名称（冗余展示）',
  `sort`            int          NOT NULL DEFAULT 0 COMMENT '排序：越小越优先',
  `status`          varchar(20)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL启用/DISABLED停用',
  `remark`          varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by`       bigint       DEFAULT NULL,
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`       bigint       DEFAULT NULL,
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`        int          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_inv_rule_sort` (`tenant_id`, `status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发票智能匹配规则';

-- 示例规则（已绑定真实费用项 id：403=国内出差-住宿费 405=国内出差-市内交通费 401=国内出差-长途交通费 392=快递费，可在“发票智能匹配规则”页面调整）
INSERT INTO `md_invoice_rule` (`tenant_id`, `keyword`, `match_type`, `expense_item_id`, `item_name`, `sort`, `status`, `remark`) VALUES
  (1, '住宿/酒店/房费',      'CONTAINS', 403, '国内出差-住宿费', 1, 'NORMAL', '绑定到 差旅费-国内出差-住宿费'),
  (1, '打车/出租/滴滴',       'CONTAINS', 405, '国内出差-市内交通费', 2, 'NORMAL', '绑定到 差旅费-国内出差-市内交通费'),
  (1, '高铁/火车/动车',       'CONTAINS', 401, '国内出差-长途交通费', 3, 'NORMAL', '绑定到 差旅费-国内出差-长途交通费'),
  (1, '顺丰快递/快递',        'CONTAINS', 392, '快递费', 4, 'NORMAL', '绑定到 行政办公费-快递费');

-- 菜单注册（parent_id=3032 为"系统配置"目录，与外部接口配置同级；路径必须带前导斜杠，否则 el-menu 相对当前路由解析）
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `sort`, `path`, `component`, `perms`, `menu_type`, `icon`, `visible`, `status`, `del_flag`)
SELECT '发票智能匹配规则', 3032, 14, '/master/invoice-rule', 'master/InvoiceRule', 'md:invoiceRule:list', 'C', 'Link', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='md:invoiceRule:list' AND del_flag=0);

-- 权限码（按钮级）
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `sort`, `path`, `component`, `perms`, `menu_type`, `icon`, `visible`, `status`, `del_flag`)
SELECT '规则新增', id, 1, '', '', 'md:invoiceRule:create', 'F', '#', 1, 1, 0 FROM sys_menu WHERE perms='md:invoiceRule:list' AND del_flag=0 LIMIT 1;
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `sort`, `path`, `component`, `perms`, `menu_type`, `icon`, `visible`, `status`, `del_flag`)
SELECT '规则修改', id, 2, '', '', 'md:invoiceRule:update', 'F', '#', 1, 1, 0 FROM sys_menu WHERE perms='md:invoiceRule:list' AND del_flag=0 LIMIT 1;
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `sort`, `path`, `component`, `perms`, `menu_type`, `icon`, `visible`, `status`, `del_flag`)
SELECT '规则删除', id, 3, '', '', 'md:invoiceRule:delete', 'F', '#', 1, 1, 0 FROM sys_menu WHERE perms='md:invoiceRule:list' AND del_flag=0 LIMIT 1;

-- 分配给 admin 角色(role_id=1, tenant_id=1)
INSERT INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT 1, 1, id FROM sys_menu WHERE perms LIKE 'md:invoiceRule:%' AND del_flag=0;