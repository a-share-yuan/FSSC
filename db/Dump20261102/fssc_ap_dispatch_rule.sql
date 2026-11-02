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
-- Table structure for table `ap_dispatch_rule`
--

DROP TABLE IF EXISTS `ap_dispatch_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ap_dispatch_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `rule_name` varchar(128) DEFAULT NULL,
  `req_type` varchar(32) DEFAULT NULL COMMENT '空=全部类型',
  `payment_way` varchar(24) NOT NULL COMMENT '付款方法(派工维度)',
  `handlers` varchar(255) NOT NULL COMMENT '负责人清单(逗号分隔,多人=选单池R-02)',
  `group_leader` varchar(64) DEFAULT NULL COMMENT 'AP组长(R-03转审)',
  `company_code` varchar(32) DEFAULT NULL,
  `sla_hours` int DEFAULT NULL COMMENT 'SLA时限(小时)',
  `enabled` int DEFAULT '1',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AP付款派工规则(R-01~R-05)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ap_dispatch_rule`
--

LOCK TABLES `ap_dispatch_rule` WRITE;
/*!40000 ALTER TABLE `ap_dispatch_rule` DISABLE KEYS */;
INSERT INTO `ap_dispatch_rule` VALUES (1,1,'银行转账类派工',NULL,'银行转账','100110,330017','100110',NULL,NULL,1,'2026-10-23 修复：原 ap-k1,ap-k2/ap-leader 账号不存在，改为应付会计 100110,330017','2026-09-14 01:36:43','2026-10-23 16:47:34'),(2,1,'冒烟-银行转账',NULL,'银行转账','Y000283','Y000281',NULL,24,0,'RBAC smoke；2026-10-23 停用：负责人 Y000283/Y000281 不存在','2026-09-16 00:55:08','2026-10-23 08:29:46'),(3,1,'冒烟-支票派工',NULL,'支票','330017',NULL,NULL,NULL,0,NULL,'2026-10-23 11:23:13','2026-10-23 11:23:44');
/*!40000 ALTER TABLE `ap_dispatch_rule` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:13:28
