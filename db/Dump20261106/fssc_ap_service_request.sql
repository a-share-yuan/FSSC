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
-- Table structure for table `ap_service_request`
--

DROP TABLE IF EXISTS `ap_service_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ap_service_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `bill_no` varchar(48) NOT NULL COMMENT '服务申请单号(YF/HX/JH/GL/JJ前缀)',
  `req_type` varchar(32) NOT NULL COMMENT 'PREPAY/PREPAY_WRITEOFF/PLAN_PAYMENT/RELATED_PAYMENT/URGENT_PAYMENT',
  `company_id` bigint DEFAULT NULL,
  `company_code` varchar(32) DEFAULT NULL COMMENT '公司代码(购方)',
  `company_name` varchar(128) DEFAULT NULL,
  `sbu` varchar(64) DEFAULT NULL COMMENT '事业部',
  `vendor_code` varchar(48) DEFAULT NULL COMMENT '供应商代码',
  `vendor_name` varchar(128) DEFAULT NULL,
  `vendor_locale` varchar(64) DEFAULT NULL COMMENT '供应商地点',
  `ccy` varchar(8) DEFAULT 'CNY',
  `exchange_rate` decimal(12,6) DEFAULT '1.000000',
  `payment_type` varchar(16) DEFAULT '快速' COMMENT '快速/人工/退款',
  `payment_way` varchar(24) DEFAULT NULL COMMENT '支票/银行转账/应付票据(派工维度R-01)',
  `bank_no` varchar(64) DEFAULT NULL COMMENT '银行账户',
  `payment_date` datetime DEFAULT NULL,
  `amount` decimal(18,2) DEFAULT NULL,
  `tax_amount` decimal(18,2) DEFAULT '0.00' COMMENT '预付款强制0',
  `rebate_amount` decimal(18,2) DEFAULT '0.00' COMMENT '折扣额默认0',
  `expire_date` date DEFAULT NULL COMMENT '汇票到期日(应付票据必填)',
  `payment_amount` decimal(18,2) DEFAULT NULL COMMENT '背书付款额=发票合计',
  `payment_config` varchar(64) DEFAULT 'payment process profile',
  `po_no` varchar(64) DEFAULT NULL COMMENT '预付款必填',
  `invoice_no` varchar(255) DEFAULT NULL COMMENT 'XXXXXX01-10/逗号非连续',
  `ekp_no` varchar(64) DEFAULT NULL COMMENT 'EKP流水号(参考)',
  `plan_table_no` varchar(64) DEFAULT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING_DISPATCH' COMMENT 'PENDING_DISPATCH/DISPATCHED/GROUP_REVIEW/POSTED/PAID/VOIDED/COMPLETED',
  `handler` varchar(64) DEFAULT NULL,
  `handler_pool` varchar(255) DEFAULT NULL COMMENT '选单池(R-02)',
  `group_leader` varchar(64) DEFAULT NULL COMMENT 'AP组长(R-03/R-04)',
  `modified_flag` int DEFAULT '0' COMMENT '确认时修改1是',
  `modify_note` varchar(500) DEFAULT NULL COMMENT '修改/审核留痕',
  `void_reason` varchar(255) DEFAULT NULL,
  `voucher_no` varchar(48) DEFAULT NULL COMMENT 'EBS付款凭证号',
  `pay_voucher_no` varchar(48) DEFAULT NULL COMMENT '资金付款凭证号',
  `remark` varchar(500) DEFAULT NULL,
  `dispatch_time` datetime DEFAULT NULL,
  `confirm_time` datetime DEFAULT NULL,
  `post_time` datetime DEFAULT NULL,
  `pay_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT 'Flowable流程实例ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bill_no` (`tenant_id`,`bill_no`),
  KEY `idx_type_status` (`tenant_id`,`req_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AP付款服务申请单(蓝图SCC_AP_15~19)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ap_service_request`
--

LOCK TABLES `ap_service_request` WRITE;
/*!40000 ALTER TABLE `ap_service_request` DISABLE KEYS */;
/*!40000 ALTER TABLE `ap_service_request` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:20
