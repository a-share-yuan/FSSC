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
-- Table structure for table `op_archive`
--

DROP TABLE IF EXISTS `op_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `archive_no` varchar(64) NOT NULL COMMENT '档案编号',
  `archive_code` varchar(64) DEFAULT NULL COMMENT '档案编码(冗余)',
  `archive_type` varchar(20) NOT NULL DEFAULT 'BILL' COMMENT '档案类型:BILL单据/VOUCHER凭证/CONTRACT合同/REPORT报表/OTHER',
  `archive_year` int DEFAULT NULL COMMENT '档案年度',
  `archive_period` varchar(20) DEFAULT NULL COMMENT '档案期间(YYYY-MM)',
  `ref_id` bigint NOT NULL COMMENT '关联主键(单据ID/凭证ID)',
  `bill_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `voucher_id` bigint DEFAULT NULL COMMENT '来源凭证ID',
  `related_ids` varchar(1000) DEFAULT NULL COMMENT '关联ID列表JSON',
  `ref_no` varchar(64) DEFAULT NULL COMMENT '关联单号',
  `title` varchar(300) DEFAULT NULL COMMENT '档案标题',
  `company_id` bigint DEFAULT NULL COMMENT '所属公司',
  `period_year` int DEFAULT NULL COMMENT '会计年度',
  `period_month` int DEFAULT NULL COMMENT '会计月份',
  `file_id` bigint DEFAULT NULL COMMENT '归档文件ID(sys_file)',
  `filing_user_id` bigint DEFAULT NULL COMMENT '归档人',
  `filing_time` datetime DEFAULT NULL COMMENT '归档时间',
  `retention_years` int DEFAULT '10' COMMENT '保管年限',
  `destroy_date` date DEFAULT NULL COMMENT '到期销毁日期',
  `integrity_hash` varchar(64) DEFAULT NULL COMMENT '完整性哈希',
  `integrity_status` varchar(20) DEFAULT 'UNKNOWN' COMMENT '完整性状态:VERIFIED/MISMATCH/UNKNOWN',
  `archive_status` varchar(20) DEFAULT 'PENDING' COMMENT '归档状态:PENDING/ARCHIVING/ARCHIVED/DISPOSED',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  `destroyed_at` datetime DEFAULT NULL COMMENT '销毁时间',
  `status` varchar(20) NOT NULL DEFAULT 'FILED' COMMENT '状态:FILED已归档/EXPIRED到期/DESTROYED已销毁',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT 'source wf process instance id (D-TRC-05)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_archive_no` (`tenant_id`,`archive_no`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_ref` (`tenant_id`,`archive_type`,`ref_id`),
  KEY `idx_proc_inst_id` (`proc_inst_id`)
) ENGINE=InnoDB AUTO_INCREMENT=188 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电子会计档案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_archive`
--

LOCK TABLES `op_archive` WRITE;
/*!40000 ALTER TABLE `op_archive` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_archive` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:15:53
