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
-- Table structure for table `md_cash_flow`
--

DROP TABLE IF EXISTS `md_cash_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_cash_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `cash_code` varchar(50) NOT NULL,
  `cash_name` varchar(100) NOT NULL,
  `direction` varchar(10) NOT NULL DEFAULT 'IN',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cash_flow` (`tenant_id`,`cash_code`)
) ENGINE=InnoDB AUTO_INCREMENT=75 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='现金流量码';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_cash_flow`
--

LOCK TABLES `md_cash_flow` WRITE;
/*!40000 ALTER TABLE `md_cash_flow` DISABLE KEYS */;
INSERT INTO `md_cash_flow` VALUES (67,1,'JF01','购买商品接收劳务支付的现金','OUT','NORMAL',NULL,NULL,'2026-08-27 07:35:44',NULL,'2026-08-27 07:35:44',0),(68,1,'JF02','经营租赁所支付的现金','OUT','NORMAL',NULL,NULL,'2026-08-27 07:35:44',NULL,'2026-08-27 07:35:44',0),(69,1,'JF03','支付给职工以及为职工支付的现金','OUT','NORMAL',NULL,NULL,'2026-08-27 07:35:44',NULL,'2026-08-27 07:35:44',0),(70,1,'JF04','支付的增值税款','OUT','NORMAL',NULL,NULL,'2026-08-27 07:35:44',NULL,'2026-08-27 07:35:44',0),(71,1,'JF05','支付的所得税款','OUT','NORMAL',NULL,NULL,'2026-08-27 07:35:44',NULL,'2026-08-27 07:35:44',0),(72,1,'JF06','支付的除增值税，所得税以外的其他税','OUT','NORMAL',NULL,NULL,'2026-08-27 07:35:44',NULL,'2026-08-27 07:35:44',0),(73,1,'JF07','支付的其他与经营活动有关的现金','OUT','NORMAL',NULL,NULL,'2026-08-27 07:35:44',NULL,'2026-08-27 07:35:44',0),(74,1,'JS05','收到的其他与经营活动有关的现金','IN','NORMAL',NULL,NULL,'2026-08-27 07:35:44',NULL,'2026-08-27 07:35:44',0);
/*!40000 ALTER TABLE `md_cash_flow` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:05:46
