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
-- Table structure for table `supplier_recon_diff`
--

DROP TABLE IF EXISTS `supplier_recon_diff`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `supplier_recon_diff` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `recon_id` bigint NOT NULL COMMENT '所属对账单ID',
  `diff_type` varchar(30) NOT NULL COMMENT '差异类型: AMOUNT_DIFF/MISSING_INVOICE/MISSING_PAYMENT/TIMING_DIFF/OTHER',
  `our_amount` decimal(20,2) DEFAULT '0.00' COMMENT '我方金额',
  `supplier_amount` decimal(20,2) DEFAULT '0.00' COMMENT '供应商金额',
  `diff_amount` decimal(20,2) DEFAULT '0.00' COMMENT '差异金额',
  `reason` varchar(500) DEFAULT NULL COMMENT '差异原因',
  `adjustment_type` varchar(30) DEFAULT 'NONE' COMMENT '调整方式: NONE/ADJUST_INVOICE/ADJUST_PAYMENT/WRITE_OFF',
  `adjustment_voucher_no` varchar(64) DEFAULT NULL COMMENT '调整凭证号',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态: PENDING/RESOLVED',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recon_id` (`recon_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商对账差异调节表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `supplier_recon_diff`
--

LOCK TABLES `supplier_recon_diff` WRITE;
/*!40000 ALTER TABLE `supplier_recon_diff` DISABLE KEYS */;
/*!40000 ALTER TABLE `supplier_recon_diff` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:06
