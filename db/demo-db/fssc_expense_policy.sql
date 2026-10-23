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
-- Table structure for table `expense_policy`
--

DROP TABLE IF EXISTS `expense_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `expense_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `policy_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '政策编码',
  `policy_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '政策名称',
  `expense_item_id` bigint DEFAULT NULL COMMENT 'expense item id',
  `expense_item_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'expense item name',
  `expense_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '费用类型编码（关联expense_type.type_code）',
  `org_id` bigint DEFAULT NULL COMMENT '适用组织ID（NULL表示全局）',
  `max_amount` decimal(18,2) DEFAULT NULL COMMENT 'max amount',
  `need_invoice` int DEFAULT NULL COMMENT 'need invoice',
  `submit_days` int DEFAULT NULL COMMENT 'submit days',
  `approve_above_amount` decimal(18,2) DEFAULT NULL COMMENT 'approve above amount',
  `effective_start` date NOT NULL COMMENT '生效开始日期',
  `effective_end` date DEFAULT NULL COMMENT '生效结束日期',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-启用 0-停用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'remark',
  `create_by` bigint DEFAULT NULL COMMENT 'create by',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_by` bigint DEFAULT NULL COMMENT 'update by',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `del_flag` int DEFAULT '0' COMMENT 'del flag',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级（数字越大优先级越高）',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_policy_code` (`policy_code`),
  KEY `idx_expense_type` (`expense_type`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_effective` (`effective_start`,`effective_end`),
  KEY `idx_status_priority` (`status`,`priority`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='费用政策表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `expense_policy`
--

LOCK TABLES `expense_policy` WRITE;
/*!40000 ALTER TABLE `expense_policy` DISABLE KEYS */;
INSERT INTO `expense_policy` VALUES (21,1,'TRV_CN','国内差旅费用标准',NULL,'差旅费','TRAVEL',NULL,20000.00,1,30,5000.00,'2026-01-01','2026-12-31',1,'含住宿/交通/伙食，超标仅警告不拦截',1,'2026-10-17 06:17:06',NULL,'2026-10-17 06:17:06',0,10,1,'2026-10-17 06:17:06',NULL,'2026-10-17 06:17:06'),(22,1,'TRV_INTL','国际差旅费用标准',NULL,'差旅费','TRAVEL',NULL,50000.00,1,45,20000.00,'2026-01-01','2026-12-31',1,'需事前审批，超额需总监以上附加审批',1,'2026-10-17 06:17:06',NULL,'2026-10-17 06:17:06',0,20,1,'2026-10-17 06:17:06',NULL,'2026-10-17 06:17:06'),(23,1,'OFFICE_STD','办公用品费用标准',NULL,'办公费','OFFICE',NULL,5000.00,1,60,2000.00,'2026-01-01','2026-12-31',1,'办公用品与耗材',1,'2026-10-17 06:17:06',NULL,'2026-10-17 06:17:06',0,30,1,'2026-10-17 06:17:06',NULL,'2026-10-17 06:17:06'),(24,1,'ENT_STD','业务招待费用标准',NULL,'招待费','ENTERTAIN',NULL,10000.00,1,30,3000.00,'2026-01-01','2026-12-31',1,'招待费须注明被招待客户与事由',1,'2026-10-17 06:17:06',NULL,'2026-10-17 06:17:06',0,40,1,'2026-10-17 06:17:06',NULL,'2026-10-17 06:17:06');
/*!40000 ALTER TABLE `expense_policy` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:15:29
