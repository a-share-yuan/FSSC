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
-- Table structure for table `rc_bad_debt`
--

DROP TABLE IF EXISTS `rc_bad_debt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rc_bad_debt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `customer_id` bigint DEFAULT NULL COMMENT '往来单位',
  `receivable_id` bigint DEFAULT NULL COMMENT '关联应收单id',
  `amount` decimal(20,2) DEFAULT '0.00' COMMENT '计提金额',
  `provision_date` date DEFAULT NULL COMMENT '计提日期',
  `provision_rate` decimal(10,4) DEFAULT '0.0000' COMMENT '计提比例',
  `period` varchar(10) DEFAULT NULL COMMENT '期间(yyyy-MM 或 yyyyMM)',
  `status` varchar(20) DEFAULT 'PROVISIONED' COMMENT 'PROVISIONED已计提/REVERSED已转回',
  `writeoff_amount` decimal(18,2) DEFAULT NULL COMMENT '坏账核销金额(≤已计提余额)',
  `writeoff_date` date DEFAULT NULL COMMENT '核销日期',
  `writeoff_reason` varchar(500) DEFAULT NULL COMMENT '核销原因',
  `basis_no` varchar(64) DEFAULT NULL COMMENT '坏账认定依据编号(破产说明等附件)',
  `writeoff_voucher_no` varchar(48) DEFAULT NULL COMMENT '坏账核销凭证号',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除0否/1是',
  `company_id` bigint DEFAULT NULL COMMENT '所属公司(多分子公司审批路由 V1.3.17)',
  PRIMARY KEY (`id`),
  KEY `idx_rc_bad_debt_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='坏账计提(OTC-P0-6)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rc_bad_debt`
--

LOCK TABLES `rc_bad_debt` WRITE;
/*!40000 ALTER TABLE `rc_bad_debt` DISABLE KEYS */;
/*!40000 ALTER TABLE `rc_bad_debt` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:29:03
