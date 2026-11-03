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
-- Table structure for table `exp_cold_archive_log`
--

DROP TABLE IF EXISTS `exp_cold_archive_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exp_cold_archive_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID(冷表不在租户拦截器管辖，此列手工维护)',
  `bill_id` bigint NOT NULL COMMENT '被归档单据ID',
  `bill_no` varchar(64) NOT NULL COMMENT '被归档单据编号',
  `bill_type` varchar(20) DEFAULT NULL COMMENT '单据类型:APPLY/CLAIM/LOAN/REPAY/PAY',
  `moved_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '搬移时间',
  `line_cnt` int NOT NULL DEFAULT '0' COMMENT '明细行数(exp_bill_line_cold)',
  `pay_cnt` int NOT NULL DEFAULT '0' COMMENT '结算行数(exp_bill_payment_cold)',
  `attach_cnt` int NOT NULL DEFAULT '0' COMMENT '附件数(exp_bill_attach_cold)',
  `rel_cnt` int NOT NULL DEFAULT '0' COMMENT '关联数(exp_bill_relation_cold)',
  `offset_cnt` int NOT NULL DEFAULT '0' COMMENT '借款核销数(exp_loan_offset_cold)',
  `batch_no` varchar(32) DEFAULT NULL COMMENT '批次号(一次调度任务共用)',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_time` (`tenant_id`,`moved_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单据冷热分离归档流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exp_cold_archive_log`
--

LOCK TABLES `exp_cold_archive_log` WRITE;
/*!40000 ALTER TABLE `exp_cold_archive_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `exp_cold_archive_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:18
