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
-- Table structure for table `gl_tax_provision`
--

DROP TABLE IF EXISTS `gl_tax_provision`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gl_tax_provision` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `provision_no` varchar(64) DEFAULT NULL COMMENT '计提单号',
  `period` varchar(16) DEFAULT NULL COMMENT '会计期间 yyyyMM',
  `tax_type` varchar(32) DEFAULT NULL COMMENT 'VAT增值税/SURCHARGE附加/INCOME所得税',
  `subject_id` bigint DEFAULT NULL COMMENT '关联会计科目 md_subject.id',
  `base_amount` decimal(18,2) DEFAULT NULL COMMENT '计税基数',
  `rate` decimal(10,2) DEFAULT NULL COMMENT '税率(百分比)',
  `tax_amount` decimal(18,2) DEFAULT NULL COMMENT '税额',
  `status` varchar(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/POSTED',
  `voucher_no` varchar(64) DEFAULT NULL COMMENT '记账凭证号',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否/1是',
  `company_id` bigint DEFAULT NULL COMMENT '记账公司(组织合法性 V1.3.18)',
  PRIMARY KEY (`id`),
  KEY `idx_gl_tax_period` (`tenant_id`,`period`),
  KEY `idx_gl_tax_type` (`tax_type`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='税金计提';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gl_tax_provision`
--

LOCK TABLES `gl_tax_provision` WRITE;
/*!40000 ALTER TABLE `gl_tax_provision` DISABLE KEYS */;
/*!40000 ALTER TABLE `gl_tax_provision` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:29
