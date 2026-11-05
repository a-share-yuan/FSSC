-- =====================================================================
-- V1.6.24  2026-11-05  违规原因字典（只收「行为类」违规）
--
-- 设计取向：电子发票（数电票）时代，凡是系统能判的，都不该记成员工违规。
--   · 发票重复报销 —— PrecheckService 已硬拦截（INVOICE_DUP：票号被其他单据占用、
--     OCR 票号查重两道），单据根本提交不进去，不存在"违规"场景；
--   · 发票真伪/查验失败 —— verifyStatus + InvoiceAbnormalAlertJob 巡检；
--   · 发票已作废/红冲 —— 销项侧有 VOID/RED_FLUSHED 状态与 original_id 关联。
--   把这类问题计入信用扣分，等于让员工替系统缺陷背锅，所以本表**只收主观行为类**：
--   虚假业务、拆分规避审批、票面与业务不符、非发票类附件缺失、归属错误、超期报销。
--
--   纸质票时代的「粘贴单据规范」随实物粘贴一起废弃，不再保留。
--
-- 本增量：
--   ① 新建 md_violation_reason（违规原因字典，含扣分值）；
--   ② 预置 6 条行为类原因；
--   ③ op_quality 加 violation_reason_code，质检退回时按字典扣分（为空则沿用原 -20）。
--
-- 幂等：建表 IF NOT EXISTS、种子 NOT EXISTS、加列先查 information_schema。
-- 回滚：V1.6.24_violation_reason_rollback.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS `md_violation_reason` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID【多租户隔离字段】',
  `reason_code` varchar(32) NOT NULL COMMENT '原因码（唯一，如 VR_TRUTH）',
  `reason_name` varchar(64) NOT NULL COMMENT '原因名称（展示用）',
  `reason_category` varchar(32) DEFAULT NULL COMMENT '分类：TRUTH真实性/SPLIT拆分/DOC票据与业务/FORM填报规范',
  `deduct_points` int NOT NULL DEFAULT 0 COMMENT '扣分（正数，扣多少分）',
  `severity` varchar(16) DEFAULT 'LOW' COMMENT '严重度：LOW/MEDIUM/HIGH',
  `sort` int DEFAULT 0,
  `status` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/DISABLED',
  `remark` varchar(500) DEFAULT NULL COMMENT '说明（写清判定边界，避免被当成"发票问题的扣分口"）',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_violation_reason_code` (`tenant_id`, `reason_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='违规原因字典（仅行为类；发票真伪/重复/作废由预检拦截，不进信用扣分）';

-- ② 预置 6 条行为类原因
INSERT INTO `md_violation_reason`
  (`tenant_id`, `reason_code`, `reason_name`, `reason_category`, `deduct_points`, `severity`, `sort`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 'VR_OVERDUE', '超期报销', 'FORM', 5, 'LOW', 10, 'NORMAL',
       '超过制度规定的报销时限。数电票可长期留存，滞后报销更常见，但属流程纪律问题', NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `md_violation_reason` WHERE `tenant_id` = 1 AND `reason_code` = 'VR_OVERDUE');

INSERT INTO `md_violation_reason`
  (`tenant_id`, `reason_code`, `reason_name`, `reason_category`, `deduct_points`, `severity`, `sort`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 'VR_ACCOUNT', '费用归属错误', 'FORM', 5, 'MEDIUM', 20, 'NORMAL',
       '部门/项目/费用项错配，导致预算与核算口径失真（非恶意，可整改）', NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `md_violation_reason` WHERE `tenant_id` = 1 AND `reason_code` = 'VR_ACCOUNT');

INSERT INTO `md_violation_reason`
  (`tenant_id`, `reason_code`, `reason_name`, `reason_category`, `deduct_points`, `severity`, `sort`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 'VR_ATTACH', '非发票类附件缺失', 'DOC', 5, 'MEDIUM', 30, 'NORMAL',
       '发票已随票夹/OCR 进系统，缺的是行程单、审批单、情况说明等业务佐证材料', NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `md_violation_reason` WHERE `tenant_id` = 1 AND `reason_code` = 'VR_ATTACH');

INSERT INTO `md_violation_reason`
  (`tenant_id`, `reason_code`, `reason_name`, `reason_category`, `deduct_points`, `severity`, `sort`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 'VR_INV_MISMATCH', '票面与业务不符', 'DOC', 10, 'MEDIUM', 40, 'NORMAL',
       '发票开票内容与实际业务事项不一致（如办公用品票报餐饮）。真伪/重复由系统拦截，这一类只能人工判', NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `md_violation_reason` WHERE `tenant_id` = 1 AND `reason_code` = 'VR_INV_MISMATCH');

INSERT INTO `md_violation_reason`
  (`tenant_id`, `reason_code`, `reason_name`, `reason_category`, `deduct_points`, `severity`, `sort`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 'VR_SPLIT', '拆分报销规避审批', 'SPLIT', 20, 'HIGH', 50, 'NORMAL',
       '同一事项拆成多单绕过审批权限或预算控制。数电票可任意拆分，这是电子发票时代的高发风险', NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `md_violation_reason` WHERE `tenant_id` = 1 AND `reason_code` = 'VR_SPLIT');

INSERT INTO `md_violation_reason`
  (`tenant_id`, `reason_code`, `reason_name`, `reason_category`, `deduct_points`, `severity`, `sort`, `status`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT 1, 'VR_TRUTH', '虚假业务/虚构事项', 'TRUTH', 30, 'HIGH', 60, 'NORMAL',
       '无真实业务发生、伪造事项或虚增金额。最重档，直接降级并触发质检必检', NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `md_violation_reason` WHERE `tenant_id` = 1 AND `reason_code` = 'VR_TRUTH');

-- ③ 质检记录加违规原因码（为空时沿用原「质检不合格 -20」口径，向后兼容）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE()
                       AND table_name = 'op_quality'
                       AND column_name = 'violation_reason_code');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `op_quality` ADD COLUMN `violation_reason_code` varchar(32) DEFAULT NULL COMMENT ''违规原因码（md_violation_reason.reason_code）；有值则按字典扣分，为空沿用 -20''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 自检
SELECT `reason_code`, `reason_name`, `deduct_points`, `severity` FROM `md_violation_reason` ORDER BY `sort`;
SELECT COUNT(*) AS col_ok FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'op_quality' AND column_name = 'violation_reason_code';
