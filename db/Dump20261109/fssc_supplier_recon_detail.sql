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
-- Table structure for table `supplier_recon_detail`
--

DROP TABLE IF EXISTS `supplier_recon_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `supplier_recon_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `recon_id` bigint NOT NULL COMMENT '所属对账单ID',
  `item_type` varchar(20) NOT NULL COMMENT '明细类型: INVOICE/PAYMENT/ADJUSTMENT',
  `item_id` bigint DEFAULT NULL COMMENT '原单ID(发票ID/付款ID)',
  `item_no` varchar(64) DEFAULT NULL COMMENT '原单编号(发票号/付款流水号)',
  `item_date` date DEFAULT NULL COMMENT '原单日期',
  `amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '金额',
  `matched_detail_id` bigint DEFAULT NULL COMMENT '勾对对手明细ID',
  `match_status` varchar(20) NOT NULL DEFAULT 'UNMATCHED' COMMENT '勾对状态: UNMATCHED/MATCHED/MANUAL',
  `match_type` varchar(20) DEFAULT NULL COMMENT '勾对方式: AUTO/MANUAL',
  `match_rule` varchar(50) DEFAULT NULL COMMENT '匹配规则: INVOICE_NO/AMOUNT/MANUAL',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recon_id` (`recon_id`),
  KEY `idx_item_type` (`item_type`),
  KEY `idx_match_status` (`match_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商对账单逐笔明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `supplier_recon_detail`
--

LOCK TABLES `supplier_recon_detail` WRITE;
/*!40000 ALTER TABLE `supplier_recon_detail` DISABLE KEYS */;
/*!40000 ALTER TABLE `supplier_recon_detail` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:23:44
