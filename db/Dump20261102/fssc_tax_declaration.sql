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
-- Table structure for table `tax_declaration`
--

DROP TABLE IF EXISTS `tax_declaration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tax_declaration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `declaration_no` varchar(64) NOT NULL COMMENT '申报单号',
  `tax_type` varchar(32) NOT NULL COMMENT '税种：VAT增值税/CIT企业所得税/IIT个人所得税/STAMP印花税/PROPERTY房产税/LAND土地使用税/VEHICLE车船税',
  `tax_subtype` varchar(32) DEFAULT NULL COMMENT '税种子类：如VAT-GENERAL一般纳税人/VAT-SMALL小规模',
  `period` varchar(6) NOT NULL COMMENT '申报属期 yyyyMM',
  `declare_type` varchar(16) DEFAULT NULL COMMENT '申报类型：MONTHLY月度/QUARTERLY季度/ANNUAL年度',
  `declaration_kind` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT '申报性质：NORMAL正常申报/ADJUST更正（调整）申报',
  `related_id` bigint DEFAULT NULL COMMENT '更正申报关联的原申报单 id',
  `base_amount` decimal(18,2) DEFAULT NULL COMMENT '计税基数',
  `tax_rate` decimal(10,4) DEFAULT NULL COMMENT '税率',
  `tax_amount` decimal(18,2) DEFAULT NULL COMMENT '应纳税额',
  `paid_amount` decimal(18,2) DEFAULT NULL COMMENT '已缴税额',
  `balance_amount` decimal(18,2) DEFAULT NULL COMMENT '应补（退）税额',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/FILED已申报/PAID已缴纳/CANCELLED',
  `declare_date` date DEFAULT NULL COMMENT '申报日期',
  `pay_deadline` date DEFAULT NULL COMMENT '缴纳截止日',
  `source_voucher_id` bigint DEFAULT NULL COMMENT '关联凭证ID',
  `attachment_url` varchar(512) DEFAULT NULL COMMENT '申报表附件',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decl_no` (`tenant_id`,`declaration_no`),
  KEY `idx_tax_type` (`tax_type`,`period`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全税种申报表（增值税/所得税/印花税等）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tax_declaration`
--

LOCK TABLES `tax_declaration` WRITE;
/*!40000 ALTER TABLE `tax_declaration` DISABLE KEYS */;
/*!40000 ALTER TABLE `tax_declaration` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:30:03
