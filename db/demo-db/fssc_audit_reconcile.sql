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
-- Table structure for table `audit_reconcile`
--

DROP TABLE IF EXISTS `audit_reconcile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_reconcile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `bill_type` varchar(32) NOT NULL COMMENT '单据类型 APPLY/CLAIM/LOAN/REPAY/PAY',
  `bill_id` bigint NOT NULL COMMENT '单据ID',
  `bill_no` varchar(64) DEFAULT NULL COMMENT '单号',
  `check_item` varchar(20) NOT NULL COMMENT '核对项 VOUCHER/PAYMENT/STATUS',
  `expect_value` varchar(200) DEFAULT NULL COMMENT '期望值',
  `actual_value` varchar(200) DEFAULT NULL COMMENT '实际值',
  `diff_desc` varchar(500) DEFAULT NULL COMMENT '差异说明',
  `status` varchar(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN未处理/RESOLVED已处理',
  `resolve_by` bigint DEFAULT NULL COMMENT '处理人',
  `resolve_time` datetime DEFAULT NULL COMMENT '处理时间',
  `resolve_remark` varchar(500) DEFAULT NULL COMMENT '处理备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_rc_bill` (`tenant_id`,`bill_type`,`bill_id`),
  KEY `idx_rc_status` (`tenant_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=172 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据一致性对账异常流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_reconcile`
--

LOCK TABLES `audit_reconcile` WRITE;
/*!40000 ALTER TABLE `audit_reconcile` DISABLE KEYS */;
/*!40000 ALTER TABLE `audit_reconcile` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:15:35
