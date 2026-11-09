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
-- Table structure for table `wf_instance_cold`
--

DROP TABLE IF EXISTS `wf_instance_cold`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_instance_cold` (
  `id` bigint NOT NULL COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `proc_inst_id` varchar(64) NOT NULL COMMENT 'Flowable流程实例ID',
  `proc_def_id` varchar(64) DEFAULT NULL COMMENT 'Flowable流程定义ID',
  `model_key` varchar(64) NOT NULL COMMENT '流程定义key',
  `biz_type` varchar(50) NOT NULL COMMENT '业务单据类型',
  `biz_id` bigint DEFAULT NULL COMMENT 'biz',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '业务单据号',
  `title` varchar(300) DEFAULT NULL COMMENT '流程标题(列表展示)',
  `amount` decimal(20,2) DEFAULT NULL COMMENT '单据金额(用于列表排序筛选)',
  `start_user_id` bigint NOT NULL COMMENT '发起人用户ID',
  `start_dept_id` bigint DEFAULT NULL COMMENT '发起人部门ID',
  `start_time` datetime DEFAULT NULL COMMENT '发起时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration` bigint DEFAULT NULL COMMENT '耗时(毫秒)',
  `current_node` varchar(200) DEFAULT NULL COMMENT '当前节点名称',
  `current_assignee` varchar(500) DEFAULT NULL COMMENT '当前处理人姓名,多个逗号分隔',
  `status` varchar(20) NOT NULL COMMENT '状态:RUNNING审批中/APPROVED已通过/REJECTED已驳回/CANCELLED已撤销/TERMINATED已终止',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间(G4第6条流程实例归档)',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_proc_inst` (`tenant_id`,`proc_inst_id`),
  KEY `idx_tenant_biz` (`tenant_id`,`biz_type`,`biz_id`),
  KEY `idx_archived_at` (`archived_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程实例冷存(G4归档,列同wf_instance+archived_at)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wf_instance_cold`
--

LOCK TABLES `wf_instance_cold` WRITE;
/*!40000 ALTER TABLE `wf_instance_cold` DISABLE KEYS */;
/*!40000 ALTER TABLE `wf_instance_cold` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:23:58
