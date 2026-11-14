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
-- Table structure for table `op_pay_batch`
--

DROP TABLE IF EXISTS `op_pay_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_pay_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `batch_no` varchar(64) NOT NULL COMMENT '付款批次号',
  `company_id` bigint NOT NULL COMMENT '付款公司ID',
  `bank_account_id` bigint NOT NULL COMMENT '付款账户ID',
  `pay_method` varchar(20) NOT NULL DEFAULT 'BANK' COMMENT '支付方式:BANK/COLLECT代发',
  `total_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '批次总金额',
  `detail_count` int NOT NULL DEFAULT '0' COMMENT '明细条数',
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿/SUBMITTED已提交银行/PROCESSING处理中/SUCCESS全部成功/PART_SUCCESS部分成功/FAILED全部失败',
  `submit_user_id` bigint DEFAULT NULL COMMENT '提交人',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `bank_resp` varchar(500) DEFAULT NULL COMMENT '银行返回信息',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '支付失败原因',
  `fail_time` datetime DEFAULT NULL COMMENT '支付失败时间',
  `remark` varchar(500) DEFAULT NULL,
  `source_doc_type` varchar(32) DEFAULT NULL COMMENT '来源单据类型:EXP_BILL_PAYMENT付款记录/AUTO_GROUP自动组批',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT 'source wf process instance id (D-TRC-05)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_batch_no` (`tenant_id`,`batch_no`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_status` (`tenant_id`,`status`),
  KEY `idx_proc_inst_id` (`proc_inst_id`),
  KEY `idx_tenant_owner` (`tenant_id`,`create_by`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款批次';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_pay_batch`
--

LOCK TABLES `op_pay_batch` WRITE;
/*!40000 ALTER TABLE `op_pay_batch` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_pay_batch` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:28:45
