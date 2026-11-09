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
-- Table structure for table `gl_book_voucher`
--

DROP TABLE IF EXISTS `gl_book_voucher`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gl_book_voucher` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `book_id` bigint NOT NULL COMMENT '账簿 gl_accounting_book.id',
  `voucher_no` varchar(64) NOT NULL COMMENT '凭证号(账簿内唯一)',
  `voucher_word` varchar(20) NOT NULL DEFAULT 'JOURNAL' COMMENT '凭证字:JOURNAL记/RECEIPT收/PAYMENT付/TRANSFER转',
  `accounting_year` int NOT NULL COMMENT '会计年度',
  `period` varchar(8) NOT NULL COMMENT '会计期间 yyyyMM',
  `voucher_date` date NOT NULL COMMENT '凭证日期',
  `source_type` varchar(32) NOT NULL COMMENT '来源类型(与 op_voucher.source_type 同口径)',
  `source_doc_type` varchar(32) DEFAULT NULL COMMENT '来源单据类型(溯源)',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据ID(溯源)',
  `summary` varchar(500) DEFAULT NULL COMMENT '凭证摘要',
  `debit_total` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '借方合计(本位币)',
  `credit_total` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '贷方合计(本位币)',
  `is_balanced` tinyint NOT NULL DEFAULT '0' COMMENT '是否借贷平衡',
  `status` varchar(16) NOT NULL DEFAULT 'POSTED' COMMENT 'DRAFT草稿/POSTED已记账/REVERSED已冲销',
  `post_user_id` bigint DEFAULT NULL COMMENT '记账人',
  `post_time` datetime DEFAULT NULL COMMENT '记账时间',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gbv_book_no` (`book_id`,`voucher_no`),
  KEY `idx_gbv_tenant_book_period` (`tenant_id`,`book_id`,`accounting_year`,`period`),
  KEY `idx_gbv_source` (`tenant_id`,`source_doc_type`,`source_doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账簿记账凭证（多账簿各记各账的载体）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gl_book_voucher`
--

LOCK TABLES `gl_book_voucher` WRITE;
/*!40000 ALTER TABLE `gl_book_voucher` DISABLE KEYS */;
/*!40000 ALTER TABLE `gl_book_voucher` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:23:33
