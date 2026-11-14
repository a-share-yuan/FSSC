CREATE DATABASE  IF NOT EXISTS `fssc` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `fssc`;
-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: 192.168.20.77    Database: fssc
-- ------------------------------------------------------
-- Server version	8.0.46-0ubuntu0.22.04.5

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
-- Table structure for table `match_tolerance`
--

DROP TABLE IF EXISTS `match_tolerance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `match_tolerance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `company_id` bigint NOT NULL COMMENT '所属公司(sys_company.id)，按公司配置容差',
  `amount_pct` decimal(10,4) NOT NULL DEFAULT '0.5000' COMMENT '金额容差百分比(%)：0.5=0.5%',
  `amount_abs` decimal(18,2) NOT NULL DEFAULT '100.00' COMMENT '金额容差绝对值(元)：100=100元',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL启用/DISABLED停用(停用按默认参数处理)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_tol_company` (`tenant_id`,`company_id`),
  KEY `idx_match_tol_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=700000007 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='三单匹配容差参数(DE-06，公司级，收货vs发票)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `match_tolerance`
--

LOCK TABLES `match_tolerance` WRITE;
/*!40000 ALTER TABLE `match_tolerance` DISABLE KEYS */;
INSERT INTO `match_tolerance` VALUES (700000001,1,1,0.5000,100.00,'NORMAL','2026-01-01 09:00:00',NULL),(700000002,1,31,0.5000,100.00,'NORMAL','2026-01-01 09:00:00',NULL),(700000003,1,32,0.5000,100.00,'NORMAL','2026-01-01 09:00:00',NULL),(700000004,1,33,0.5000,100.00,'NORMAL','2026-01-01 09:00:00',NULL),(700000005,1,34,0.5000,100.00,'NORMAL','2026-01-01 09:00:00',NULL),(700000006,1,35,0.5000,100.00,'NORMAL','2026-01-01 09:00:00',NULL);
/*!40000 ALTER TABLE `match_tolerance` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:28:31
