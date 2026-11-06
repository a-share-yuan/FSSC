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
-- Table structure for table `tax_provision_rule`
--

DROP TABLE IF EXISTS `tax_provision_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tax_provision_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `tax_type` varchar(32) NOT NULL COMMENT '税种码：VAT/SURCHARGE/INCOME/IIT/STAMP/PROPERTY/LAND/VEHICLE',
  `rule_name` varchar(100) NOT NULL COMMENT '规则名（展示用）',
  `base_source` varchar(20) NOT NULL DEFAULT 'MANUAL' COMMENT '基数来源：MANUAL手工录入/DECLARATION按已申报数汇总/INVOICE按发票池已认证进项税汇总',
  `base_amount` decimal(18,2) DEFAULT NULL COMMENT '手工基数来源时的缺省计税基数',
  `tax_rate` decimal(10,4) NOT NULL COMMENT '税率（百分数，13.00 表示 13%）',
  `debit_subject_code` varchar(64) DEFAULT NULL COMMENT '计提凭证借方科目码（6403/6801）',
  `credit_subject_code` varchar(64) DEFAULT NULL COMMENT '计提凭证贷方科目码（应交税费明细）',
  `declare_credit_subject_code` varchar(64) DEFAULT '2221' COMMENT '申报凭证贷方科目码（未交税金）',
  `pay_debit_subject_code` varchar(64) DEFAULT '2221' COMMENT '缴纳（付款）凭证借方科目码',
  `period_type` varchar(16) DEFAULT 'MONTHLY' COMMENT '纳税周期：MONTHLY/QUARTERLY/HALF_YEARLY/ANNUAL/PER_TIME',
  `declare_deadline_day` int DEFAULT '15' COMMENT '申报截止日（月内第几日）',
  `pay_deadline_day` int DEFAULT '15' COMMENT '缴款截止日（月内第几日）',
  `sort` int DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/DISABLED',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_tax_type` (`tenant_id`,`tax_type`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='税金计提规则（税种/基数来源/税率/借贷科目）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tax_provision_rule`
--

LOCK TABLES `tax_provision_rule` WRITE;
/*!40000 ALTER TABLE `tax_provision_rule` DISABLE KEYS */;
INSERT INTO `tax_provision_rule` VALUES (1,1,'VAT','增值税-月度','MANUAL',100000.00,13.0000,'6403','22210101','22210104','22210104','MONTHLY',15,15,1,'NORMAL','种子：与改造前写死常量同值；销项税额明细按 22210101',1,'2026-11-03 01:11:00',NULL,'2026-11-03 01:19:48',0),(2,1,'SURCHARGE','附加税-月度','MANUAL',12000.00,12.0000,'6403','22210105','22210104','22210104','MONTHLY',15,15,2,'NORMAL','种子：附加税无明细子目，借贷同码时申报出证会自动跳过（不产空转凭证）',1,'2026-11-03 01:11:00',NULL,'2026-11-03 01:19:48',0),(3,1,'INCOME','企业所得税-季度','MANUAL',50000.00,25.0000,'6801','22210201','22210104','22210104','QUARTERLY',15,15,3,'NORMAL','种子：企业所得税季报，贷方 22210201',1,'2026-11-03 01:11:00',NULL,'2026-11-03 01:19:48',0);
/*!40000 ALTER TABLE `tax_provision_rule` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:44
