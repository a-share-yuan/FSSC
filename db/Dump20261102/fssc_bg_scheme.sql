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
-- Table structure for table `bg_scheme`
--

DROP TABLE IF EXISTS `bg_scheme`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bg_scheme` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `company_id` bigint NOT NULL COMMENT '公司ID',
  `scheme_code` varchar(64) NOT NULL COMMENT '方案编码',
  `scheme_name` varchar(200) NOT NULL COMMENT '方案名称,如 2026年度费用预算',
  `budget_year` int NOT NULL COMMENT '预算年度',
  `period_type` varchar(20) NOT NULL DEFAULT 'MONTH' COMMENT '预算周期:MONTH月度/QUARTER季度/YEAR年度',
  `dimension` varchar(200) NOT NULL DEFAULT 'DEPT,ITEM' COMMENT '控制维度组合,逗号分隔:DEPT部门/ITEM费用项目/PROJECT项目/COMPANY公司',
  `control_type` varchar(20) NOT NULL DEFAULT 'WARN' COMMENT '控制方式:NONE不控/WARN超额提醒/BLOCK超额禁止',
  `carry_over` tinyint NOT NULL DEFAULT '0' COMMENT '余额是否结转下期:0否 1是',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT草稿/ENABLED启用/CLOSED已关闭',
  `remark` varchar(500) DEFAULT NULL,
  `issue_time` datetime DEFAULT NULL COMMENT '下达时间(CL-02 流程通过/手动下达)',
  `issue_by` bigint DEFAULT NULL COMMENT '下达人(sys_user.id)',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_scheme_code` (`tenant_id`,`scheme_code`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_year` (`tenant_id`,`budget_year`)
) ENGINE=InnoDB AUTO_INCREMENT=111 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预算方案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bg_scheme`
--

LOCK TABLES `bg_scheme` WRITE;
/*!40000 ALTER TABLE `bg_scheme` DISABLE KEYS */;
INSERT INTO `bg_scheme` VALUES (101,1,1,'BG202611','2026年11月集团预算方案',2026,'MONTH','DEPT,ITEM','SOFT',1,'ISSUED','','2026-09-29 11:24:32',3925,3925,'2026-09-29 11:06:58',3925,'2026-10-02 16:32:49',0),(102,1,1,'BGS1790746090306','2026年9月集团预算方案（第2轮Act2重建）',2026,'MONTH','DEPT,ITEM','SOFT',1,'ISSUED','Act2 PREPAY 预算腿：并发会话清池后重建 202609 期方案；原方案 100 已删、8 条明细悬空','2026-09-30 13:31:08',3837,3837,'2026-09-30 13:28:10',3837,'2026-10-02 16:32:49',0),(108,1,1,'BGS1793640405450','E2E-年度方案',2026,'YEAR','ITEM','WARN',0,'ISSUED',NULL,'2026-11-03 01:26:51',1,1,'2026-11-03 01:26:45',1,'2026-11-03 01:26:51',0),(109,1,1,'BGS1793640426223','E2E-免审下达校验',2026,'MONTH','ITEM','WARN',0,'DRAFT',NULL,NULL,NULL,1,'2026-11-03 01:27:06',1,'2026-11-03 01:27:06',0),(110,1,1,'BGS1793640431837','E2E-空方案下达',2026,'MONTH','ITEM','WARN',0,'DRAFT',NULL,NULL,NULL,1,'2026-11-03 01:27:12',1,'2026-11-03 01:27:12',0);
/*!40000 ALTER TABLE `bg_scheme` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:25
