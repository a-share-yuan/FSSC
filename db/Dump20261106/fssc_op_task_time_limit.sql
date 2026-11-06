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
-- Table structure for table `op_task_time_limit`
--

DROP TABLE IF EXISTS `op_task_time_limit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_task_time_limit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `task_id` varchar(64) NOT NULL COMMENT 'Flowable任务ID（wf_task.task_id）',
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `model_key` varchar(64) DEFAULT NULL COMMENT '流程定义key',
  `node_key` varchar(64) DEFAULT NULL COMMENT '节点key',
  `priority` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT '优先级:NORMAL普通/URGENT加急',
  `policy_id` bigint DEFAULT NULL COMMENT '命中的策略配置ID（op_dispatch_policy）',
  `limit_hours` decimal(8,2) DEFAULT NULL COMMENT '本次采用的时限(小时)，按优先级取普通或加急',
  `due_time` datetime DEFAULT NULL COMMENT '到期时间（与 wf_task.due_time 同值，便于按本表核对漂移）',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ottl_tenant_task` (`tenant_id`,`task_id`),
  KEY `idx_ottl_due` (`tenant_id`,`due_time`),
  KEY `idx_ottl_priority` (`tenant_id`,`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='G3 任务优先级与时限落账（wf_task 的旁表扩展）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_task_time_limit`
--

LOCK TABLES `op_task_time_limit` WRITE;
/*!40000 ALTER TABLE `op_task_time_limit` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_task_time_limit` ENABLE KEYS */;
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
