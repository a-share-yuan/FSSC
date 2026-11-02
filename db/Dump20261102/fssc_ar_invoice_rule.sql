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
-- Table structure for table `ar_invoice_rule`
--

DROP TABLE IF EXISTS `ar_invoice_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ar_invoice_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `customer_code` varchar(32) NOT NULL,
  `customer_name` varchar(128) DEFAULT NULL,
  `invoice_type` varchar(8) DEFAULT '专票',
  `limit_amount` decimal(18,2) DEFAULT '1000000.00' COMMENT '单张限额默认100万',
  `max_lines` int DEFAULT NULL COMMENT '销货清单受限如8行',
  `allow_summary` int DEFAULT '1',
  `split_dim` varchar(16) DEFAULT 'PO',
  `spec_rule` varchar(16) DEFAULT 'ORIGINAL',
  `remark_template` varchar(255) DEFAULT NULL,
  `need_biz_confirm` int DEFAULT '0' COMMENT '商务确认(受参数ar.bizconfirm.enabled控制)',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_customer` (`tenant_id`,`customer_code`)
) ENGINE=InnoDB AUTO_INCREMENT=700000011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户开票规则配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ar_invoice_rule`
--

LOCK TABLES `ar_invoice_rule` WRITE;
/*!40000 ALTER TABLE `ar_invoice_rule` DISABLE KEYS */;
INSERT INTO `ar_invoice_rule` VALUES (700000001,1,'C2101','环海重工股份有限公司','专票',1000000.00,10,1,'PO','ORIGINAL',NULL,1,'ENABLED','V1.4.81 造数开票规则','2026-01-01 09:00:00','2026-10-23 20:23:45'),(700000002,1,'C2102','恒瑞重工（江苏）有限公司','专票',1000000.00,20,1,'PO','ORIGINAL',NULL,0,'ENABLED','V1.4.81 造数开票规则','2026-01-01 09:00:00','2026-10-23 20:23:45'),(700000003,1,'C2103','蓝海船舶重工集团有限公司','专票',1000000.00,10,1,'PO','ORIGINAL',NULL,1,'ENABLED','V1.4.81 造数开票规则','2026-01-01 09:00:00','2026-10-23 20:23:45'),(700000004,1,'C2104','东海重工（集团）股份有限公司','专票',1000000.00,20,1,'PO','ORIGINAL',NULL,0,'ENABLED','V1.4.81 造数开票规则','2026-01-01 09:00:00','2026-10-23 20:23:45'),(700000005,1,'C2105','烟台精密铸造有限公司','专票',1000000.00,20,1,'PO','ORIGINAL',NULL,0,'ENABLED','V1.4.81 造数开票规则','2026-01-01 09:00:00','2026-10-23 20:23:45'),(700000006,1,'C2106','青岛海工科技有限公司','专票',1000000.00,8,1,'PO','ORIGINAL',NULL,1,'ENABLED','V1.4.81 造数开票规则','2026-01-01 09:00:00','2026-10-23 20:23:45'),(700000007,1,'C2107','蓬莱海工结构制造有限公司','专票',1000000.00,10,1,'PO','ORIGINAL',NULL,1,'ENABLED','V1.4.81 造数开票规则','2026-01-01 09:00:00','2026-10-23 20:23:45'),(700000008,1,'C2108','粤兴船舶机械（广东）有限公司','专票',1000000.00,8,1,'PO','ORIGINAL',NULL,0,'ENABLED','V1.4.81 造数开票规则','2026-01-01 09:00:00','2026-10-23 20:23:45'),(700000009,1,'C2109','苏莱钢结构有限公司','专票',1000000.00,8,1,'PO','ORIGINAL',NULL,0,'ENABLED','V1.4.81 造数开票规则','2026-01-01 09:00:00','2026-10-23 20:23:45'),(700000010,1,'C2110','迅达交通科技有限公司','专票',1000000.00,20,1,'PO','ORIGINAL',NULL,1,'ENABLED','V1.4.81 造数开票规则','2026-01-01 09:00:00','2026-10-23 20:23:45');
/*!40000 ALTER TABLE `ar_invoice_rule` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:14:08
