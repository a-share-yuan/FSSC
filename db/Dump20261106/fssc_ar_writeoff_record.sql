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
-- Table structure for table `ar_writeoff_record`
--

DROP TABLE IF EXISTS `ar_writeoff_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ar_writeoff_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `record_no` varchar(48) NOT NULL COMMENT '核销记录号',
  `writeoff_type` varchar(16) NOT NULL DEFAULT 'RECEIPT' COMMENT 'RECEIPT收款核销/NOTE票据核销/OFFSET对冲核销',
  `fund_id` bigint DEFAULT NULL,
  `fund_no` varchar(48) DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `customer_name` varchar(128) DEFAULT NULL,
  `invoice_nos` varchar(500) DEFAULT NULL COMMENT '核销应收单号(逗号分隔)',
  `writeoff_amount` decimal(18,2) NOT NULL COMMENT '核销金额',
  `auto_flag` int DEFAULT '0' COMMENT '1=自动核销(FIFO)',
  `receipt_no` varchar(48) DEFAULT NULL COMMENT '生成的收款核销单号(rc_receipt.bill_no)',
  `voucher_no` varchar(48) DEFAULT NULL COMMENT '凭证号',
  `status` varchar(16) NOT NULL DEFAULT 'DONE' COMMENT 'PENDING/DONE/FAILED',
  `fail_reason` varchar(500) DEFAULT NULL,
  `operator` varchar(64) DEFAULT NULL,
  `note` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_no` (`tenant_id`,`record_no`),
  KEY `idx_customer` (`tenant_id`,`customer_id`),
  KEY `idx_type_status` (`tenant_id`,`writeoff_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='核销记录(核销工作台/核销查询)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ar_writeoff_record`
--

LOCK TABLES `ar_writeoff_record` WRITE;
/*!40000 ALTER TABLE `ar_writeoff_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `ar_writeoff_record` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:22
