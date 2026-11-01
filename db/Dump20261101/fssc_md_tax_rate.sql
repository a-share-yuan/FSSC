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
-- Table structure for table `md_tax_rate`
--

DROP TABLE IF EXISTS `md_tax_rate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_tax_rate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `rate_code` varchar(64) NOT NULL COMMENT '税率编码',
  `rate_name` varchar(100) NOT NULL COMMENT '税率名称,如 增值税6%',
  `rate_value` decimal(6,4) NOT NULL COMMENT '税率值,如 0.0600',
  `tax_type` varchar(20) NOT NULL DEFAULT 'VAT' COMMENT '税种:VAT增值税/OTHER其他',
  `deductible` tinyint NOT NULL DEFAULT '1' COMMENT '是否可抵扣:0否 1是',
  `sort` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT '状态:NORMAL/DISABLED',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_rate_code` (`tenant_id`,`rate_code`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='税率';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_tax_rate`
--

LOCK TABLES `md_tax_rate` WRITE;
/*!40000 ALTER TABLE `md_tax_rate` DISABLE KEYS */;
INSERT INTO `md_tax_rate` VALUES (7,1,'T13','13%增值税',0.1300,'VAT',1,1,'NORMAL',NULL,'2026-09-08 01:00:55',NULL,'2026-09-08 01:00:55',0),(8,1,'T9','9%增值税',0.0900,'VAT',1,2,'NORMAL',NULL,'2026-09-08 01:00:55',NULL,'2026-09-08 01:00:55',0),(9,1,'T6','6%增值税',0.0600,'VAT',1,3,'NORMAL',NULL,'2026-09-08 01:00:55',NULL,'2026-09-08 01:00:55',0),(10,1,'T3','3%征收率',0.0300,'VAT',1,4,'NORMAL',NULL,'2026-09-08 01:00:55',NULL,'2026-09-08 01:00:55',0),(11,1,'T1','1%征收率',0.0100,'VAT',1,5,'NORMAL',NULL,'2026-09-08 01:00:55',NULL,'2026-09-08 01:00:55',0),(12,1,'T0','免税',0.0000,'VAT',0,6,'NORMAL',NULL,'2026-09-08 01:00:55',NULL,'2026-09-08 01:00:55',0);
/*!40000 ALTER TABLE `md_tax_rate` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:05:41
