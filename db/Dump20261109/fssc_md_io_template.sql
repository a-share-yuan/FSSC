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
-- Table structure for table `md_io_template`
--

DROP TABLE IF EXISTS `md_io_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_io_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `template_code` varchar(64) NOT NULL COMMENT '模板编码(租户内唯一,含版本)',
  `template_name` varchar(128) NOT NULL COMMENT '模板名称',
  `biz_type` varchar(32) NOT NULL COMMENT '业务类型:AR_STATEMENT对账单/AR_RECEIPT收款核销/AR_OFFSET对冲/AP_PLAN付款计划/BUDGET_ITEM预算项目/RC_BAD_DEBT坏账',
  `direction` varchar(8) NOT NULL DEFAULT 'IMPORT' COMMENT '方向:IMPORT导入/EXPORT导出/BOTH双向',
  `target_table` varchar(64) DEFAULT NULL COMMENT '落库目标表(EXPORT 时为目标表名,便于导出取数)',
  `sheet_name` varchar(64) NOT NULL DEFAULT 'Sheet1' COMMENT 'Excel 工作表名',
  `header_row` int NOT NULL DEFAULT '1' COMMENT '表头所在行(1-based,允许前导说明行)',
  `max_rows` int NOT NULL DEFAULT '10000' COMMENT '单次最大数据行数(防打爆内存)',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `is_system` tinyint NOT NULL DEFAULT '0' COMMENT '是否系统预置:1-是 0-否',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED启用/DISABLED停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_io_code` (`tenant_id`,`template_code`,`version`),
  KEY `idx_io_tenant_biz` (`tenant_id`,`biz_type`,`direction`,`status`),
  KEY `idx_io_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=57 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导入导出模板头（声明式模板，替代每模块一支手写ExcelService）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_io_template`
--

LOCK TABLES `md_io_template` WRITE;
/*!40000 ALTER TABLE `md_io_template` DISABLE KEYS */;
INSERT INTO `md_io_template` VALUES (50,1,'IO_AR_STATEMENT','客户对账单导入模板','AR_STATEMENT','IMPORT','ar_statement','Sheet1',1,10000,1,1,'ENABLED','源: ArStatementExcelService HEADERS(V1.4.84)',NULL,'2026-10-18 11:11:24',NULL,NULL,0),(51,1,'IO_AR_RECEIPT','收款核销导入模板','AR_RECEIPT','IMPORT','rc_receipt','Sheet1',1,10000,1,1,'ENABLED','源: ArReceiptExcelService HEADERS(V1.4.84)',NULL,'2026-10-18 11:11:24',NULL,NULL,0),(52,1,'IO_AR_OFFSET','应收对冲凭证导入模板','AR_OFFSET','IMPORT',NULL,'Sheet1',1,10000,1,1,'ENABLED','源: ArOffsetExcelService HEADERS(V1.4.84)',NULL,'2026-10-18 11:11:24',NULL,NULL,0),(53,1,'IO_AP_PLAN','付款计划导入模板','AP_PLAN','IMPORT','pay_plan','Sheet1',1,10000,1,1,'ENABLED','源: ApPlanExcelService HEADERS(V1.4.84)',NULL,'2026-10-18 11:11:24',NULL,NULL,0),(54,1,'IO_BUDGET_ITEM','预算项目导入模板','BUDGET_ITEM','IMPORT','bg_item','Sheet1',1,10000,1,1,'ENABLED','源: BudgetItemExcelService HEADERS(V1.4.84)',NULL,'2026-10-18 11:11:24',NULL,NULL,0),(55,1,'IO_RC_BAD_DEBT','坏账核销导入模板','RC_BAD_DEBT','IMPORT','rc_bad_debt','Sheet1',1,10000,1,1,'ENABLED','源: RcBadDebtExcelService HEADERS(V1.4.84)',NULL,'2026-10-18 11:11:24',NULL,NULL,0),(56,1,'IO_AR_STATEMENT_OUT','客户对账单导出模板','AR_STATEMENT','EXPORT','ar_statement','Sheet1',1,10000,1,1,'ENABLED','导入模板的反向导出(V1.4.84)',NULL,'2026-10-18 11:11:24',NULL,NULL,0);
/*!40000 ALTER TABLE `md_io_template` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:23:57
