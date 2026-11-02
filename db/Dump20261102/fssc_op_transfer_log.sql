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
-- Table structure for table `op_transfer_log`
--

DROP TABLE IF EXISTS `op_transfer_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_transfer_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `from_user_id` bigint NOT NULL COMMENT '移交人（原办理人）',
  `from_user_name` varchar(64) DEFAULT NULL COMMENT '移交人姓名（冗余）',
  `to_user_id` bigint NOT NULL COMMENT '接收人',
  `to_user_name` varchar(64) DEFAULT NULL COMMENT '接收人姓名（冗余）',
  `task_ids` text COMMENT '被移交的 Flowable 任务ID，逗号分隔',
  `task_count` int NOT NULL DEFAULT '0' COMMENT '请求移交条数',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '成功条数',
  `fail_count` int NOT NULL DEFAULT '0' COMMENT '失败条数',
  `failed_detail` text COMMENT '失败清单（taskId:原因），失败必须留痕不得静默',
  `reason` varchar(500) DEFAULT NULL COMMENT '移交原因',
  `oper_user_id` bigint DEFAULT NULL COMMENT '操作人（流程管理员/移交人本人）',
  `oper_user_name` varchar(64) DEFAULT NULL COMMENT '操作人姓名（冗余）',
  `oper_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '移交时间',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_otl_tenant_from` (`tenant_id`,`from_user_id`),
  KEY `idx_otl_tenant_to` (`tenant_id`,`to_user_id`),
  KEY `idx_otl_tenant_time` (`tenant_id`,`oper_time`),
  KEY `idx_otl_tenant_del_id` (`tenant_id`,`del_flag`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='G3 流程批量移交日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_transfer_log`
--

LOCK TABLES `op_transfer_log` WRITE;
/*!40000 ALTER TABLE `op_transfer_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_transfer_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:13:27
