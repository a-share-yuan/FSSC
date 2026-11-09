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
-- Table structure for table `ar_writeoff_fund`
--

DROP TABLE IF EXISTS `ar_writeoff_fund`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ar_writeoff_fund` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `fund_no` varchar(48) NOT NULL COMMENT '资金单号(收款单号/票据编号/对冲单号)',
  `fund_type` varchar(16) NOT NULL DEFAULT 'RECEIPT' COMMENT 'RECEIPT收款/NOTE票据/OFFSET对冲虚拟收付',
  `note_id` bigint DEFAULT NULL COMMENT '票据核销来源(ar_note.id)',
  `customer_id` bigint DEFAULT NULL COMMENT '往来单位(crm_customer.id)',
  `customer_name` varchar(128) DEFAULT NULL,
  `ccy` varchar(8) DEFAULT 'CNY',
  `fund_amount` decimal(18,2) NOT NULL COMMENT '资金总额',
  `remain_amount` decimal(18,2) NOT NULL COMMENT '未核销余额',
  `fund_date` date DEFAULT NULL,
  `bank_serial` varchar(64) DEFAULT NULL COMMENT '银行流水号',
  `agreement_no` varchar(64) DEFAULT NULL COMMENT '债务抵消协议编号(对冲核销)',
  `status` varchar(16) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE可用/EXHAUSTED已用完/VOIDED已作废',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_no` (`tenant_id`,`fund_no`),
  KEY `idx_customer` (`tenant_id`,`customer_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='核销资金池(核销工作台-收款/票据匹配)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ar_writeoff_fund`
--

LOCK TABLES `ar_writeoff_fund` WRITE;
/*!40000 ALTER TABLE `ar_writeoff_fund` DISABLE KEYS */;
/*!40000 ALTER TABLE `ar_writeoff_fund` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:23:27
