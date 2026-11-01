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
-- Table structure for table `gl_journal_entry`
--

DROP TABLE IF EXISTS `gl_journal_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gl_journal_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `entry_no` varchar(64) DEFAULT NULL COMMENT '记账单号',
  `entry_type` varchar(32) DEFAULT NULL COMMENT 'ACCRUAL/DEFER/MANUAL/OTHER_RECEIPT',
  `period` varchar(16) DEFAULT NULL COMMENT '会计期间 yyyyMM',
  `summary` varchar(200) DEFAULT NULL COMMENT '凭证摘要',
  `debit_subject_id` bigint DEFAULT NULL COMMENT '借方科目 md_subject.id',
  `credit_subject_id` bigint DEFAULT NULL COMMENT '贷方科目 md_subject.id',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '金额',
  `status` varchar(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/POSTED',
  `voucher_no` varchar(64) DEFAULT NULL COMMENT '记账凭证号',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否/1是',
  `company_id` bigint DEFAULT NULL COMMENT '记账公司(组织合法性 V1.3.18)',
  PRIMARY KEY (`id`),
  KEY `idx_gl_jrn_period` (`tenant_id`,`period`),
  KEY `idx_gl_jrn_type` (`entry_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一般总账记账';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gl_journal_entry`
--

LOCK TABLES `gl_journal_entry` WRITE;
/*!40000 ALTER TABLE `gl_journal_entry` DISABLE KEYS */;
/*!40000 ALTER TABLE `gl_journal_entry` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:06:12
