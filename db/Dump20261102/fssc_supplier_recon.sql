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
-- Table structure for table `supplier_recon`
--

DROP TABLE IF EXISTS `supplier_recon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `supplier_recon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `supplier_id` bigint NOT NULL COMMENT '供应商ID',
  `supplier_name` varchar(200) DEFAULT NULL COMMENT '供应商名称',
  `recon_no` varchar(64) NOT NULL COMMENT '对账单号',
  `period` varchar(10) NOT NULL COMMENT '对账期间 yyyyMM',
  `payable_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '应付合计（关联发票）',
  `paid_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '已付合计',
  `balance` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '余额=应付-已付',
  `opening_balance` decimal(20,2) DEFAULT '0.00' COMMENT '期初余额',
  `period_payable` decimal(20,2) DEFAULT '0.00' COMMENT '本期应付发生额',
  `period_paid` decimal(20,2) DEFAULT '0.00' COMMENT '本期已付发生额',
  `closing_balance` decimal(20,2) DEFAULT '0.00' COMMENT '期末余额',
  `recon_type` varchar(20) DEFAULT 'MANUAL' COMMENT '对账类型: AUTO/MANUAL',
  `letter_no` varchar(64) DEFAULT NULL COMMENT '对账函编号',
  `letter_status` varchar(20) DEFAULT 'NONE' COMMENT '对账函状态: NONE/SENT/RECEIVED',
  `letter_sent_time` datetime DEFAULT NULL COMMENT '对账函发出时间',
  `letter_received_time` datetime DEFAULT NULL COMMENT '供应商回函时间',
  `supplier_confirm_status` varchar(20) DEFAULT 'PENDING' COMMENT '供应商确认状态: PENDING/CONFIRMED/REJECTED',
  `supplier_confirm_time` datetime DEFAULT NULL COMMENT '供应商确认时间',
  `supplier_confirm_remark` varchar(500) DEFAULT NULL COMMENT '供应商确认备注',
  `linku_sync_status` varchar(20) DEFAULT 'NONE' COMMENT 'LinkU同步状态: NONE/SYNCED/FAILED',
  `linku_sync_time` datetime DEFAULT NULL COMMENT 'LinkU同步时间',
  `linku_recon_id` bigint DEFAULT NULL COMMENT 'LinkU侧对账单ID',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT已生成/CONFIRMED双方确认/DIFF有差异',
  `diff_remark` varchar(500) DEFAULT NULL COMMENT '差异说明',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认时间',
  `confirm_by` bigint DEFAULT NULL COMMENT '确认人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志',
  `company_id` bigint DEFAULT NULL COMMENT '所属公司(多分子公司审批路由 V1.3.17)',
  `linku_partner_id` bigint DEFAULT NULL COMMENT 'LinkU侧供应商ID(partnerId)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recon` (`tenant_id`,`supplier_id`,`period`),
  KEY `idx_recon_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商对账单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `supplier_recon`
--

LOCK TABLES `supplier_recon` WRITE;
/*!40000 ALTER TABLE `supplier_recon` DISABLE KEYS */;
/*!40000 ALTER TABLE `supplier_recon` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:20
