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
-- Table structure for table `wf_process_log`
--

DROP TABLE IF EXISTS `wf_process_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_process_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `task_id` varchar(64) DEFAULT NULL COMMENT 'Flowable任务ID（流程级事件为空）',
  `model_key` varchar(64) DEFAULT NULL COMMENT '流程定义key',
  `node_key` varchar(64) DEFAULT NULL COMMENT '节点ID(BPMN userTask id)',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '业务单据类型',
  `biz_id` bigint DEFAULT NULL COMMENT '业务单据ID',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '业务单号',
  `title` varchar(200) DEFAULT NULL COMMENT '单据标题',
  `assignee_id` bigint DEFAULT NULL COMMENT '办理人用户ID（候选组任务可能为空）',
  `log_type` varchar(32) NOT NULL COMMENT '日志类型:PROCESS_STARTED流程发起/TASK_CREATED任务生成/TASK_ASSIGNED任务指派/TASK_COMPLETED任务办结/TASK_CANCELLED任务取消/PROCESS_COMPLETED流程结束/TIMER_FIRED定时器触发',
  `level` varchar(8) NOT NULL DEFAULT 'INFO' COMMENT '级别:INFO正常/WARN异常但不致命',
  `content` varchar(500) DEFAULT NULL COMMENT '人类可读的一句话（列表直接显示）',
  `detail` varchar(2000) DEFAULT NULL COMMENT '事件当时的明细快照(JSON:变量/办理人/审批项结果等)',
  `oper_user_id` bigint DEFAULT NULL COMMENT '操作人用户ID（能从上下文取到时）',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_wpl_tenant_proc` (`tenant_id`,`proc_inst_id`,`id`),
  KEY `idx_wpl_tenant_bizno` (`tenant_id`,`biz_no`),
  KEY `idx_wpl_tenant_type_time` (`tenant_id`,`log_type`,`create_time`),
  KEY `idx_wpl_tenant_del_id` (`tenant_id`,`del_flag`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程处理详细日志（详细日志开关，管理中心可查）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wf_process_log`
--

LOCK TABLES `wf_process_log` WRITE;
/*!40000 ALTER TABLE `wf_process_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `wf_process_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-05 22:33:52
