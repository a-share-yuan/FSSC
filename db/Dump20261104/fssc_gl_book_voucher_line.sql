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
-- Table structure for table `gl_book_voucher_line`
--

DROP TABLE IF EXISTS `gl_book_voucher_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gl_book_voucher_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `book_voucher_id` bigint NOT NULL COMMENT '凭证 gl_book_voucher.id',
  `line_no` int NOT NULL DEFAULT '1' COMMENT '分录行号',
  `summary` varchar(500) DEFAULT NULL COMMENT '摘要',
  `subject_id` bigint NOT NULL COMMENT '科目 md_subject.id（账簿口径，已过准则映射）',
  `subject_code` varchar(64) DEFAULT NULL COMMENT '科目编码快照',
  `subject_name` varchar(200) DEFAULT NULL COMMENT '科目名称快照',
  `direction` varchar(10) NOT NULL DEFAULT 'DEBIT' COMMENT 'DEBIT借/CREDIT贷',
  `currency_id` bigint DEFAULT NULL COMMENT '币种(本位币时为空)',
  `orig_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '原币金额',
  `exchange_rate` decimal(20,10) NOT NULL DEFAULT '1.0000000000' COMMENT '汇率(原币→本位币)',
  `base_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '本位币金额',
  `dept_id` bigint DEFAULT NULL COMMENT '辅助核算-部门',
  `project_id` bigint DEFAULT NULL COMMENT '辅助核算-项目',
  `customer_id` bigint DEFAULT NULL COMMENT '辅助核算-客户',
  `supplier_id` bigint DEFAULT NULL COMMENT '辅助核算-供应商',
  `employee_id` bigint DEFAULT NULL COMMENT '辅助核算-员工',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_gbvl_voucher` (`book_voucher_id`,`line_no`),
  KEY `idx_gbvl_subject` (`tenant_id`,`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账簿记账凭证分录（本位币金额 + 辅助核算）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gl_book_voucher_line`
--

LOCK TABLES `gl_book_voucher_line` WRITE;
/*!40000 ALTER TABLE `gl_book_voucher_line` DISABLE KEYS */;
/*!40000 ALTER TABLE `gl_book_voucher_line` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:30:00
