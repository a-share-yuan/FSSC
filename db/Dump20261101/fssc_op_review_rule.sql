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
-- Table structure for table `op_review_rule`
--

DROP TABLE IF EXISTS `op_review_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_review_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `rule_code` varchar(64) NOT NULL,
  `rule_name` varchar(200) NOT NULL,
  `rule_category` varchar(32) DEFAULT NULL,
  `match_expression` varchar(500) DEFAULT NULL,
  `action` varchar(32) DEFAULT NULL,
  `priority` int DEFAULT '100',
  `risk_level` varchar(16) DEFAULT NULL,
  `pass_rate` decimal(8,4) DEFAULT NULL,
  `status` varchar(16) DEFAULT 'NORMAL',
  `remark` varchar(500) DEFAULT NULL,
  `rule_level` varchar(16) DEFAULT NULL,
  `rule_order` int DEFAULT NULL,
  `script_content` text,
  `input_params` varchar(1000) DEFAULT NULL,
  `output_message` varchar(500) DEFAULT NULL,
  `related_expense_types` varchar(500) DEFAULT NULL,
  `related_org_types` varchar(500) DEFAULT NULL,
  `is_parallel` tinyint DEFAULT '0',
  `timeout_ms` int DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_review_rule`
--

LOCK TABLES `op_review_rule` WRITE;
/*!40000 ALTER TABLE `op_review_rule` DISABLE KEYS */;
INSERT INTO `op_review_rule` VALUES (1,1,'RR_AMOUNT_HIGH','金额超5万自动驳回','AMOUNT','amount > 50000','AUTO_REJECT',1,'HIGH',NULL,'DISABLED','单据金额超过5万元自动驳回（仅费用报销类；借款/还款/付款等资金类单据豁免，2026-09-09 UI实测修复）；2026-09-12 符合性整改停用：与 Visio EER04-03 金额分档设计冲突（>5万/10万/50万 应走基地总经理/财务总监/总裁分级审批，不应一刀切自动驳回）',NULL,NULL,NULL,NULL,NULL,'CLAIM,CLAIM_TRAVEL,TRAVEL,TRAIN,PREPAY,SETTLE,APPLY',NULL,0,NULL,1,'2026-09-06 15:42:22',NULL,'2026-10-08 01:11:43',0),(2,1,'RR_AMOUNT_LOW','金额低于500自动通过','AMOUNT','amount < 500','AUTO_PASS',2,'LOW',NULL,'NORMAL','单据金额低于500元自动通过',NULL,NULL,NULL,NULL,NULL,'CLAIM,CLAIM_TRAVEL,TRAVEL,TRAIN,PREPAY,SETTLE,APPLY',NULL,0,NULL,1,'2026-09-06 15:42:22',NULL,'2026-09-09 08:56:15',0),(3,1,'RR_DEFAULT','默认人工审核','DEFAULT','','MANUAL_REVIEW',99,'LOW',NULL,'NORMAL','默认人工审核',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,1,'2026-09-06 15:42:22',NULL,'2026-09-06 15:42:22',0),(4,1,'PRE-INV-DUPLICATE','发票重复报销拦截','INVOICE','invoiceNumber != null','REJECT',10,'HIGH',NULL,'NORMAL','同票号OCR记录>1次即拦截(BUG-06修复)','BILL',10,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,'2026-09-21 09:44:38',NULL,'2026-09-21 09:59:35',0),(5,1,'W14UNN7-REV','波次十四审单-W14UNN7','AMOUNT','amount > 50000','AUTO_REJECT',1,'HIGH',NULL,'DISABLED','单据金额超过5万元自动驳回（仅费用报销类；借款/还款/付款等资金类单据豁免，2026-09-09 UI实测修复）；2026-09-12 符合性整改停用：与 Visio EER04-03 金额分档设计冲突（>5万/10万/50万 应走基地总经理/财务总监/总裁分级审批，不应一刀切自动驳回）',NULL,NULL,NULL,NULL,NULL,'CLAIM,CLAIM_TRAVEL,TRAVEL,TRAIN,PREPAY,SETTLE,APPLY',NULL,0,NULL,NULL,'2026-10-08 01:01:58',NULL,'2026-10-27 12:40:00',0),(7,1,'W14B6RO-RULE','波次十四预检B重建','AMOUNT','amount > 50000','AUTO_REJECT',1,'HIGH',NULL,'DISABLED','单据金额超过5万元自动驳回（仅费用报销类；借款/还款/付款等资金类单据豁免，2026-09-09 UI实测修复）；2026-09-12 符合性整改停用：与 Visio EER04-03 金额分档设计冲突（>5万/10万/50万 应走基地总经理/财务总监/总裁分级审批，不应一刀切自动驳回）',NULL,0,NULL,NULL,NULL,'CLAIM,CLAIM_TRAVEL,TRAVEL,TRAIN,PREPAY,SETTLE,APPLY',NULL,0,NULL,NULL,'2026-09-06 15:42:22',NULL,'2026-10-08 01:01:58',0),(8,1,'W14B6RO-REV','波次十四审单B','AMOUNT','amount > 50000','AUTO_REJECT',1,'HIGH',NULL,'DISABLED','单据金额超过5万元自动驳回（仅费用报销类；借款/还款/付款等资金类单据豁免，2026-09-09 UI实测修复）；2026-09-12 符合性整改停用：与 Visio EER04-03 金额分档设计冲突（>5万/10万/50万 应走基地总经理/财务总监/总裁分级审批，不应一刀切自动驳回）',NULL,NULL,NULL,NULL,NULL,'CLAIM,CLAIM_TRAVEL,TRAVEL,TRAIN,PREPAY,SETTLE,APPLY',NULL,0,NULL,NULL,'2026-10-08 01:11:43',NULL,'2026-10-27 12:40:02',0);
/*!40000 ALTER TABLE `op_review_rule` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:05:57
