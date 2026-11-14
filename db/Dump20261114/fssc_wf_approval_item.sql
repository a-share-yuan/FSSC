CREATE DATABASE  IF NOT EXISTS `fssc` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `fssc`;
-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: 192.168.20.77    Database: fssc
-- ------------------------------------------------------
-- Server version	8.0.46-0ubuntu0.22.04.5

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
-- Table structure for table `wf_approval_item`
--

DROP TABLE IF EXISTS `wf_approval_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_approval_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `model_key` varchar(64) NOT NULL COMMENT '流程定义key（与 wf_node_config.model_key 同口径）',
  `node_key` varchar(64) NOT NULL COMMENT '节点ID(BPMN userTask id)；与 wf_node_config.node_key 一样精确匹配，孤儿行不生效',
  `item_code` varchar(64) NOT NULL COMMENT '审批项编码（同一节点内唯一，落流程变量 ActionResult，供 BPMN 条件 ${ActionResult == ''x''} 使用）',
  `item_name` varchar(64) NOT NULL COMMENT '审批项名称（审批弹窗按钮文案，落流程变量 ActionResultName）',
  `category` varchar(16) NOT NULL DEFAULT 'POSITIVE' COMMENT '类别:POSITIVE正向(通过并继续)/REJECT驳回(可逆向运行)/TERMINATE终止',
  `linked_oper` varchar(16) DEFAULT NULL COMMENT '关联表单操作:SAVE保存/AUDIT审核/UNAUDIT反审核/SUBMIT提交；仅落流程变量 ActionOper 供业务域订阅，引擎侧不执行表单写操作',
  `target_node` varchar(64) DEFAULT NULL COMMENT '目标节点key（仅 REJECT 有意义）:非空且驳回闸门放行时退回该上游节点，为空则终止本流程',
  `sort` int NOT NULL DEFAULT '0' COMMENT '同一节点内展示顺序(升序)',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用:1启用 0停用（停用项不进审批弹窗、也解析不到）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wai_tenant_node_code` (`tenant_id`,`model_key`,`node_key`,`item_code`),
  KEY `idx_wai_tenant_model_node` (`tenant_id`,`model_key`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='G2 流程节点审批项';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wf_approval_item`
--

LOCK TABLES `wf_approval_item` WRITE;
/*!40000 ALTER TABLE `wf_approval_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `wf_approval_item` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:28:24
