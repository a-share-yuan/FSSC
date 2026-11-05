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
-- Table structure for table `ar_month_close`
--

DROP TABLE IF EXISTS `ar_month_close`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ar_month_close` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `close_no` varchar(48) NOT NULL,
  `company_id` bigint NOT NULL DEFAULT '1',
  `period` varchar(10) NOT NULL COMMENT 'yyyy-MM',
  `close_type` varchar(16) NOT NULL DEFAULT 'RECEIVABLE' COMMENT 'RECEIVABLE应收月结/COST成本月结',
  `status` varchar(24) NOT NULL DEFAULT 'INIT' COMMENT 'INIT/INTERFACE_CLEARED/DATA_PULLED/ADJUSTED/GL_POSTED/RECONCILED/CLOSED',
  `pulled_amount` decimal(18,2) DEFAULT NULL COMMENT '扛数快照-单据金额合计',
  `pulled_count` int DEFAULT NULL COMMENT '扛数快照-单据数',
  `gl_amount` decimal(18,2) DEFAULT NULL COMMENT '过总账金额',
  `diff_amount` decimal(18,2) DEFAULT NULL COMMENT '核对差异金额',
  `diff_flag` int DEFAULT '0' COMMENT '1=差异未消除',
  `adjust_note` varchar(500) DEFAULT NULL COMMENT '差异调整说明',
  `voucher_no` varchar(48) DEFAULT NULL COMMENT '过总账凭证号',
  `operator` varchar(64) DEFAULT NULL,
  `closed_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_close_no` (`tenant_id`,`close_no`),
  KEY `idx_period` (`tenant_id`,`company_id`,`period`,`close_type`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AR月末结账(应收月结/成本月结,蓝图9.6)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ar_month_close`
--

LOCK TABLES `ar_month_close` WRITE;
/*!40000 ALTER TABLE `ar_month_close` DISABLE KEYS */;
/*!40000 ALTER TABLE `ar_month_close` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:29:39
