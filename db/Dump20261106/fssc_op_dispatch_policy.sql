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
-- Table structure for table `op_dispatch_policy`
--

DROP TABLE IF EXISTS `op_dispatch_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_dispatch_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `model_key` varchar(64) NOT NULL DEFAULT '*' COMMENT '流程定义key；* 表示全局默认行',
  `node_key` varchar(64) NOT NULL DEFAULT '*' COMMENT '节点ID(BPMN userTask id)；* 表示全局默认行',
  `policy_name` varchar(128) DEFAULT NULL COMMENT '策略名称',
  `strategy` varchar(24) NOT NULL DEFAULT 'PICK' COMMENT '分配策略:PICK抢单/BALANCE_TIME均衡按时/BALANCE_VOLUME均衡按量/SMART智能',
  `normal_hours` decimal(8,2) NOT NULL DEFAULT '24.00' COMMENT '普通任务时限(小时)，缺省 24h',
  `urgent_hours` decimal(8,2) NOT NULL DEFAULT '4.00' COMMENT '加急任务时限(小时)，缺省 4h',
  `balance_interval_hours` decimal(8,2) DEFAULT '1.00' COMMENT '均衡·按时的分配间隔(小时)，缺省 1h 一分',
  `balance_batch_size` int DEFAULT '10' COMMENT '均衡·按量的累积批大小(单)，缺省 10 单一分配',
  `smart_weight_volume` decimal(4,2) DEFAULT '0.50' COMMENT '智能策略权重:在手量',
  `smart_weight_urgency` decimal(4,2) DEFAULT '0.30' COMMENT '智能策略权重:时限紧迫度',
  `smart_weight_priority` decimal(4,2) DEFAULT '0.20' COMMENT '智能策略权重:任务优先级',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用:1启用 0停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注（说明用途，回滚时可据此核对）',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_odp_tenant_node` (`tenant_id`,`model_key`,`node_key`),
  KEY `idx_odp_tenant_strategy` (`tenant_id`,`strategy`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='G3 共享作业分配策略与双时限配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_dispatch_policy`
--

LOCK TABLES `op_dispatch_policy` WRITE;
/*!40000 ALTER TABLE `op_dispatch_policy` DISABLE KEYS */;
INSERT INTO `op_dispatch_policy` VALUES (1,1,'*','*','全局默认分配策略','PICK',24.00,4.00,1.00,10,0.50,0.30,0.20,1,'未命中节点级策略时的兜底：抢单 + 普通24h/加急4h（内部口径）',NULL,'2026-09-24 21:32:46',NULL,'2026-10-10 22:45:24',0);
/*!40000 ALTER TABLE `op_dispatch_policy` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:36
