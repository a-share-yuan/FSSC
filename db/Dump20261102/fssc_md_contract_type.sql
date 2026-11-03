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
-- Table structure for table `md_contract_type`
--

DROP TABLE IF EXISTS `md_contract_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_contract_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `type_code` varchar(64) NOT NULL,
  `type_name` varchar(128) NOT NULL,
  `is_po` varchar(16) NOT NULL DEFAULT 'NONPO' COMMENT 'PO 采购订单 / NONPO 费用类',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `uk_ct` (`tenant_id`,`type_code`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同类型';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_contract_type`
--

LOCK TABLES `md_contract_type` WRITE;
/*!40000 ALTER TABLE `md_contract_type` DISABLE KEYS */;
INSERT INTO `md_contract_type` VALUES (1,1,'PURCHASE','采购合同(PO)','PO','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(2,1,'ASSET','资产类合同(PO)','PO','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(3,1,'OUTSOURCE','外包类合同(PO)','PO','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(4,1,'SERVICE','服务合同(非PO)','NONPO','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(5,1,'FRAMEWORK','框架合同(非PO)','NONPO','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(6,1,'MAINT','维保合同(非PO)','NONPO','NORMAL',NULL,1,'2026-08-12 17:43:28',1,'2026-08-12 17:43:28',0),(7,1,'W16CTA01#7','波次十六留痕取证类型-改','PO','NORMAL',NULL,3831,'2026-10-08 03:49:11',3831,'2026-10-08 03:49:11',1);
/*!40000 ALTER TABLE `md_contract_type` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:24
