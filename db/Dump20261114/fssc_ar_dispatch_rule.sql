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
-- Table structure for table `ar_dispatch_rule`
--

DROP TABLE IF EXISTS `ar_dispatch_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ar_dispatch_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `rule_name` varchar(128) DEFAULT NULL,
  `claim_type` varchar(32) DEFAULT NULL COMMENT '鏈嶅姟鐢宠?绫诲瀷,绌?鍏ㄩ儴10绫',
  `customer_prefix` varchar(10) DEFAULT NULL COMMENT '瀹㈡埛鍗佷綅鐮佸墠缂?绌?鍏滃簳',
  `handlers` varchar(255) NOT NULL COMMENT '璐熻矗浜烘竻鍗?閫楀彿鍒嗛殧,澶氫汉=閫夊崟姹?',
  `group_leader` varchar(64) DEFAULT NULL COMMENT '缁勯暱(SLA鍗囩骇)',
  `auto_balance` int DEFAULT '0' COMMENT '1=鎸夊湪鍔為噺鏈?皯鑷?姩鐩存淳(鐢ㄥ弸璐熻浇鍧囪　),0=杩涢?鍗曟睜',
  `sla_hours` int DEFAULT NULL,
  `priority` int DEFAULT '100',
  `enabled` int DEFAULT '1',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_prefix` (`tenant_id`,`enabled`,`customer_prefix`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AR娲惧伐瑙勫垯(钃濆浘5.2,瀹㈡埛鍗佷綅鐮佽矾鐢?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ar_dispatch_rule`
--

LOCK TABLES `ar_dispatch_rule` WRITE;
/*!40000 ALTER TABLE `ar_dispatch_rule` DISABLE KEYS */;
INSERT INTO `ar_dispatch_rule` VALUES (1,1,'C0组-应收核算',NULL,'C0','Y000283','Y000281',0,24,10,1,NULL,'2026-09-16 08:04:00','2026-09-16 08:04:00');
/*!40000 ALTER TABLE `ar_dispatch_rule` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:28:53
