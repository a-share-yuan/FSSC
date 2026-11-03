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
-- Table structure for table `dispatch_task`
--

DROP TABLE IF EXISTS `dispatch_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dispatch_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `task_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务编码',
  `bill_id` bigint NOT NULL COMMENT '单据ID',
  `bill_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单据类型',
  `expense_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '费用类型',
  `amount` decimal(18,2) NOT NULL COMMENT '金额',
  `legal_entity_id` bigint DEFAULT NULL COMMENT '法人主体ID',
  `complexity_score` decimal(3,2) NOT NULL DEFAULT '0.00' COMMENT '复杂度评分（0-10）',
  `matched_rule_id` bigint DEFAULT NULL COMMENT '匹配的派单规则ID',
  `assigned_group_id` bigint DEFAULT NULL COMMENT '分配的审核组ID',
  `assigned_reviewer_id` bigint DEFAULT NULL COMMENT '分配的审核员ID',
  `dispatch_strategy` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '派单策略',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：PENDING/ASSIGNED/PROCESSING/COMPLETED/UPGRADED',
  `assigned_at` datetime DEFAULT NULL COMMENT '分配时间',
  `due_time` datetime DEFAULT NULL,
  `sla_due_time` datetime DEFAULT NULL COMMENT 'SLA due time',
  `upgraded_at` datetime DEFAULT NULL COMMENT '升级时间',
  `upgrade_reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '升级原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `sla_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'NORMAL',
  `credit_score` int DEFAULT NULL,
  `quality_score` int DEFAULT NULL,
  `satisfaction_score` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_code` (`task_code`),
  KEY `idx_bill_id` (`bill_id`),
  KEY `idx_expense_type` (`expense_type`),
  KEY `idx_status` (`status`),
  KEY `idx_assigned_reviewer` (`assigned_reviewer_id`,`status`),
  KEY `idx_due_time` (`due_time`),
  KEY `idx_complexity_score` (`complexity_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='派单任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dispatch_task`
--

LOCK TABLES `dispatch_task` WRITE;
/*!40000 ALTER TABLE `dispatch_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `dispatch_task` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:26
