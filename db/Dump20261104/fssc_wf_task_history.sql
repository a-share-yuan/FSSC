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
-- Table structure for table `wf_task_history`
--

DROP TABLE IF EXISTS `wf_task_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_task_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `proc_inst_id` varchar(64) NOT NULL COMMENT '流程实例ID',
  `task_id` varchar(64) DEFAULT NULL COMMENT '任务ID',
  `node_key` varchar(64) DEFAULT NULL COMMENT '节点key',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `biz_type` varchar(50) NOT NULL COMMENT '业务单据类型',
  `biz_id` bigint DEFAULT NULL,
  `action` varchar(20) NOT NULL COMMENT '动作:SUBMIT提交/APPROVE同意/REJECT驳回/TRANSFER转办/ADDSIGN加签/CANCEL撤销/URGE催办/AUTO_PASS自动通过/TERMINATE终止/ASSIGN上一环节指定办理人(PICK留痕)',
  `oper_user_id` bigint DEFAULT NULL COMMENT '操作人用户ID',
  `oper_user_name` varchar(64) DEFAULT NULL COMMENT '操作人姓名(冗余,防人员离职后查不到)',
  `oper_dept_name` varchar(100) DEFAULT NULL COMMENT '操作人部门(冗余)',
  `target_user_id` bigint DEFAULT NULL COMMENT '目标人(转办/加签/ASSIGN点名时)',
  `comment` varchar(2000) DEFAULT NULL COMMENT '审批意见',
  `attach_ids` varchar(500) DEFAULT NULL COMMENT '意见附件ID,逗号分隔',
  `duration` bigint DEFAULT NULL COMMENT '本节点停留时长(毫秒)',
  `oper_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`,`oper_time`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_inst` (`tenant_id`,`proc_inst_id`),
  KEY `idx_tenant_biz` (`tenant_id`,`biz_type`,`biz_id`),
  KEY `idx_tenant_user` (`tenant_id`,`oper_user_id`),
  KEY `idx_tenant_oper` (`tenant_id`,`oper_time`),
  KEY `idx_tenant_inst_oper` (`tenant_id`,`proc_inst_id`,`oper_time`,`id`),
  KEY `idx_tenant_bizid` (`tenant_id`,`biz_id`,`id`),
  KEY `idx_wth_tenant_action_oper` (`tenant_id`,`action`,`oper_time`,`id`),
  KEY `idx_wth_tenant_target` (`tenant_id`,`target_user_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=604 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按月分区'
/*!50500 PARTITION BY RANGE  COLUMNS(oper_time)
(PARTITION p202601 VALUES LESS THAN ('2026-02-01') ENGINE = InnoDB,
 PARTITION p202602 VALUES LESS THAN ('2026-03-01') ENGINE = InnoDB,
 PARTITION p202603 VALUES LESS THAN ('2026-04-01') ENGINE = InnoDB,
 PARTITION p202604 VALUES LESS THAN ('2026-05-01') ENGINE = InnoDB,
 PARTITION p202605 VALUES LESS THAN ('2026-06-01') ENGINE = InnoDB,
 PARTITION p202606 VALUES LESS THAN ('2026-07-01') ENGINE = InnoDB,
 PARTITION p202607 VALUES LESS THAN ('2026-08-01') ENGINE = InnoDB,
 PARTITION p202608 VALUES LESS THAN ('2026-09-01') ENGINE = InnoDB,
 PARTITION p202609 VALUES LESS THAN ('2026-10-01') ENGINE = InnoDB,
 PARTITION p202610 VALUES LESS THAN ('2026-11-01') ENGINE = InnoDB,
 PARTITION p202611 VALUES LESS THAN ('2026-12-01') ENGINE = InnoDB,
 PARTITION p202612 VALUES LESS THAN ('2027-01-01') ENGINE = InnoDB,
 PARTITION p202701 VALUES LESS THAN ('2027-02-01') ENGINE = InnoDB,
 PARTITION p202702 VALUES LESS THAN ('2027-03-01') ENGINE = InnoDB,
 PARTITION p202703 VALUES LESS THAN ('2027-04-01') ENGINE = InnoDB,
 PARTITION p202704 VALUES LESS THAN ('2027-05-01') ENGINE = InnoDB,
 PARTITION p202705 VALUES LESS THAN ('2027-06-01') ENGINE = InnoDB,
 PARTITION p202706 VALUES LESS THAN ('2027-07-01') ENGINE = InnoDB,
 PARTITION p202707 VALUES LESS THAN ('2027-08-01') ENGINE = InnoDB,
 PARTITION p202708 VALUES LESS THAN ('2027-09-01') ENGINE = InnoDB,
 PARTITION p202709 VALUES LESS THAN ('2027-10-01') ENGINE = InnoDB,
 PARTITION p202710 VALUES LESS THAN ('2027-11-01') ENGINE = InnoDB,
 PARTITION p202711 VALUES LESS THAN ('2027-12-01') ENGINE = InnoDB,
 PARTITION p202712 VALUES LESS THAN ('2028-01-01') ENGINE = InnoDB,
 PARTITION pMAX VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB) */;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wf_task_history`
--

LOCK TABLES `wf_task_history` WRITE;
/*!40000 ALTER TABLE `wf_task_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `wf_task_history` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:30:46
