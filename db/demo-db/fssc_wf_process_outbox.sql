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
-- Table structure for table `wf_process_outbox`
--

DROP TABLE IF EXISTS `wf_process_outbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_process_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（兼作消费顺序号：同一事务内严格递增）',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `event_type` varchar(32) NOT NULL COMMENT '引擎事件类型:PROCESS_STARTED/TASK_CREATED/TASK_ASSIGNED/TASK_COMPLETED/TASK_DELETED/PROCESS_COMPLETED',
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID（分片键：同一实例的事件串行消费）',
  `task_id` varchar(64) DEFAULT NULL COMMENT 'Flowable任务ID（流程级事件为空）',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '业务单据类型',
  `biz_id` bigint DEFAULT NULL COMMENT '业务单据ID',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '业务单号',
  `payload` json DEFAULT NULL COMMENT '投影事件信封（WfProjectionEvent 的 JSON：监听器在事件当场把所有需要 Flowable 实体才能解析的字段 = 候选组/到期时间/变量回退 全部解好，消费者只做纯写库，不再触碰引擎）',
  `idempotency_key` varchar(160) NOT NULL COMMENT '幂等键（唯一）：eventType:taskId[:assignee] / eventType:procInstId。重复投递被唯一键拒绝=幂等而非报错',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态:PENDING待消费/CONSUMED已投影/DEAD死信（重试超上限，已记缺陷待人工处置）',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试消费次数',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下次可消费时间（退避重试；NULL=立即可消费）',
  `consumed_time` datetime DEFAULT NULL COMMENT '投影成功时刻',
  `last_error` varchar(1000) DEFAULT NULL COMMENT '最后一次失败原因（截断）',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '入队时刻（积压最老年龄按它算）',
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wpo_idem` (`idempotency_key`),
  KEY `idx_wpo_claim` (`status`,`next_retry_at`,`id`),
  KEY `idx_wpo_tenant_proc` (`tenant_id`,`proc_inst_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=700000274 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程投影事件发件箱（「流程处理模式=消息队列」，事务性发件箱实现）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wf_process_outbox`
--

LOCK TABLES `wf_process_outbox` WRITE;
/*!40000 ALTER TABLE `wf_process_outbox` DISABLE KEYS */;
/*!40000 ALTER TABLE `wf_process_outbox` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:16:01
