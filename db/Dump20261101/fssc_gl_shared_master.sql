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
-- Table structure for table `gl_shared_master`
--

DROP TABLE IF EXISTS `gl_shared_master`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gl_shared_master` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `req_no` varchar(64) DEFAULT NULL COMMENT '维护申请单号',
  `type` varchar(32) DEFAULT NULL COMMENT 'SUBJECT科目/COST_CENTER成本中心/BANK银行/OTHER其他',
  `period` varchar(16) DEFAULT NULL COMMENT '会计期间 yyyyMM',
  `subject_id` bigint DEFAULT NULL COMMENT '关联科目 md_subject.id',
  `subject_code` varchar(32) DEFAULT NULL COMMENT '科目编码',
  `subject_name` varchar(120) DEFAULT NULL COMMENT '科目名称',
  `action` varchar(16) DEFAULT 'ADD' COMMENT 'ADD/MODIFY/DELETE',
  `description` varchar(200) DEFAULT NULL COMMENT '变更说明',
  `status` varchar(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVED',
  `voucher_no` varchar(64) DEFAULT NULL COMMENT '记账凭证号',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否/1是',
  PRIMARY KEY (`id`),
  KEY `idx_gl_sm_period` (`tenant_id`,`period`),
  KEY `idx_gl_sm_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='GL主数据维护';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gl_shared_master`
--

LOCK TABLES `gl_shared_master` WRITE;
/*!40000 ALTER TABLE `gl_shared_master` DISABLE KEYS */;
/*!40000 ALTER TABLE `gl_shared_master` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:06:04
