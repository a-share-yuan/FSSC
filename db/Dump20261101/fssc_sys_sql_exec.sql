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
-- Table structure for table `sys_sql_exec`
--

DROP TABLE IF EXISTS `sys_sql_exec`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_sql_exec` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID（DDL 类脚本为 NULL）',
  `script_name` varchar(128) NOT NULL COMMENT '脚本文件名（含 _seed/_rollback 后缀）',
  `version_tag` varchar(32) DEFAULT NULL COMMENT '版本号（V1.4.66 →1.4.66）',
  `stmt_index` int NOT NULL COMMENT '脚本内第几条语句（从1 开始）',
  `stmt_digest` char(16) DEFAULT NULL COMMENT '语句内容 MD4/MD5 前 16 位（定位改动）',
  `rows_affected` int NOT NULL DEFAULT '0' COMMENT '影响行数',
  `status` varchar(16) NOT NULL DEFAULT 'OK' COMMENT 'OK / FAILED',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `executed_by` varchar(64) DEFAULT NULL COMMENT '执行人（OS 用户）',
  `checksum` char(32) DEFAULT NULL COMMENT '整份脚本内容 MD5',
  `executed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_script_stmt` (`script_name`,`stmt_index`),
  KEY `idx_script` (`script_name`),
  KEY `idx_version` (`version_tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SQL 脚本执行台账（手工迁移对账用）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_sql_exec`
--

LOCK TABLES `sys_sql_exec` WRITE;
/*!40000 ALTER TABLE `sys_sql_exec` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_sql_exec` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:06:00
