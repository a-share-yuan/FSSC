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
-- Table structure for table `gl_currency`
--

DROP TABLE IF EXISTS `gl_currency`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gl_currency` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `currency_code` varchar(8) NOT NULL COMMENT '币种代码:CNY/USD/EUR/JPY/HKD',
  `currency_name` varchar(50) NOT NULL COMMENT '币种名称',
  `symbol` varchar(10) DEFAULT NULL COMMENT '符号',
  `scale` int NOT NULL DEFAULT '2' COMMENT '小数位(JPY=0,CNY=2)',
  `rate_source` varchar(64) DEFAULT NULL COMMENT '汇率来源:sys_config ar.fx.{ccy}/HMDA/手工',
  `is_base` tinyint NOT NULL DEFAULT '0' COMMENT '是否本位币:1-是',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gcur_code` (`tenant_id`,`currency_code`),
  KEY `idx_gcur_base` (`tenant_id`,`is_base`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='币种与本位币登记';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gl_currency`
--

LOCK TABLES `gl_currency` WRITE;
/*!40000 ALTER TABLE `gl_currency` DISABLE KEYS */;
INSERT INTO `gl_currency` VALUES (6,1,'CNY','人民币','¥',2,'本位币',1,'ENABLED',NULL,NULL,'2026-10-18 11:23:07',NULL,NULL,0),(7,1,'USD','美元','$',2,'sys_config ar.fx.USD',0,'ENABLED',NULL,NULL,'2026-10-18 11:23:07',NULL,NULL,0),(8,1,'HKD','港币','HK$',2,'sys_config ar.fx.HKD',0,'ENABLED',NULL,NULL,'2026-10-18 11:23:07',NULL,NULL,0),(9,1,'JPY','日元','¥',0,'sys_config ar.fx.JPY',0,'ENABLED',NULL,NULL,'2026-10-18 11:23:07',NULL,NULL,0),(10,1,'EUR','欧元','€',2,'sys_config ar.fx.EUR',0,'ENABLED',NULL,NULL,'2026-10-18 11:23:07',NULL,NULL,0);
/*!40000 ALTER TABLE `gl_currency` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:30:05
