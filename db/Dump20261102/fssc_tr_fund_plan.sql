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
-- Table structure for table `tr_fund_plan`
--

DROP TABLE IF EXISTS `tr_fund_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tr_fund_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `plan_no` varchar(64) DEFAULT NULL COMMENT '计划编号',
  `plan_date` date DEFAULT NULL COMMENT '计划日期',
  `period` varchar(10) DEFAULT NULL COMMENT '期间yyyyMM',
  `company_id` bigint DEFAULT NULL COMMENT '所属公司',
  `income_amount` decimal(20,2) DEFAULT '0.00' COMMENT '计划收入',
  `outcome_amount` decimal(20,2) DEFAULT '0.00' COMMENT '计划支出',
  `income_actual` decimal(20,2) DEFAULT '0.00' COMMENT '实际收入',
  `outcome_actual` decimal(20,2) DEFAULT '0.00' COMMENT '实际支出',
  `owner_id` bigint DEFAULT NULL COMMENT '资金负责人',
  `status` varchar(20) DEFAULT 'DRAFT' COMMENT 'DRAFT草稿/SELECTED已入选/COMPLETED已完成',
  `create_by` bigint DEFAULT NULL COMMENT '登记人（sys_user.user_id）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_plan_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金计划(TR-P0-8)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tr_fund_plan`
--

LOCK TABLES `tr_fund_plan` WRITE;
/*!40000 ALTER TABLE `tr_fund_plan` DISABLE KEYS */;
/*!40000 ALTER TABLE `tr_fund_plan` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:08
