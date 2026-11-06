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
-- Table structure for table `op_quality`
--

DROP TABLE IF EXISTS `op_quality`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_quality` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `bill_id` bigint NOT NULL COMMENT '被质检单据ID',
  `bill_no` varchar(64) DEFAULT NULL COMMENT '单据号',
  `auditor_id` bigint DEFAULT NULL COMMENT '原审核人',
  `checker_id` bigint NOT NULL COMMENT '质检员',
  `check_type` varchar(20) NOT NULL DEFAULT 'RANDOM' COMMENT '质检类型:RANDOM抽检/FULL全检/KEY关键',
  `score` int DEFAULT NULL COMMENT '质检得分(百分制)',
  `deduct_reason` varchar(1000) DEFAULT NULL COMMENT '扣分/问题说明',
  `result` varchar(20) NOT NULL DEFAULT 'PASS' COMMENT '质检结果:PASS合格/BACK退回整改',
  `check_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '质检时间',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `violation_reason_code` varchar(32) DEFAULT NULL COMMENT '违规原因码（md_violation_reason.reason_code）；有值则按字典扣分，为空沿用 -20',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_bill` (`tenant_id`,`bill_id`),
  KEY `idx_tenant_checker` (`tenant_id`,`checker_id`,`result`),
  KEY `idx_tenant_del` (`tenant_id`,`del_flag`),
  KEY `idx_oq_tenant_del_ct` (`tenant_id`,`del_flag`,`check_time`,`id`),
  KEY `idx_oq_tenant_del_result` (`tenant_id`,`del_flag`,`result`),
  KEY `idx_oq_tenant_del_type` (`tenant_id`,`del_flag`,`check_type`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='共享质检记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_quality`
--

LOCK TABLES `op_quality` WRITE;
/*!40000 ALTER TABLE `op_quality` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_quality` ENABLE KEYS */;
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
