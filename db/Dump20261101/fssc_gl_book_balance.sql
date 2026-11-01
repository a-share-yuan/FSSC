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
-- Table structure for table `gl_book_balance`
--

DROP TABLE IF EXISTS `gl_book_balance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gl_book_balance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `book_id` bigint NOT NULL COMMENT '账簿 gl_accounting_book.id',
  `accounting_year` int NOT NULL COMMENT '会计年度',
  `period` varchar(8) NOT NULL DEFAULT '0000' COMMENT '期间 yyyyMM；0000=年度期初',
  `subject_id` bigint NOT NULL COMMENT '科目 md_subject.id',
  `currency_id` bigint NOT NULL COMMENT '币种 gl_currency.id',
  `direction` varchar(8) NOT NULL DEFAULT 'DEBIT' COMMENT '余额方向:DEBIT借/CREDIT贷',
  `begin_balance` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '期初余额',
  `begin_qty` decimal(20,4) NOT NULL DEFAULT '0.0000' COMMENT '期初数量(数量核算科目)',
  `period_begin` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '本期期初',
  `period_debit` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '本期借方发生',
  `period_credit` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '本期贷方发生',
  `period_end` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '本期期末',
  `status` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL正常',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gbb_book_period_subject` (`book_id`,`period`,`subject_id`,`currency_id`),
  KEY `idx_gbb_tenant_book` (`tenant_id`,`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账簿科目余额（期初/发生/期末，多账簿各自独立）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gl_book_balance`
--

LOCK TABLES `gl_book_balance` WRITE;
/*!40000 ALTER TABLE `gl_book_balance` DISABLE KEYS */;
/*!40000 ALTER TABLE `gl_book_balance` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:05:50
