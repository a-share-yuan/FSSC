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
-- Table structure for table `cont_contract`
--

DROP TABLE IF EXISTS `cont_contract`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cont_contract` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` int DEFAULT '0',
  `contract_no` varchar(64) DEFAULT NULL,
  `contract_name` varchar(200) DEFAULT NULL,
  `contract_type` varchar(32) DEFAULT NULL,
  `party_a_name` varchar(200) DEFAULT NULL,
  `party_b_name` varchar(200) DEFAULT NULL,
  `amount` decimal(18,2) DEFAULT NULL,
  `currency_code` varchar(16) DEFAULT NULL,
  `sign_date` date DEFAULT NULL,
  `begin_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `status` varchar(32) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL COMMENT '关联客商(crm_customer)',
  `company_id` bigint DEFAULT NULL COMMENT '签订公司',
  `dept_id` bigint DEFAULT NULL COMMENT '费用归属部门',
  `item_id` bigint DEFAULT NULL COMMENT '费用类型ID',
  `item_name` varchar(100) DEFAULT NULL,
  `no_fixed_amount` tinyint DEFAULT '0' COMMENT '无固定金额(长期协议/以量定额) 1=是',
  `contract_class` varchar(20) DEFAULT NULL COMMENT '合同大类',
  `price_decrease` tinyint DEFAULT '0' COMMENT '是否同比价格递减',
  `bid_compare` tinyint DEFAULT '0' COMMENT '是否招标比价',
  `bid_process` varchar(500) DEFAULT NULL COMMENT '招标比价过程',
  `bid_reason` varchar(200) DEFAULT NULL COMMENT '中标原因',
  `bid_note` varchar(500) DEFAULT NULL COMMENT '中标说明',
  `source` varchar(20) DEFAULT 'MANUAL' COMMENT '来源 MANUAL手动/IFS同步/IMPORT导入',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cont_contract`
--

LOCK TABLES `cont_contract` WRITE;
/*!40000 ALTER TABLE `cont_contract` DISABLE KEYS */;
/*!40000 ALTER TABLE `cont_contract` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:15:32
