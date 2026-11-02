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
-- Table structure for table `tax_input_certify`
--

DROP TABLE IF EXISTS `tax_input_certify`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tax_input_certify` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `invoice_id` bigint DEFAULT NULL COMMENT '关联发票ID（op_invoice.id）',
  `invoice_no` varchar(64) DEFAULT NULL COMMENT '发票号码',
  `invoice_code` varchar(32) DEFAULT NULL COMMENT '发票代码',
  `invoice_type` varchar(32) DEFAULT NULL COMMENT '专票/普通发票/电子发票/农产品收购',
  `supplier_name` varchar(128) DEFAULT NULL COMMENT '销方名称',
  `supplier_tax_no` varchar(32) DEFAULT NULL COMMENT '销方税号',
  `invoice_date` date DEFAULT NULL COMMENT '开票日期',
  `invoice_amount` decimal(18,2) DEFAULT NULL COMMENT '发票金额（不含税）',
  `tax_amount` decimal(18,2) DEFAULT NULL COMMENT '税额',
  `total_amount` decimal(18,2) DEFAULT NULL COMMENT '价税合计',
  `certify_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待认证/CERTIFIED已认证/DEDUCTED已抵扣/TRANSFERRED已转出/REJECTED认证失败',
  `certify_date` date DEFAULT NULL COMMENT '认证日期',
  `certify_period` varchar(6) DEFAULT NULL COMMENT '认证属期 yyyyMM',
  `deduct_period` varchar(6) DEFAULT NULL COMMENT '抵扣属期 yyyyMM',
  `transfer_reason` varchar(256) DEFAULT NULL COMMENT '转出原因（进项转出）',
  `risk_flag` tinyint NOT NULL DEFAULT '0' COMMENT '风险标识：0=正常 1=疑点发票',
  `source_doc_type` varchar(32) DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_certify_status` (`tenant_id`,`certify_status`),
  KEY `idx_invoice_no` (`invoice_no`),
  KEY `idx_certify_period` (`certify_period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='进项认证台账（逐张发票认证状态跟踪）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tax_input_certify`
--

LOCK TABLES `tax_input_certify` WRITE;
/*!40000 ALTER TABLE `tax_input_certify` DISABLE KEYS */;
/*!40000 ALTER TABLE `tax_input_certify` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:13:36
