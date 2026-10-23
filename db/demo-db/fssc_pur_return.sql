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
-- Table structure for table `pur_return`
--

DROP TABLE IF EXISTS `pur_return`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pur_return` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `bill_no` varchar(48) NOT NULL COMMENT '采购退货单号(CT前缀,sys_number_rule)',
  `company_id` bigint DEFAULT NULL,
  `company_code` varchar(32) DEFAULT NULL COMMENT '公司代码',
  `company_name` varchar(128) DEFAULT NULL,
  `vendor_code` varchar(48) NOT NULL COMMENT '供应商代码(与bcp_partner.partner_code对齐)',
  `vendor_name` varchar(128) DEFAULT NULL,
  `ccy` varchar(8) DEFAULT 'CNY',
  `bill_date` date NOT NULL COMMENT '业务日期(退货日期,对账receiptDate)',
  `amount` decimal(18,2) NOT NULL COMMENT '含税退货总额(恒正数,对账接口按负数返回)',
  `tax_amount` decimal(18,2) DEFAULT '0.00' COMMENT '税额合计',
  `return_mode` varchar(16) NOT NULL DEFAULT 'REFUND' COMMENT 'REFUND退货退款(进对账)/EXCHANGE退料补货(不进对账)',
  `source_doc_type` varchar(32) DEFAULT NULL COMMENT '源单类型:PO_RECEIPT采购入库单',
  `source_doc_id` bigint DEFAULT NULL COMMENT '源单id(po_receipt.id)',
  `source_bill_no` varchar(48) DEFAULT NULL COMMENT '源入库单号(对账明细追溯用)',
  `return_reason` varchar(200) DEFAULT NULL COMMENT '退货原因:质量不合格/破损/错发/多发/合同取消/其他',
  `line_count` int DEFAULT '0',
  `status` varchar(24) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/REJECTED/VOIDED',
  `approver` varchar(64) DEFAULT NULL,
  `approve_time` datetime DEFAULT NULL,
  `void_reason` varchar(255) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `del_flag` int DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bill_no` (`tenant_id`,`bill_no`),
  KEY `idx_partner` (`tenant_id`,`company_id`,`vendor_code`,`status`),
  KEY `idx_source` (`tenant_id`,`source_doc_type`,`source_doc_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购退货单(AP退货冲减应付)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pur_return`
--

LOCK TABLES `pur_return` WRITE;
/*!40000 ALTER TABLE `pur_return` DISABLE KEYS */;
/*!40000 ALTER TABLE `pur_return` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:15:26
