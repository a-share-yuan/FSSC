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
-- Table structure for table `wf_add_sign`
--

DROP TABLE IF EXISTS `wf_add_sign`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_add_sign` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `proc_inst_id` varchar(64) NOT NULL COMMENT '流程实例ID',
  `src_task_id` varchar(64) NOT NULL COMMENT '触发加签的任务ID（前加签=当前任务；后加签=已完成的上游任务）',
  `task_id` varchar(64) DEFAULT NULL COMMENT '实际被加签的任务ID（后加签在流程推进后回填）',
  `node_key` varchar(64) DEFAULT NULL COMMENT '节点key（BPMN taskDefinitionKey）',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `position` varchar(10) NOT NULL COMMENT '加签位置:BEFORE前加签/AFTER后加签',
  `sign_users` varchar(500) NOT NULL COMMENT '加签人用户ID,逗号分隔（多人=链式依次签）',
  `seq_index` int NOT NULL DEFAULT '0' COMMENT '当前轮到的加签人下标（0-based）',
  `exit_assignee` varchar(64) DEFAULT NULL COMMENT '回跳目标:加签链走完后交还的办理人ID,空=交还候选组/任务池',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING已登记待生效/ACTIVE加签进行中/DONE已完成/CANCELLED已取消/FAILED未能生效',
  `reason` varchar(500) DEFAULT NULL COMMENT '加签原因',
  `create_user_id` bigint DEFAULT NULL COMMENT '发起加签的人（原办理人或其候选人）',
  `done_time` datetime DEFAULT NULL COMMENT '加签链走完/取消时间',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_src_task` (`src_task_id`),
  KEY `idx_proc` (`proc_inst_id`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程加签（前/后加签）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wf_add_sign`
--

LOCK TABLES `wf_add_sign` WRITE;
/*!40000 ALTER TABLE `wf_add_sign` DISABLE KEYS */;
/*!40000 ALTER TABLE `wf_add_sign` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:29:45
