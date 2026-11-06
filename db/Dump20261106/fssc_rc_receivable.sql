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
-- Table structure for table `rc_receivable`
--

DROP TABLE IF EXISTS `rc_receivable`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rc_receivable` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `bill_no` varchar(64) DEFAULT NULL COMMENT '单据号',
  `customer_id` bigint DEFAULT NULL COMMENT '往来单位(crm_customer.id)',
  `bill_date` date DEFAULT NULL COMMENT '单据日期',
  `order_id` bigint DEFAULT NULL COMMENT '来源订单id',
  `batch_no` varchar(64) DEFAULT NULL COMMENT '批次号',
  `currency_code` varchar(16) DEFAULT 'CNY' COMMENT '币种',
  `amount` decimal(20,2) DEFAULT '0.00' COMMENT '应收金额',
  `received_amount` decimal(20,2) DEFAULT '0.00' COMMENT '已收金额',
  `remain_amount` decimal(20,2) DEFAULT '0.00' COMMENT '应收余额=应收-已收',
  `due_date` date DEFAULT NULL COMMENT '到期日',
  `status` varchar(20) DEFAULT 'NONE' COMMENT 'NONE部分/DONE结清/OVERDUE逾期',
  `period` varchar(10) DEFAULT NULL COMMENT '期间(yyyy-MM 或 yyyyMM)',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除0否/1是',
  `company_id` bigint DEFAULT NULL COMMENT '所属公司(多分子公司审批路由 V1.3.17)',
  `source_doc_type` varchar(32) DEFAULT NULL COMMENT '来源单据类型:SALES_ORDER销售订单/MANUAL手工创建',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据ID（source_doc_type 对应表的主键）',
  PRIMARY KEY (`id`),
  KEY `idx_rc_receivable_tenant` (`tenant_id`),
  KEY `idx_rc_receivable_customer` (`customer_id`),
  KEY `idx_tenant_due` (`tenant_id`,`due_date`,`status`),
  KEY `idx_tenant_owner` (`tenant_id`,`create_by`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应收单(OTC-P0-6)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rc_receivable`
--

LOCK TABLES `rc_receivable` WRITE;
/*!40000 ALTER TABLE `rc_receivable` DISABLE KEYS */;
/*!40000 ALTER TABLE `rc_receivable` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:40
