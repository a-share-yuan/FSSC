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
-- Table structure for table `rpt_report_def`
--

DROP TABLE IF EXISTS `rpt_report_def`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rpt_report_def` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `report_code` varchar(64) NOT NULL COMMENT '报表编码（唯一）',
  `report_name` varchar(128) NOT NULL COMMENT '报表名称',
  `category` varchar(32) NOT NULL COMMENT '分类: EXPENSE/BUDGET/RECEIVABLE/TREASURY/ASSET/TAX/COMPLIANCE/OPS',
  `description` varchar(512) DEFAULT NULL COMMENT '报表说明',
  `api_path` varchar(256) DEFAULT NULL COMMENT '后端API路径',
  `refresh_type` varchar(16) DEFAULT 'REALTIME' COMMENT '刷新类型: REALTIME/HOURLY/DAILY/MONTHLY',
  `chart_type` varchar(16) DEFAULT NULL COMMENT '推荐图表: TABLE/BAR/LINE/PIE/COMBO',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '同分类内排序',
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_report_code` (`tenant_id`,`report_code`),
  KEY `idx_report_category` (`tenant_id`,`category`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表注册目录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rpt_report_def`
--

LOCK TABLES `rpt_report_def` WRITE;
/*!40000 ALTER TABLE `rpt_report_def` DISABLE KEYS */;
INSERT INTO `rpt_report_def` VALUES (1,1,'EXPENSE_SUMMARY','费用发生汇总表','EXPENSE','按期间×费用类型汇总','/api/tenant/report/expense-summary','MONTHLY','BAR','ACTIVE',1,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(2,1,'MONTHLY_TREND','费用月度变动表','EXPENSE','费用月度趋势分析','/api/tenant/report/monthly-trend','MONTHLY','LINE','ACTIVE',2,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(3,1,'EXPENSE_COMPOSITION','费用构成情况表','EXPENSE','费用类型×组织×月度构成','/api/tenant/report/expense-composition','MONTHLY','PIE','ACTIVE',3,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(4,1,'EXPENSE_DETAIL','费用明细表','EXPENSE','按单号/经办人/费用项查询','/api/tenant/report/expense-detail','REALTIME','TABLE','ACTIVE',4,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(5,1,'LOAN_BALANCE','借款余额汇总','RECEIVABLE','员工借款金额及未冲销','/api/tenant/report/loan-balance','REALTIME','TABLE','ACTIVE',5,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(6,1,'CONTRACT_SUMMARY','合同汇总','COMPLIANCE','合同执行情况','/api/tenant/report/contract-summary','DAILY','TABLE','ACTIVE',6,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(7,1,'OPS_STATISTICS','运营数据统计','OPS','派工/凭证/付款/质检/影像','/api/tenant/report/ops-statistics','HOURLY','COMBO','ACTIVE',7,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(8,1,'AP_AGING','应付账龄分析','TREASURY','应付账款账龄','/api/tenant/report/ap-aging','DAILY','BAR','ACTIVE',8,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(9,1,'AR_AGING','应收账龄分析','RECEIVABLE','应收账款账龄','/api/tenant/report/ar-aging','DAILY','BAR','ACTIVE',9,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(10,1,'CASH_FLOW','资金收支汇总','TREASURY','资金流入流出汇总','/api/tenant/report/cash-flow','DAILY','COMBO','ACTIVE',10,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(11,1,'BANK_BALANCE','资金余额表','TREASURY','银行账户余额','/api/tenant/report/bank-balance','REALTIME','TABLE','ACTIVE',11,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(12,1,'SUPPLIER_PAYMENT','供应商付款汇总','TREASURY','供应商付款统计','/api/tenant/report/supplier-payment','MONTHLY','TABLE','ACTIVE',12,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(13,1,'INPUT_INVOICE','进项发票统计','TAX','进项发票汇总','/api/tenant/report/input-invoice','MONTHLY','BAR','ACTIVE',13,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(14,1,'OUTPUT_INVOICE','销项发票统计','TAX','销项发票汇总','/api/tenant/report/output-invoice','MONTHLY','BAR','ACTIVE',14,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(15,1,'TAX_BURDEN','税负分析','TAX','税负率分析','/api/tenant/report/tax-burden','MONTHLY','LINE','ACTIVE',15,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(16,1,'ASSET_LEDGER','资产台账报表','ASSET','固定资产台账','/api/tenant/report/asset-ledger','DAILY','TABLE','ACTIVE',16,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(17,1,'ASSET_DEPR_DETAIL','折旧明细报表','ASSET','折旧明细','/api/tenant/report/asset-depr-detail','MONTHLY','TABLE','ACTIVE',17,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(18,1,'BUDGET_EXECUTION','预算执行明细表','BUDGET','预算执行分析','/api/tenant/report/budget-execution-detail','MONTHLY','BAR','ACTIVE',18,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(19,1,'EXPENSE_ANALYSIS','费用多维分析','EXPENSE','费用多维度分析','/api/tenant/report/expense-analysis','MONTHLY','COMBO','ACTIVE',19,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(20,1,'TREASURY_FLOW','资金流水明细表','TREASURY','银行流水明细','/api/tenant/report/treasury-flow','REALTIME','TABLE','ACTIVE',20,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(21,1,'VOUCHER_STATS','凭证生成统计','OPS','凭证生成情况','/api/tenant/report/voucher-stats','HOURLY','BAR','ACTIVE',21,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(22,1,'OPS_EFFICIENCY','运营效率分析','OPS','审批效率/作业效率','/api/tenant/report/ops-efficiency','DAILY','COMBO','ACTIVE',22,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(23,1,'QUALITY_PASS_RATE','质检合格率','OPS','质检合格率统计','/api/tenant/report/quality-pass-rate','DAILY','PIE','ACTIVE',23,0,'2026-09-06 17:51:35','2026-09-06 17:51:35'),(24,1,'INACTIVE_REPORT','已停用报表','EXPENSE','测试用停用报表','/api/tenant/report/none','REALTIME','TABLE','INACTIVE',99,0,'2026-09-06 17:51:35','2026-09-06 17:51:35');
/*!40000 ALTER TABLE `rpt_report_def` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:14:23
