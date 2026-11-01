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
-- Table structure for table `exp_bill_receipt`
--

DROP TABLE IF EXISTS `exp_bill_receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exp_bill_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `bill_id` bigint NOT NULL,
  `line_no` int NOT NULL DEFAULT '1',
  `payer_type` varchar(20) NOT NULL DEFAULT 'EMPLOYEE',
  `payer_id` bigint DEFAULT NULL,
  `payer_name` varchar(200) NOT NULL,
  `bank_name` varchar(200) DEFAULT NULL,
  `bank_account` varchar(64) DEFAULT NULL,
  `receive_method` varchar(20) NOT NULL DEFAULT 'BANK',
  `receive_account_id` bigint DEFAULT NULL,
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY',
  `amount` decimal(20,2) NOT NULL DEFAULT '0.00',
  `receipt_status` varchar(20) DEFAULT NULL,
  `receipt_time` datetime DEFAULT NULL,
  `receipt_serial_no` varchar(100) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `proc_inst_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ebr_bill` (`tenant_id`,`bill_id`,`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单据收款事实（还款/退回等资金流入侧载体）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exp_bill_receipt`
--

LOCK TABLES `exp_bill_receipt` WRITE;
/*!40000 ALTER TABLE `exp_bill_receipt` DISABLE KEYS */;
/*!40000 ALTER TABLE `exp_bill_receipt` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:06:04
