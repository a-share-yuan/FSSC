CREATE DATABASE  IF NOT EXISTS `fssc` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `fssc`;
-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: fssc
-- ------------------------------------------------------
-- Server version	8.4.11

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `op_voucher_template`
--

DROP TABLE IF EXISTS `op_voucher_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_voucher_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `template_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'EXPENSE/LOAN/PAY/REPAY/CONTRACT/ADVANCE',
  `voucher_word` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '记/收/付/转',
  `summary_template` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '摘要模板，支持{billTitle}变量',
  `debit_subject_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `debit_subject_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `credit_subject_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `credit_subject_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `aux_dept` tinyint DEFAULT '0',
  `aux_project` tinyint DEFAULT '0',
  `aux_supplier` tinyint DEFAULT '0',
  `amount_min` decimal(18,2) DEFAULT NULL,
  `amount_max` decimal(18,2) DEFAULT NULL,
  `priority` int DEFAULT '100',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'NORMAL' COMMENT 'NORMAL/DISABLED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_ovt_tenant_source` (`tenant_id`,`source_type`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='凭证模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_voucher_template`
--

LOCK TABLES `op_voucher_template` WRITE;
/*!40000 ALTER TABLE `op_voucher_template` DISABLE KEYS */;
INSERT INTO `op_voucher_template` VALUES (1,NULL,'TPL_PURCHASE','采购入库凭证模板','PURCHASE','JOURNAL','采购入库-{billTitle}','1405','库存商品','2202','应付账款',0,0,1,0.00,NULL,100,'NORMAL','P0-2 自动化凭证引擎',NULL,'2026-09-06 12:35:03',NULL,NULL,0),(2,NULL,'TPL_RECEIVABLE','应收确认凭证模板','RECEIVABLE','JOURNAL','应收确认-{billTitle}','1131','应收账款','6001','主营业务收入',0,0,0,0.00,NULL,100,'NORMAL','P0-2 自动化凭证引擎',NULL,'2026-09-06 12:35:03',NULL,NULL,0),(3,NULL,'TPL_RECEIVABLE_COLLECT','应收收款凭证模板','RECEIVABLE_COLLECT','RECEIPT','应收收款-{billTitle}','1002','银行存款','1131','应收账款',0,0,0,0.00,NULL,100,'NORMAL','P0-2 自动化凭证引擎',NULL,'2026-09-06 12:35:03',NULL,NULL,0),(4,NULL,'TPL_TREASURY_PAY','出纳付款凭证模板','TREASURY_PAY','PAYMENT','出纳付款-{billTitle}','2202','应付账款','1002','银行存款',0,0,1,0.00,NULL,100,'NORMAL','P0-2 自动化凭证引擎',NULL,'2026-09-06 12:35:03',NULL,NULL,0),(5,NULL,'TPL_BANK_RECON','银行对账差异凭证模板','BANK_RECON','JOURNAL','银行对账差异-{billTitle}','6603','财务费用','1002','银行存款',0,0,0,0.00,NULL,100,'NORMAL','P0-2 自动化凭证引擎',NULL,'2026-09-06 12:35:03',NULL,NULL,0),(6,NULL,'TPL_ASSET_DEPRECIATION','资产折旧凭证模板','ASSET_DEPRECIATION','JOURNAL','月度折旧计提-{billTitle}','6602','管理费用','1602','累计折旧',0,0,0,0.00,NULL,100,'NORMAL','P0-2 自动化凭证引擎',NULL,'2026-09-06 12:35:03',NULL,NULL,0),(7,NULL,'TPL_CONTRACT','合同凭证模板','CONTRACT','JOURNAL','合同确认-{billTitle}','1122','预付账款','2202','应付账款',0,0,1,0.00,NULL,100,'NORMAL','P0-2 自动化凭证引擎(激活原零调用方)',NULL,'2026-09-06 12:35:03',NULL,NULL,0),(8,1,'TPL_LOAN','员工借款凭证模板','EXP_LOAN','JOURNAL','借款-{billTitle}','1221','其他应收款','1002','银行存款',1,0,0,0.00,NULL,100,'NORMAL','缺陷E修复:借款审批通过自动制单(对照NC借款单付款凭证)','3799','2026-09-18 08:29:35',NULL,NULL,0),(9,1,'TPL_REPAY','员工还款凭证模板','EXP_REPAY','JOURNAL','还款-{billTitle}','1002','银行存款','1221','其他应收款',1,0,0,0.00,NULL,100,'NORMAL','缺陷E修复:还款审批通过自动制单(对照NC还款收款凭证)','3799','2026-09-18 08:29:35',NULL,NULL,0),(10,1,'W14UNN7-TPL#10','波次十四模板改名','EXP_REPAY','JOURNAL','还款-{billTitle}','1002','银行存款','1221','其他应收款',1,0,0,0.00,NULL,100,'NORMAL','缺陷E修复:还款审批通过自动制单(对照NC还款收款凭证)','3799','2026-09-18 08:29:35','3834','2026-10-08 01:01:58',1),(11,1,'W14UNN7-TPL#11','波次十四重建-W14UNN7','EXP_REPAY','JOURNAL','还款-{billTitle}','1002','银行存款','1221','其他应收款',1,0,0,0.00,NULL,100,'NORMAL','缺陷E修复:还款审批通过自动制单(对照NC还款收款凭证)','3799','2026-09-18 08:29:35','3834','2026-10-08 01:01:58',1),(12,1,'W14B6RO-TPL#12','波次十四模板B改名','EXP_REPAY','JOURNAL','还款-{billTitle}','1002','银行存款','1221','其他应收款',1,0,0,0.00,NULL,100,'NORMAL','缺陷E修复:还款审批通过自动制单(对照NC还款收款凭证)','3799','2026-09-18 08:29:35','3834','2026-10-08 01:11:43',1),(13,1,'W14B6RO-TPL#13','波次十四模板B重建','EXP_REPAY','JOURNAL','还款-{billTitle}','1002','银行存款','1221','其他应收款',1,0,0,0.00,NULL,100,'NORMAL','缺陷E修复:还款审批通过自动制单(对照NC还款收款凭证)','3799','2026-09-18 08:29:35','3834','2026-10-08 01:11:43',1);
/*!40000 ALTER TABLE `op_voucher_template` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:15:48
