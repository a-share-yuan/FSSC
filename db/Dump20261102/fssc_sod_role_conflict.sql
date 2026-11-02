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
-- Table structure for table `sod_role_conflict`
--

DROP TABLE IF EXISTS `sod_role_conflict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sod_role_conflict` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT 'NULL=平台级规则(全租户生效)；非空=租户自定义规则',
  `role_code_a` varchar(64) NOT NULL COMMENT '互斥角色编码A',
  `role_code_b` varchar(64) NOT NULL COMMENT '互斥角色编码B',
  `reason` varchar(200) DEFAULT NULL COMMENT '不相容原因说明',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sod_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SoD 不相容职务分离互斥规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sod_role_conflict`
--

LOCK TABLES `sod_role_conflict` WRITE;
/*!40000 ALTER TABLE `sod_role_conflict` DISABLE KEYS */;
INSERT INTO `sod_role_conflict` VALUES (1,NULL,'CASHIER','FIN_GL','出纳不得兼总账会计（钱账分管）','2026-08-29 09:31:39'),(2,NULL,'CASHIER','FSSC_AUDITOR','出纳不得兼共享会计（管钱不管账）','2026-08-29 09:31:39'),(3,NULL,'FSSC_AUDITOR','FSSC_SUPERVISOR','制单与复核不得同一人（共享作业链分离）','2026-08-29 09:31:39'),(4,NULL,'FIN_GL','FSSC_AUDITOR','记账与审核不得同一人','2026-08-29 09:31:39'),(5,NULL,'FM_ARCHIVIST','CFO','档案监销与审批不得同一人','2026-08-29 09:31:39'),(6,NULL,'TAX_MANAGER','CASHIER','税务经理不得兼出纳','2026-08-29 09:31:39'),(7,NULL,'CASHIER','FIN_AP','出纳不得兼应付会计（钱账分管）','2026-09-18 21:21:33'),(8,NULL,'FIN_AP','FIN_GL','制单与过账不得同一人（凭证制作/记账分离）','2026-09-18 21:21:33'),(9,NULL,'FIN_AP','FSSC_SUPERVISOR','制单与复核不得同一人（作业链分离）','2026-09-18 21:21:33'),(10,NULL,'FUND_PAY_STAFF','FIN_GL','资金支付与记账不得同一人','2026-09-18 21:21:33'),(13,NULL,'FM_ARCHIVIST','FIN_MANAGER','档案销毁与财务审批不得同一人（监销≠审批）','2026-09-18 21:21:33'),(18,1,'DEPT_MANAGER','DEPT_MANAGER#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：部门经理 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(19,1,'FIN_AUDIT','FIN_AUDIT#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：财务审核岗 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(20,1,'FIN_MANAGER','FIN_MANAGER#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：财务经理 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(21,1,'BIZ_FIN_MANAGER','BIZ_FIN_MANAGER#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：业务财务经理 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(22,1,'CFO','CFO#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：财务总监 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(23,1,'FIN_DIRECTOR','FIN_DIRECTOR#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：板块财务总监 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(24,1,'FSSC_AUDITOR','FSSC_AUDITOR#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：共享会计 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(25,1,'FSSC_SUPERVISOR','FSSC_SUPERVISOR#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：共享主管 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(26,1,'CENTER_DIRECTOR','CENTER_DIRECTOR#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：中心总监 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(27,1,'BASE_FIN_MANAGER','BASE_FIN_MANAGER#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：基地财务经理 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(28,1,'PLATE_FIN_MANAGER','PLATE_FIN_MANAGER#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：板块财务经理 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(29,1,'PROJECT_ACCOUNTANT','PROJECT_ACCOUNTANT#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：项目会计 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(30,1,'CENTER_APPROVER','CENTER_APPROVER#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：中心/总监级审批档 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(31,1,'EXEC_APPROVER','EXEC_APPROVER#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：高管决策审批档 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27'),(32,1,'FIN_MGR_ORG','FIN_MGR_ORG#INTRA_ROLE','角色内共存 提交(exp:bill:submit)+审核(exp:bill:audit)：基层/板块财务经理档 —— 中危巡检项，deny_self 兜底，季度复核','2026-09-19 20:16:27');
/*!40000 ALTER TABLE `sod_role_conflict` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:14:04
