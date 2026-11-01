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
-- Table structure for table `op_gl_entry`
--

DROP TABLE IF EXISTS `op_gl_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_gl_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `company_id` bigint NOT NULL COMMENT '公司ID',
  `period_year` int NOT NULL COMMENT '会计年度',
  `period_month` int NOT NULL COMMENT '会计期间',
  `voucher_id` bigint NOT NULL COMMENT '凭证ID（op_voucher.id）',
  `voucher_no` varchar(50) NOT NULL COMMENT '凭证号',
  `voucher_line_id` bigint NOT NULL COMMENT '凭证明细行ID（op_voucher_line.id）',
  `subject_id` bigint NOT NULL COMMENT '科目ID（md_subject.id）',
  `subject_code` varchar(30) NOT NULL COMMENT '科目编码',
  `subject_name` varchar(100) NOT NULL COMMENT '科目名称',
  `direction` varchar(10) NOT NULL COMMENT '借贷方向 DEBIT/CREDIT',
  `amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '发生金额',
  `summary` varchar(300) DEFAULT NULL COMMENT '摘要',
  `biz_id` bigint DEFAULT NULL COMMENT '业务单据ID',
  `biz_type` varchar(30) DEFAULT NULL COMMENT '业务类型',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_period` (`tenant_id`,`period_year`,`period_month`),
  KEY `idx_company_subject` (`company_id`,`period_year`,`period_month`,`subject_id`),
  KEY `idx_voucher` (`voucher_id`),
  KEY `idx_biz` (`biz_id`,`biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='总账分录明细（凭证 POSTED 逐行写入）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_gl_entry`
--

LOCK TABLES `op_gl_entry` WRITE;
/*!40000 ALTER TABLE `op_gl_entry` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_gl_entry` ENABLE KEYS */;
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
