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
-- Table structure for table `wf_task_history_cold`
--

DROP TABLE IF EXISTS `wf_task_history_cold`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_task_history_cold` (
  `id` bigint NOT NULL COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `proc_inst_id` varchar(64) NOT NULL COMMENT '流程实例ID',
  `task_id` varchar(64) DEFAULT NULL COMMENT '任务ID',
  `node_key` varchar(64) DEFAULT NULL COMMENT '节点key',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `biz_type` varchar(50) NOT NULL COMMENT '业务单据类型',
  `biz_id` bigint DEFAULT NULL,
  `action` varchar(20) NOT NULL COMMENT '动作:SUBMIT提交/APPROVE同意/REJECT驳回/TRANSFER转办/ADDSIGN加签/CANCEL撤销/URGE催办/AUTO_PASS自动通过/TERMINATE终止',
  `oper_user_id` bigint DEFAULT NULL COMMENT '操作人用户ID',
  `oper_user_name` varchar(64) DEFAULT NULL COMMENT '操作人姓名(冗余,防人员离职后查不到)',
  `oper_dept_name` varchar(100) DEFAULT NULL COMMENT '操作人部门(冗余)',
  `target_user_id` bigint DEFAULT NULL COMMENT '目标人(转办/加签时)',
  `comment` varchar(2000) DEFAULT NULL COMMENT '审批意见',
  `attach_ids` varchar(500) DEFAULT NULL COMMENT '意见附件ID,逗号分隔',
  `duration` bigint DEFAULT NULL COMMENT '本节点停留时长(毫秒)',
  `oper_time` datetime NOT NULL COMMENT '操作时间',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间(G4第6条流程实例归档)',
  PRIMARY KEY (`id`,`oper_time`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_proc_inst` (`tenant_id`,`proc_inst_id`),
  KEY `idx_tenant_biz` (`tenant_id`,`biz_type`,`biz_id`),
  KEY `idx_archived_at` (`archived_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批留痕冷存(G4归档,列同wf_task_history+archived_at)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wf_task_history_cold`
--

LOCK TABLES `wf_task_history_cold` WRITE;
/*!40000 ALTER TABLE `wf_task_history_cold` DISABLE KEYS */;
/*!40000 ALTER TABLE `wf_task_history_cold` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:07
