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
-- Table structure for table `op_dispatch_log`
--

DROP TABLE IF EXISTS `op_dispatch_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_dispatch_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `task_id` varchar(64) DEFAULT NULL COMMENT 'Flowable任务ID',
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '业务单据类型',
  `biz_id` bigint DEFAULT NULL COMMENT '业务单据ID',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '业务单据号',
  `model_key` varchar(64) DEFAULT NULL COMMENT '流程定义key',
  `node_key` varchar(64) DEFAULT NULL COMMENT '节点key',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `dispatch_mode` varchar(16) NOT NULL DEFAULT 'STRATEGY' COMMENT '分配方式:RULE规则命中/STRATEGY策略选人/PICK抢单池/MANUAL人工指定',
  `matched_rule_id` bigint DEFAULT NULL COMMENT '命中的分配规则ID（op_dispatch_rule）',
  `matched_rule_code` varchar(64) DEFAULT NULL COMMENT '命中的分配规则编码',
  `matched_rule_name` varchar(128) DEFAULT NULL COMMENT '命中的分配规则名称',
  `policy_id` bigint DEFAULT NULL COMMENT '命中的策略配置ID（op_dispatch_policy）',
  `strategy` varchar(24) DEFAULT NULL COMMENT '采用的分配策略',
  `target_group_id` bigint DEFAULT NULL COMMENT '目标分组ID（op_group）',
  `candidate_snapshot` text COMMENT '候选快照（用户ID:姓名:在手量:预计耗时 列表），决策依据留证',
  `chosen_user_id` bigint DEFAULT NULL COMMENT '最终选中的办理人ID（抢单模式下为空）',
  `chosen_user_name` varchar(64) DEFAULT NULL COMMENT '最终选中的办理人姓名（冗余）',
  `decision_reason` varchar(1000) DEFAULT NULL COMMENT '决策说明：为什么选他/为什么不选人',
  `success` tinyint NOT NULL DEFAULT '1' COMMENT '本次分配是否成功:1成功 0失败',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_odl_tenant_task` (`tenant_id`,`task_id`),
  KEY `idx_odl_tenant_biz` (`tenant_id`,`biz_type`,`biz_id`),
  KEY `idx_odl_tenant_time` (`tenant_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=700000091 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='G3 自动分配日志（可追"这单为什么派给了他"）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_dispatch_log`
--

LOCK TABLES `op_dispatch_log` WRITE;
/*!40000 ALTER TABLE `op_dispatch_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_dispatch_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:15:49
