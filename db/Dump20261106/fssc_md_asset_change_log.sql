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
-- Table structure for table `md_asset_change_log`
--

DROP TABLE IF EXISTS `md_asset_change_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_asset_change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `asset_id` bigint NOT NULL,
  `asset_no` varchar(64) DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `dept_id` bigint DEFAULT NULL,
  `change_type` varchar(30) NOT NULL COMMENT 'VALUE_ADJUST 原值调整/CHECK_DIFF 盘点差异/IMPAIR 减值/DISPOSE 处置/TRANSFER 调拨',
  `change_reason` varchar(500) DEFAULT NULL COMMENT '变动原因（人工输入，非硬编码兜底句）',
  `change_method` varchar(50) DEFAULT NULL COMMENT '变动方式（取值域待定义，当前无写入方＝恒 NULL）',
  `before_original_amount` decimal(20,2) DEFAULT NULL,
  `after_original_amount` decimal(20,2) DEFAULT NULL,
  `before_net_amount` decimal(20,2) DEFAULT NULL,
  `after_net_amount` decimal(20,2) DEFAULT NULL,
  `delta_amount` decimal(20,2) DEFAULT NULL COMMENT '幅度：after-before，盘点差异＝差异额（可负＝盘亏）',
  `status` varchar(20) DEFAULT NULL COMMENT 'APPROVING 在途/APPROVED 通过/REJECTED 驳回',
  `remark` varchar(500) DEFAULT NULL,
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT 'Flowable 流程实例号（起流返回，终态回写键）',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_macl_asset` (`tenant_id`,`asset_id`,`del_flag`),
  KEY `idx_macl_proc` (`proc_inst_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产变动台账（原值调整/盘点差异等原因与幅度载体，D-DEC-431）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_asset_change_log`
--

LOCK TABLES `md_asset_change_log` WRITE;
/*!40000 ALTER TABLE `md_asset_change_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `md_asset_change_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:30
