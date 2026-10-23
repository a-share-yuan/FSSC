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
-- Table structure for table `ar_pre_invoice`
--

DROP TABLE IF EXISTS `ar_pre_invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ar_pre_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `pre_invoice_no` varchar(64) NOT NULL COMMENT '=对账单号+三位流水(R-31)',
  `company_id` bigint DEFAULT NULL,
  `recon_bill_no` varchar(48) NOT NULL,
  `customer_code` varchar(32) DEFAULT NULL,
  `customer_name` varchar(128) DEFAULT NULL,
  `invoice_type` varchar(8) DEFAULT '专票' COMMENT '专票/普票',
  `ccy` varchar(8) DEFAULT 'CNY',
  `amount` decimal(18,2) DEFAULT NULL,
  `tax_amount` decimal(18,2) DEFAULT NULL,
  `total_amount` decimal(18,2) DEFAULT NULL,
  `line_count` int DEFAULT '0',
  `lines_json` text COMMENT '发票明细',
  `split_dim` varchar(16) DEFAULT 'PO',
  `status` varchar(16) NOT NULL DEFAULT 'TO_INVOICE' COMMENT 'TO_INVOICE/BIZ_CONFIRMING/PUSHED/INVOICED/VOIDED/RED_INVOICED',
  `invoice_no` varchar(64) DEFAULT NULL COMMENT '金税回写',
  `invoice_code` varchar(64) DEFAULT NULL,
  `invoice_date` date DEFAULT NULL,
  `red_notice_no` varchar(64) DEFAULT NULL,
  `origin_no` varchar(64) DEFAULT NULL COMMENT '换票原票',
  `printed_flag` int DEFAULT '0',
  `pre_check_flag` varchar(8) DEFAULT 'NONE' COMMENT '提前开票核对NONE/PENDING/DONE',
  `pre_check_note` varchar(255) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `red_pending` int DEFAULT '0' COMMENT '1=EBS璐熸暟鍙戠エ宸叉帹閫佸緟琛ョ孩瀛楅?鐭ュ崟鍙',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pre_no` (`tenant_id`,`pre_invoice_no`),
  KEY `idx_recon` (`tenant_id`,`recon_bill_no`)
) ENGINE=InnoDB AUTO_INCREMENT=700000321 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预制发票';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ar_pre_invoice`
--

LOCK TABLES `ar_pre_invoice` WRITE;
/*!40000 ALTER TABLE `ar_pre_invoice` DISABLE KEYS */;
/*!40000 ALTER TABLE `ar_pre_invoice` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:15:38
