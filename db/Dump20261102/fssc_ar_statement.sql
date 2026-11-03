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
-- Table structure for table `ar_statement`
--

DROP TABLE IF EXISTS `ar_statement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ar_statement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `bill_no` varchar(48) NOT NULL,
  `company_id` bigint DEFAULT NULL,
  `customer_code` varchar(32) NOT NULL COMMENT '客户编码(十位码,必填)',
  `customer_name` varchar(128) DEFAULT NULL,
  `bill_type` varchar(16) DEFAULT 'DOMESTIC' COMMENT 'DOMESTIC/SHIP_NORMAL/OTHER_BIZ/OTHER_INCOME/OVERSEAS',
  `ccy` varchar(8) DEFAULT 'CNY',
  `fx_rate` decimal(12,6) DEFAULT NULL COMMENT '国税局当期汇率(sys_config ar.fx.{ccy})',
  `amount` decimal(18,2) DEFAULT NULL,
  `tax_amount` decimal(18,2) DEFAULT NULL,
  `total_amount` decimal(18,2) DEFAULT NULL,
  `rmb_amount` decimal(18,2) DEFAULT NULL COMMENT '折本位币',
  `rmb_tax_amount` decimal(18,2) DEFAULT NULL,
  `pre_invoice_flag` int DEFAULT '0' COMMENT '提前开票1是',
  `check_status` varchar(16) DEFAULT 'UNCHECKED',
  `diff_reason` varchar(500) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'IMPORTED' COMMENT 'IMPORTED/RECONCILED/ABNORMAL',
  `lines_json` text COMMENT '对账明细',
  `import_user` varchar(64) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `check_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bill_no` (`tenant_id`,`bill_no`),
  KEY `idx_customer` (`tenant_id`,`customer_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户对账单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ar_statement`
--

LOCK TABLES `ar_statement` WRITE;
/*!40000 ALTER TABLE `ar_statement` DISABLE KEYS */;
/*!40000 ALTER TABLE `ar_statement` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:28
