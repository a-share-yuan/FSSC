-- =====================================================
-- V1.0.5 实物流签收功能增强
-- 1. op_physical_delivery 新增快递/条码/拒签字段
-- 2. 新建 op_barcode 条码管理表
-- =====================================================

-- 1. op_physical_delivery 增加快递信息、条码、拒签字段
ALTER TABLE `op_physical_delivery`
  ADD COLUMN `express_company` varchar(50) DEFAULT NULL COMMENT '快递公司' AFTER `remark`,
  ADD COLUMN `express_no` varchar(100) DEFAULT NULL COMMENT '运单号' AFTER `express_company`,
  ADD COLUMN `barcode` varchar(100) DEFAULT NULL COMMENT '条码值（条码枪扫描）' AFTER `express_no`,
  ADD COLUMN `package_count` int DEFAULT 1 COMMENT '件数' AFTER `barcode`,
  ADD COLUMN `reject_reason` varchar(500) DEFAULT NULL COMMENT '拒签原因' AFTER `package_count`,
  ADD COLUMN `sign_remark` varchar(500) DEFAULT NULL COMMENT '签收备注' AFTER `reject_reason`;

-- 扩展 status 枚举值说明（DDL 层不约束，应用层控制）
-- PENDING -> SIGNED | REJECTED

-- 2. 新建条码管理表
CREATE TABLE IF NOT EXISTS `op_barcode` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `barcode` varchar(100) NOT NULL COMMENT '条码值（一维码/二维码内容）',
  `barcode_type` varchar(20) NOT NULL DEFAULT 'CODE128' COMMENT '条码类型: CODE128/QRCODE',
  `biz_type` varchar(20) NOT NULL COMMENT '业务类型: BILL单据/ARCHIVE档案/BOX档案盒',
  `ref_id` bigint DEFAULT NULL COMMENT '关联业务ID（单据ID/档案ID/档案盒ID）',
  `ref_no` varchar(50) DEFAULT NULL COMMENT '关联业务编号',
  `print_by` bigint DEFAULT NULL COMMENT '打印人',
  `print_name` varchar(50) DEFAULT NULL COMMENT '打印人姓名',
  `print_time` datetime DEFAULT NULL COMMENT '打印时间',
  `scan_by` bigint DEFAULT NULL COMMENT '扫描人',
  `scan_name` varchar(50) DEFAULT NULL COMMENT '扫描人姓名',
  `scan_time` datetime DEFAULT NULL COMMENT '扫描时间',
  `status` varchar(20) NOT NULL DEFAULT 'GENERATED' COMMENT '状态: GENERATED已生成/PRINTED已打印/SCANNED已扫描/SHELVED已上架',
  `location` varchar(100) DEFAULT NULL COMMENT '上架位置',
  `shelve_time` datetime DEFAULT NULL COMMENT '上架时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_barcode_tenant` (`tenant_id`, `barcode`),
  KEY `idx_barcode_ref` (`tenant_id`, `biz_type`, `ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='条码管理';
