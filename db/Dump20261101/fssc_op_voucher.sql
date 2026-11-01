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
-- Table structure for table `op_voucher`
--

DROP TABLE IF EXISTS `op_voucher`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_voucher` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `voucher_no` varchar(64) NOT NULL COMMENT '凭证号(按凭证字+年月+序号)',
  `voucher_word` varchar(20) NOT NULL DEFAULT 'JOURNAL' COMMENT '凭证字:JOURNAL记/RECEIPT收/PAYMENT付/TRANSFER转',
  `company_id` bigint NOT NULL COMMENT '记账公司ID',
  `period_year` int NOT NULL COMMENT '会计年度',
  `period_month` int NOT NULL COMMENT '会计月份',
  `voucher_date` date NOT NULL COMMENT '凭证日期',
  `attach_count` int NOT NULL DEFAULT '0' COMMENT '附件张数',
  `bill_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_type` varchar(32) NOT NULL DEFAULT 'EXPENSE' COMMENT '来源类型:EXPENSE费用/EXPENSE_PAY费用付款/PURCHASE采购入库/RECEIVABLE应收确认/RECEIVABLE_COLLECT应收收款/TREASURY_PAY出纳付款/BANK_RECON银行对账/ASSET_DEPRECIATION资产折旧/CONTRACT合同',
  `summary` varchar(500) DEFAULT NULL COMMENT '凭证摘要',
  `debit_total` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '借方合计',
  `credit_total` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '贷方合计',
  `is_balanced` tinyint NOT NULL DEFAULT '0' COMMENT '是否借贷平衡:0否 1是',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿/POSTED已记账/CANCELLED已作废',
  `post_user_id` bigint DEFAULT NULL COMMENT '记账人',
  `post_time` datetime DEFAULT NULL COMMENT '记账时间',
  `remark` varchar(500) DEFAULT NULL,
  `source_doc_type` varchar(32) DEFAULT NULL COMMENT '来源单据类型(标准化溯源字段，与source_type语义一致)',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据ID(标准化溯源字段，与bill_id语义一致)',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT 'source wf process instance id (D-TRC-05)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_voucher_no` (`tenant_id`,`voucher_no`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_period` (`tenant_id`,`company_id`,`period_year`,`period_month`),
  KEY `idx_tenant_bill` (`tenant_id`,`bill_id`),
  KEY `idx_proc_inst_id` (`proc_inst_id`),
  KEY `idx_tenant_srcdoc` (`tenant_id`,`source_doc_id`,`source_type`),
  KEY `idx_tenant_del` (`tenant_id`,`del_flag`),
  KEY `idx_tenant_company_del_id` (`tenant_id`,`company_id`,`del_flag`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会计凭证头';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_voucher`
--

LOCK TABLES `op_voucher` WRITE;
/*!40000 ALTER TABLE `op_voucher` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_voucher` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:06:04
