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
-- Table structure for table `biz_form_bill`
--

DROP TABLE IF EXISTS `biz_form_bill`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_form_bill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `form_code` varchar(50) NOT NULL COMMENT '表单编码 md_form_config.form_code',
  `bill_no` varchar(64) DEFAULT NULL COMMENT '单据编号（sys_number_rule 按 form_code 取号）',
  `title` varchar(200) DEFAULT NULL COMMENT '流程标题',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '起流时使用的 biz_type（快照，便于事后追溯）',
  `apply_user_id` bigint DEFAULT NULL COMMENT '申请人',
  `apply_dept_id` bigint DEFAULT NULL COMMENT '申请部门',
  `company_id` bigint DEFAULT NULL COMMENT '所属公司',
  `status` varchar(24) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED/CANCELED',
  `form_data` json DEFAULT NULL COMMENT '按 form_code 元数据渲染的字段键值',
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `current_node` varchar(200) DEFAULT NULL COMMENT '当前节点（冗余，便于列表展示）',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `finish_time` datetime DEFAULT NULL COMMENT '办结时间',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_bfb_tenant_form` (`tenant_id`,`form_code`,`status`,`create_time`),
  KEY `idx_bfb_tenant_apply` (`tenant_id`,`apply_user_id`,`create_time`),
  KEY `idx_bfb_proc` (`proc_inst_id`),
  KEY `idx_bfb_billno` (`tenant_id`,`bill_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可配置表单通用单据（form_data JSON，新增表单不改表结构）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_form_bill`
--

LOCK TABLES `biz_form_bill` WRITE;
/*!40000 ALTER TABLE `biz_form_bill` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_form_bill` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:23:47
