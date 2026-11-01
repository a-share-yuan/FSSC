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
-- Table structure for table `md_loan_pay_type`
--

DROP TABLE IF EXISTS `md_loan_pay_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_loan_pay_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `type_code` varchar(64) NOT NULL,
  `type_name` varchar(128) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `uk_lpt` (`tenant_id`,`type_code`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='借款付款类型';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_loan_pay_type`
--

LOCK TABLES `md_loan_pay_type` WRITE;
/*!40000 ALTER TABLE `md_loan_pay_type` DISABLE KEYS */;
INSERT INTO `md_loan_pay_type` VALUES (3,1,'Acceptance','承兑/Acceptance','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(4,1,'Bill','汇票/Bill','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(5,1,'CASH/Acceptance/Bill','现汇/承兑汇票/财票/Cash/Acceptance/Bill','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(6,1,'Cash','现金/Cash','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(7,1,'Cheque','支票/Cheque','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(8,1,'Other','其他/Other','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(9,1,'T/T','电汇/T/T','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(10,1,'01','网银支付','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(11,1,'W16LP01#11','波次十六留痕取证借款付款类型-改','NORMAL',NULL,3897,'2026-10-08 04:03:43',3897,'2026-10-08 04:03:43',1);
/*!40000 ALTER TABLE `md_loan_pay_type` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:06:21
