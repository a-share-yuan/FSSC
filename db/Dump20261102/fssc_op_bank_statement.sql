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
-- Table structure for table `op_bank_statement`
--

DROP TABLE IF EXISTS `op_bank_statement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_bank_statement` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `company_id` bigint NOT NULL COMMENT '公司ID',
  `bank_account_id` bigint NOT NULL COMMENT '付款账户ID',
  `trans_date` datetime NOT NULL COMMENT '交易时间',
  `bank_serial` varchar(100) NOT NULL COMMENT '银行流水号',
  `opposite_name` varchar(200) DEFAULT NULL COMMENT '对方户名',
  `opposite_account` varchar(64) DEFAULT NULL COMMENT '对方账号',
  `trans_type` varchar(20) DEFAULT NULL COMMENT '借贷方向:DEBIT借/CREDIT贷',
  `amount` decimal(20,2) NOT NULL COMMENT '金额',
  `balance` decimal(20,2) DEFAULT NULL COMMENT '账户余额',
  `summary` varchar(500) DEFAULT NULL COMMENT '摘要',
  `recon_status` varchar(20) NOT NULL DEFAULT 'UNRECON' COMMENT '对账状态:UNRECON未对账/RECONED已对账/DIFF差额',
  `recon_bill_id` bigint DEFAULT NULL COMMENT '勾稽到的单据ID',
  `recon_bill_type` varchar(32) DEFAULT NULL COMMENT 'recon_bill_id 指向的表：TR_CREDIT_BILL / RC_RECEIPT',
  `recon_time` datetime DEFAULT NULL COMMENT '对账时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `slip_status` varchar(16) DEFAULT 'NONE' COMMENT '回单状态:NONE未生成/DOWNLOADED已下载/ARCHIVED已归档',
  `slip_file_id` bigint DEFAULT NULL COMMENT '回单影像文件ID(sys_file.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_serial` (`tenant_id`,`bank_account_id`,`bank_serial`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_recon` (`tenant_id`,`recon_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='银行流水(回单)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_bank_statement`
--

LOCK TABLES `op_bank_statement` WRITE;
/*!40000 ALTER TABLE `op_bank_statement` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_bank_statement` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:29:52
