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
-- Table structure for table `wf_assignee_scheme`
--

DROP TABLE IF EXISTS `wf_assignee_scheme`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_assignee_scheme` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `model_key` varchar(64) NOT NULL COMMENT '流程定义key（与 wf_node_config.model_key 同源）',
  `node_key` varchar(64) NOT NULL COMMENT '节点ID（BPMN userTask id，与 wf_node_config.node_key 同源，精确匹配）',
  `priority` int NOT NULL DEFAULT '1' COMMENT '优先级:数值小者先试,高优先级解析不出人时降级到下一优先级',
  `condition_expr` varchar(1000) DEFAULT NULL COMMENT '条件表达式(空=无条件适用);文法见实体javadoc,刻意不接SpEL',
  `combine_mode` varchar(16) NOT NULL DEFAULT 'OR' COMMENT '维度组合方式:OR并集(默认)/AND交集/EXCLUDE从节点候选组扣除',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用:1启用 0停用(停用行不参与解析,便于灰度/回滚)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '同优先级内排序,越小越先',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_was_tenant_node` (`tenant_id`,`model_key`,`node_key`),
  KEY `idx_was_tenant_enabled` (`tenant_id`,`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='G2 节点参与人方案（多套方案按优先级+条件降级解析）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wf_assignee_scheme`
--

LOCK TABLES `wf_assignee_scheme` WRITE;
/*!40000 ALTER TABLE `wf_assignee_scheme` DISABLE KEYS */;
INSERT INTO `wf_assignee_scheme` VALUES (1,1,'CLAIM','Task_7',1,NULL,'AND',1,10,NULL,'2026-10-15 08:39:27',NULL,'2026-10-15 08:39:27',0),(2,1,'CLAIM_NC','Task_7',1,NULL,'AND',1,10,NULL,'2026-10-24 08:41:11',NULL,'2026-10-24 08:41:11',0);
/*!40000 ALTER TABLE `wf_assignee_scheme` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:14:17
