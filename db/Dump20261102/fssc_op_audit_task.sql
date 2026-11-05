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
-- Table structure for table `op_audit_task`
--

DROP TABLE IF EXISTS `op_audit_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_audit_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `bill_id` bigint NOT NULL COMMENT '单据主表ID',
  `bill_no` varchar(64) DEFAULT NULL COMMENT '单据号',
  `bill_type` varchar(20) NOT NULL COMMENT '单据类型',
  `title` varchar(300) DEFAULT NULL COMMENT '事由',
  `amount` decimal(20,2) DEFAULT NULL COMMENT '金额',
  `dept_id` bigint DEFAULT NULL COMMENT '承担部门',
  `apply_user_id` bigint DEFAULT NULL COMMENT '申请人',
  `priority` int NOT NULL DEFAULT '5' COMMENT '优先级,数值小优先(加急单=1)',
  `task_type` varchar(20) NOT NULL DEFAULT 'AUDIT' COMMENT '任务类型:AUDIT初审/REAUDIT复审/FILE档案/PAY支付',
  `assignee_id` bigint DEFAULT NULL COMMENT '分配到的共享会计',
  `pool_status` varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT '状态:WAITING待分配/ASSIGNED已分配/PROCESSING处理中/DONE已完成/BACK退回',
  `sla_time` datetime DEFAULT NULL COMMENT 'SLA时限(超时预警)',
  `receive_time` datetime DEFAULT NULL COMMENT '领取时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `chain_seq` int DEFAULT '1' COMMENT '作业链序号(1审核/2付款/3记账)',
  `chain_total` int DEFAULT '1' COMMENT '作业链总环节数',
  `active_flag` varchar(8) DEFAULT 'Y' COMMENT '是否可认领 Y/N(前序未完成则N)',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_status` (`tenant_id`,`pool_status`),
  KEY `idx_tenant_assignee` (`tenant_id`,`assignee_id`,`pool_status`),
  KEY `idx_tenant_bill_type` (`tenant_id`,`bill_id`,`task_type`),
  KEY `idx_tenant_pool_del` (`tenant_id`,`pool_status`,`del_flag`),
  KEY `idx_oat_tenant_del_type_ft` (`tenant_id`,`del_flag`,`task_type`,`finish_time`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='共享审核任务池';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_audit_task`
--

LOCK TABLES `op_audit_task` WRITE;
/*!40000 ALTER TABLE `op_audit_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_audit_task` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:29:42
