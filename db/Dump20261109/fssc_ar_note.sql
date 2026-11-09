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
-- Table structure for table `ar_note`
--

DROP TABLE IF EXISTS `ar_note`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ar_note` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `note_no` varchar(64) NOT NULL COMMENT '票据编号',
  `note_type` varchar(16) NOT NULL DEFAULT 'ELECTRONIC' COMMENT 'ELECTRONIC电子票据/PAPER纸质票据',
  `drawer` varchar(128) DEFAULT NULL COMMENT '出票人',
  `acceptor` varchar(128) DEFAULT NULL COMMENT '承兑人',
  `customer_code` varchar(32) DEFAULT NULL COMMENT '客户编码(收票来源)',
  `customer_name` varchar(128) DEFAULT NULL,
  `ccy` varchar(8) DEFAULT 'CNY',
  `amount` decimal(18,2) NOT NULL COMMENT '票据金额',
  `issue_date` date DEFAULT NULL COMMENT '出票日',
  `due_date` date NOT NULL COMMENT '到期日',
  `image_ref` varchar(255) DEFAULT NULL COMMENT '影像扫描引用(纸质必填)',
  `transferee` varchar(128) DEFAULT NULL COMMENT '被背书人(背书转让)',
  `discount_rate` decimal(8,4) DEFAULT NULL COMMENT '贴现利率%(年)',
  `discount_date` date DEFAULT NULL COMMENT '贴现日期',
  `discount_days` int DEFAULT NULL COMMENT '贴现天数(贴现日→到期日)',
  `discount_interest` decimal(18,2) DEFAULT NULL COMMENT '贴现利息(单独记账)',
  `discount_amount` decimal(18,2) DEFAULT NULL COMMENT '贴现净额(入账)',
  `voucher_no` varchar(48) DEFAULT NULL COMMENT '入账凭证号',
  `discount_voucher_no` varchar(48) DEFAULT NULL COMMENT '贴现凭证号',
  `collect_voucher_no` varchar(48) DEFAULT NULL COMMENT '托收入账凭证号',
  `status` varchar(24) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED/IN_ACCOUNT/CUSTODY/ENDORSED/DISCOUNTED/COLLECTING/COMPLETED/VOIDED',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_note_no` (`tenant_id`,`note_no`),
  KEY `idx_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应收票据登记(蓝图SCC_AR票据管理9.5)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ar_note`
--

LOCK TABLES `ar_note` WRITE;
/*!40000 ALTER TABLE `ar_note` DISABLE KEYS */;
/*!40000 ALTER TABLE `ar_note` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:24:06
