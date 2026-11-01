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
-- Table structure for table `exp_bill_line_cold`
--

DROP TABLE IF EXISTS `exp_bill_line_cold`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exp_bill_line_cold` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `bill_id` bigint NOT NULL COMMENT '单据主表ID',
  `line_no` int NOT NULL DEFAULT '1' COMMENT '行号',
  `item_id` bigint NOT NULL COMMENT '费用项目ID',
  `item_name` varchar(200) DEFAULT NULL COMMENT '费用项目名称(冗余快照)',
  `expense_date` date DEFAULT NULL COMMENT '费用发生日期',
  `dept_id` bigint DEFAULT NULL COMMENT '行级承担部门(默认取表头)',
  `cost_center_id` bigint DEFAULT NULL COMMENT '行级成本中心',
  `project_id` bigint DEFAULT NULL COMMENT '行级项目',
  `subject_id` bigint DEFAULT NULL COMMENT '会计科目ID(生成凭证用)',
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '原币种',
  `exchange_rate` decimal(20,10) NOT NULL DEFAULT '1.0000000000' COMMENT '汇率',
  `orig_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '原币金额(含税)',
  `price_type` varchar(20) DEFAULT NULL COMMENT '行录入口径：TAX_IN含税录入/TAX_EX不含税录入，缺省继承表头',
  `amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '本位币金额(含税)',
  `tax_rate` decimal(6,4) DEFAULT NULL COMMENT '税率',
  `tax_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '税额',
  `net_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '不含税金额',
  `deduct_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '可抵扣进项税额',
  `approved_amount` decimal(20,2) DEFAULT NULL COMMENT '行级核定金额',
  `quantity` decimal(20,4) DEFAULT NULL COMMENT '数量(人数/天数/次数)',
  `unit_price` decimal(20,4) DEFAULT NULL COMMENT '单价',
  `person_count` int DEFAULT NULL COMMENT '涉及人数(招待费必填)',
  `attendees` varchar(1000) DEFAULT NULL COMMENT '参与人员(招待费/会议费)',
  `std_amount` decimal(20,2) DEFAULT NULL COMMENT '匹配到的标准限额',
  `over_std` tinyint NOT NULL DEFAULT '0' COMMENT '是否超标:0否 1是',
  `over_reason` varchar(500) DEFAULT NULL COMMENT '超标说明',
  `entertain_type` varchar(32) DEFAULT NULL COMMENT '接待类型(取字典)，D-DEC-373',
  `budget_item_id` bigint DEFAULT NULL COMMENT '命中的预算明细ID',
  `remark` varchar(500) DEFAULT NULL COMMENT '费用说明',
  `ext_json` text COMMENT '扩展字段JSON',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `voucher_form` varchar(20) NOT NULL DEFAULT 'FAPIAO' COMMENT '凭证形态：FAPIAO境内发票/RECEIPT境外收据/ITINERARY行程单',
  `cn_desc` varchar(500) DEFAULT NULL COMMENT '境外凭证中文说明（19号公告：载明行程/地点/费用/人名，税前扣除要件）',
  `archived_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exp_bill_line_cold`
--

LOCK TABLES `exp_bill_line_cold` WRITE;
/*!40000 ALTER TABLE `exp_bill_line_cold` DISABLE KEYS */;
/*!40000 ALTER TABLE `exp_bill_line_cold` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:05:51
