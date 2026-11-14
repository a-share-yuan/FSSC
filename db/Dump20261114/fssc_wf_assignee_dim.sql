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
-- Table structure for table `wf_assignee_dim`
--

DROP TABLE IF EXISTS `wf_assignee_dim`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_assignee_dim` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `scheme_id` bigint NOT NULL COMMENT '所属方案ID（wf_assignee_scheme.id）',
  `dim_type` varchar(20) NOT NULL COMMENT '维度类型:USER指定人/ROLE角色/POST岗位/DEPT部门/DEPT_TREE部门含下级/DEPT_LEADER部门负责人/REPORT_TO汇报关系/VARIABLE参与人变量',
  `dim_value` varchar(500) DEFAULT NULL COMMENT '维度取值,多个逗号分隔;USER=用户ID/ROLE=角色编码/POST=岗位ID或编码/DEPT系列=部门ID/REPORT_TO=主体用户ID(空=发起人)/VARIABLE=流程变量名',
  `relation` varchar(20) DEFAULT NULL COMMENT '仅REPORT_TO:DIRECT_UP直接上级/INDIRECT_UP间接上级/PEER平级/DIRECT_DOWN直接下级/INDIRECT_DOWN间接下级',
  `up_level` int DEFAULT NULL COMMENT '仅REPORT_TO:级数(1=直接,2=隔一级…;与wf_node_config.up_level同口径,最多10)',
  `include_children` tinyint DEFAULT '0' COMMENT '仅DEPT:是否含下级部门(0否 1是);DEPT_TREE恒视为1',
  `sort` int NOT NULL DEFAULT '0' COMMENT '方案内排序,越小越先(影响AND/EXCLUDE基集选择与预览回显顺序)',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_wad_tenant_scheme` (`tenant_id`,`scheme_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='G2 参与人方案取人维度（一行一个维度，可追溯"为什么解析到他"）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wf_assignee_dim`
--

LOCK TABLES `wf_assignee_dim` WRITE;
/*!40000 ALTER TABLE `wf_assignee_dim` DISABLE KEYS */;
INSERT INTO `wf_assignee_dim` VALUES (1,1,1,'ROLE','FSSC_AUDITOR',NULL,NULL,0,10,NULL,'2026-10-15 08:39:27',NULL,'2026-10-15 08:39:27',0),(2,1,1,'POST','J0060',NULL,NULL,0,20,NULL,'2026-10-15 08:39:27',NULL,'2026-10-15 21:15:56',0),(3,1,2,'ROLE','FSSC_AUDITOR',NULL,NULL,0,10,NULL,'2026-10-24 08:41:11',NULL,'2026-10-24 08:41:11',0),(4,1,2,'POST','J0060',NULL,NULL,0,20,NULL,'2026-10-24 08:41:11',NULL,'2026-10-24 08:41:11',0);
/*!40000 ALTER TABLE `wf_assignee_dim` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:28:22
