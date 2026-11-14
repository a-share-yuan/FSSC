CREATE DATABASE  IF NOT EXISTS `fssc` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `fssc`;
-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: 192.168.20.77    Database: fssc
-- ------------------------------------------------------
-- Server version	8.0.46-0ubuntu0.22.04.5

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
-- Table structure for table `ar_claim`
--

DROP TABLE IF EXISTS `ar_claim`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ar_claim` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `bill_no` varchar(48) NOT NULL,
  `company_id` bigint DEFAULT NULL,
  `company_code` varchar(32) DEFAULT NULL COMMENT 'OU',
  `company_name` varchar(128) DEFAULT NULL,
  `claim_type` varchar(16) NOT NULL COMMENT 'CLAIM_IN/CLAIM_OUT/REVENUE_EST/THING_ADJ/AR_AP_OFFSET/OTHER_REV/OTHER_EXP/OTHER_OFFSET/ORDER_UNFRZ/NOTE_DISCOUNT',
  `customer_code` varchar(32) DEFAULT NULL,
  `customer_name` varchar(128) DEFAULT NULL,
  `ccy` varchar(8) DEFAULT 'CNY',
  `fx_rate` decimal(12,6) DEFAULT '1.000000',
  `amount` decimal(18,2) DEFAULT NULL,
  `cost_amount` decimal(18,2) DEFAULT NULL COMMENT '成本金额(向客户索赔)',
  `tax_rate` decimal(6,2) DEFAULT NULL,
  `headquarters_amount` decimal(18,2) DEFAULT NULL COMMENT '总部金额(原币)',
  `bu_amount` decimal(18,2) DEFAULT NULL COMMENT '事业部金额(跨账套折本位币,厂别00不生成)',
  `debit_account` varchar(64) DEFAULT NULL COMMENT '借方科目(附件五)',
  `credit_account` varchar(64) DEFAULT NULL,
  `tax_account` varchar(64) DEFAULT NULL,
  `debit_amount` decimal(18,2) DEFAULT NULL COMMENT '其它业务对冲借贷',
  `credit_amount` decimal(18,2) DEFAULT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `ekp_no` varchar(64) DEFAULT NULL,
  `factory_code` varchar(16) DEFAULT NULL COMMENT '厂别(附件四,00不做事业部凭证)',
  `order_no` varchar(64) DEFAULT NULL,
  `unfreeze_result` varchar(255) DEFAULT NULL,
  `reverse_flag` int DEFAULT '0' COMMENT '暂估冲回',
  `status` varchar(24) NOT NULL DEFAULT 'EKP_REVIEW' COMMENT 'EKP_REVIEW/PENDING_DISPATCH/DISPATCHED/POSTED/REJECTED/COMPLETED',
  `handler` varchar(64) DEFAULT NULL,
  `voucher_no` varchar(48) DEFAULT NULL,
  `lines_json` text,
  `attachment_ref` text,
  `remark` varchar(500) DEFAULT NULL,
  `dispatch_time` datetime DEFAULT NULL,
  `post_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT 'Flowable流程实例ID',
  `handler_pool` varchar(255) DEFAULT NULL COMMENT '选单池(逗号分隔,组内抢单)',
  `group_leader` varchar(64) DEFAULT NULL COMMENT 'AR组长(SLA升级)',
  `modify_note` varchar(500) DEFAULT NULL COMMENT '审核/派工留痕',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bill_no` (`tenant_id`,`bill_no`),
  KEY `idx_type_status` (`tenant_id`,`claim_type`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AR服务申请单(10类)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ar_claim`
--

LOCK TABLES `ar_claim` WRITE;
/*!40000 ALTER TABLE `ar_claim` DISABLE KEYS */;
/*!40000 ALTER TABLE `ar_claim` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:28:48
