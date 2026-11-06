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
-- Table structure for table `op_voucher_line`
--

DROP TABLE IF EXISTS `op_voucher_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_voucher_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `voucher_id` bigint NOT NULL COMMENT '凭证头ID',
  `line_no` int NOT NULL DEFAULT '1' COMMENT '分录行号',
  `summary` varchar(500) NOT NULL COMMENT '摘要',
  `subject_id` bigint NOT NULL COMMENT '会计科目ID',
  `subject_code` varchar(64) DEFAULT NULL COMMENT '科目编码(快照)',
  `subject_name` varchar(200) DEFAULT NULL COMMENT '科目名称(快照)',
  `direction` varchar(10) NOT NULL DEFAULT 'DEBIT' COMMENT '借贷方向:DEBIT借/CREDIT贷',
  `amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '金额',
  `currency_code` varchar(10) DEFAULT NULL COMMENT '外币币种(有外币核算时)',
  `exchange_rate` decimal(20,10) DEFAULT NULL COMMENT '汇率',
  `orig_amount` decimal(20,2) DEFAULT NULL COMMENT '原币金额',
  `dept_id` bigint DEFAULT NULL COMMENT '辅助核算-部门',
  `project_id` bigint DEFAULT NULL COMMENT '辅助核算-项目',
  `supplier_id` bigint DEFAULT NULL COMMENT '辅助核算-供应商',
  `customer_id` bigint DEFAULT NULL COMMENT '辅助核算-客户ID',
  `employee_id` bigint DEFAULT NULL COMMENT '辅助核算-员工',
  `aux_json` text COMMENT '其他辅助核算JSON',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_voucher` (`tenant_id`,`voucher_id`),
  KEY `idx_tenant_subject` (`tenant_id`,`subject_id`)
) ENGINE=InnoDB AUTO_INCREMENT=337 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会计凭证分录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_voucher_line`
--

LOCK TABLES `op_voucher_line` WRITE;
/*!40000 ALTER TABLE `op_voucher_line` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_voucher_line` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:38
