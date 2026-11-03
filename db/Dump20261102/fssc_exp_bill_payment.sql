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
-- Table structure for table `exp_bill_payment`
--

DROP TABLE IF EXISTS `exp_bill_payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exp_bill_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `bill_id` bigint NOT NULL COMMENT '单据主表ID',
  `line_no` int NOT NULL DEFAULT '1' COMMENT '行号',
  `payee_type` varchar(20) NOT NULL DEFAULT 'EMPLOYEE' COMMENT '收款方类型:EMPLOYEE/SUPPLIER/COMPANY/OTHER',
  `payee_id` bigint DEFAULT NULL COMMENT '收款方账户ID(md_payee)',
  `payee_name` varchar(200) NOT NULL COMMENT '收款户名(快照)',
  `bank_name` varchar(200) DEFAULT NULL COMMENT '开户行(快照)',
  `bank_account` varchar(64) DEFAULT NULL COMMENT '银行账号(快照,脱敏展示)',
  `pay_method` varchar(20) NOT NULL DEFAULT 'BANK' COMMENT '支付方式:BANK银行转账/CASH现金/CARD公务卡/OFFSET内部冲抵',
  `pay_account_id` bigint DEFAULT NULL COMMENT '付款账户ID(md_bank_account)',
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '结算金额',
  `pay_status` varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT '支付状态:WAITING待支付/PAYING支付中/PAID已支付/FAILED支付失败',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `pay_batch_no` varchar(64) DEFAULT NULL COMMENT '支付批次号',
  `pay_serial_no` varchar(100) DEFAULT NULL COMMENT '银行流水号',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `contract_id` bigint DEFAULT NULL COMMENT '关联合同ID',
  `customer_id` bigint DEFAULT NULL COMMENT '关联往来单位ID',
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT 'source wf process instance id (D-TRC-05)',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_bill` (`tenant_id`,`bill_id`),
  KEY `idx_tenant_paystatus` (`tenant_id`,`pay_status`),
  KEY `idx_tenant_batch` (`tenant_id`,`pay_batch_no`),
  KEY `idx_proc_inst_id` (`proc_inst_id`),
  KEY `idx_t_del_status_amount` (`tenant_id`,`del_flag`,`pay_status`,`amount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单据结算收款信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exp_bill_payment`
--

LOCK TABLES `exp_bill_payment` WRITE;
/*!40000 ALTER TABLE `exp_bill_payment` DISABLE KEYS */;
/*!40000 ALTER TABLE `exp_bill_payment` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:43
