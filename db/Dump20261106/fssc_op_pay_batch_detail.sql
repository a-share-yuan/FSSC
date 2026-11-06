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
-- Table structure for table `op_pay_batch_detail`
--

DROP TABLE IF EXISTS `op_pay_batch_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_pay_batch_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `batch_id` bigint NOT NULL COMMENT '付款批次ID',
  `bill_payment_id` bigint DEFAULT NULL COMMENT '关联单据结算行ID',
  `payee_name` varchar(200) NOT NULL COMMENT '收款户名',
  `supplier_id` bigint DEFAULT NULL COMMENT '组批时锁定的供应商档案id',
  `bank_name` varchar(200) DEFAULT NULL COMMENT '开户行',
  `bank_account` varchar(64) NOT NULL COMMENT '收款账号',
  `amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '付款金额',
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `use_flag` varchar(10) NOT NULL DEFAULT '0' COMMENT '行内用途码',
  `postscript` varchar(200) DEFAULT NULL COMMENT '附言',
  `bank_serial` varchar(100) DEFAULT NULL COMMENT '银行流水号(回盘回填)',
  `status` varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT '状态:WAITING待付/SUCCESS成功/FAIL失败',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_batch` (`tenant_id`,`batch_id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款批次明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_pay_batch_detail`
--

LOCK TABLES `op_pay_batch_detail` WRITE;
/*!40000 ALTER TABLE `op_pay_batch_detail` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_pay_batch_detail` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:37
