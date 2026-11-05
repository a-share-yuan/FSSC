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
-- Table structure for table `op_group`
--

DROP TABLE IF EXISTS `op_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_group` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `group_code` varchar(50) NOT NULL,
  `group_name` varchar(100) NOT NULL,
  `biz_type` varchar(20) NOT NULL DEFAULT 'EXPENSE' COMMENT 'EXPENSE费用组/FUND资金组/AR应收组/AP应付组',
  `leader_id` bigint DEFAULT NULL COMMENT '负责人用户ID',
  `member_min` int DEFAULT '5' COMMENT '成员任务数下限',
  `member_max` int DEFAULT '50' COMMENT '成员任务数上限',
  `task_mode` varchar(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL手动提取/SYSTEM系统提取',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `group_type` varchar(20) DEFAULT NULL COMMENT '组类型：BY_TYPE/BY_AMOUNT/BY_LEGAL/BY_REGION',
  `group_config` text COMMENT '组配置JSON',
  `member_count` int DEFAULT '0' COMMENT '成员数',
  `is_auto_dispatch` tinyint DEFAULT '0' COMMENT '是否自动派单：1-是 0-否',
  `priority` int DEFAULT '0' COMMENT '优先级',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_op_group` (`tenant_id`,`group_code`)
) ENGINE=InnoDB AUTO_INCREMENT=700000005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='共享业务分组';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_group`
--

LOCK TABLES `op_group` WRITE;
/*!40000 ALTER TABLE `op_group` DISABLE KEYS */;
INSERT INTO `op_group` VALUES (700000001,1,'GRP-EXP','费用审核组','EXPENSE',3853,3,30,'SYSTEM','NORMAL','V1.4.81 造数组',NULL,'2026-10-18 10:56:58',NULL,'2026-10-18 10:56:58',0,NULL,NULL,4,1,1),(700000002,1,'GRP-FUND','资金付款组','FUND',3800,3,30,'SYSTEM','NORMAL','V1.4.81 造数组',NULL,'2026-10-18 10:56:58',NULL,'2026-10-18 10:56:58',0,NULL,NULL,9,1,2),(700000003,1,'GRP-AR','应收收款组','AR',3837,3,30,'SYSTEM','NORMAL','V1.4.81 造数组',NULL,'2026-10-18 10:56:58',NULL,'2026-10-18 10:56:58',0,NULL,NULL,11,1,3),(700000004,1,'GRP-AP','应付付款组','AP',3823,3,30,'SYSTEM','NORMAL','V1.4.81 造数组',NULL,'2026-10-18 10:56:58',NULL,'2026-10-18 10:56:58',0,NULL,NULL,8,1,2);
/*!40000 ALTER TABLE `op_group` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:30:39
