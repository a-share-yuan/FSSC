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
-- Table structure for table `md_compliance_rule`
--

DROP TABLE IF EXISTS `md_compliance_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_compliance_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID（NULL 表示平台全局默认）',
  `rule_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则编码（唯一）',
  `rule_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `rule_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则类别：DUPLICATE_INVOICE/EXPENSE_MISMATCH/OVER_STANDARD/BLACKLIST',
  `risk_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM' COMMENT '风险等级：LOW/MEDIUM/HIGH/CRITICAL',
  `action` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REVIEW' COMMENT '处置动作：BLOCK/WARN/REVIEW/PASS',
  `match_expression` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '命中判定表达式（与预检脚本同语法）',
  `threshold_config` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '阈值配置（JSON）',
  `message_template` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '命中提示/阻断信息模板',
  `related_expense_types` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '适用费用类型（逗号分隔，空=全局）',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号，越小越先执行',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`),
  KEY `idx_rule_type` (`rule_type`),
  KEY `idx_status` (`status`),
  KEY `idx_sort` (`sort_no`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合规规则配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_compliance_rule`
--

LOCK TABLES `md_compliance_rule` WRITE;
/*!40000 ALTER TABLE `md_compliance_rule` DISABLE KEYS */;
INSERT INTO `md_compliance_rule` VALUES (1,NULL,'COMP-DUP-INV-001','重复发票拦截','DUPLICATE_INVOICE','HIGH','BLOCK','dupCount >= 1','{\"windowDays\":90}','发票{invoiceCode}-{invoiceNo}在{windowDays}天内已被报销，属于重复报销，被拦截',NULL,'ENABLED',10,'同一发票号窗口期内仅可报销一次','2026-09-06 12:34:59','2026-09-06 12:34:59'),(2,NULL,'COMP-EXP-MISMATCH-001','费用项目与发票品名不一致','EXPENSE_MISMATCH','MEDIUM','REVIEW','expenseMatchScore < 0.5','{\"scoreThreshold\":0.5}','发票品名[{invoiceItem}]与费用类型[{expenseType}]不匹配，需人工复核',NULL,'ENABLED',20,'发票品名口径与报销费用类型一致性校验','2026-09-06 12:34:59','2026-09-06 12:34:59'),(3,NULL,'COMP-OVER-STD-001','住宿费超标','OVER_STANDARD','MEDIUM','WARN','amount > standardLimit','{\"standardLimit\":500}','住宿费{amount}元超出标准限额{standardLimit}元，超支{exceed}元','HOTEL,住宿费','ENABLED',30,'单笔住宿费标准限额','2026-09-06 12:34:59','2026-09-06 12:34:59'),(4,NULL,'COMP-OVER-STD-002','差旅交通超标','OVER_STANDARD','MEDIUM','WARN','amount > standardLimit','{\"standardLimit\":2000}','交通费{amount}元超出标准限额{standardLimit}元','TRAFFIC,交通费,TAXI,机票','ENABLED',31,'差旅交通费标准限额','2026-09-06 12:34:59','2026-09-06 12:34:59'),(5,NULL,'COMP-OVER-STD-003','招待费超标','OVER_STANDARD','HIGH','REVIEW','amount > standardLimit OR personAmount > personLimit','{\"standardLimit\":100000,\"personLimit\":1000}','招待费{amount}元超标（限额{standardLimit}元），或人均{personAmount}元超人均{personLimit}元，需高级复核','ENTERTAIN,招待费','ENABLED',32,'招待费金额或人均超标','2026-09-06 12:34:59','2026-09-06 12:34:59'),(6,NULL,'COMP-BLACKLIST-001','供应商黑名单校验','BLACKLIST','CRITICAL','BLOCK','blacklisted == true','{\"checkDimension\":\"supplier\"}','供应商[{supplierName}]在问题名单（欠税/走逃/涉刑/红冲）,禁止发生业务',NULL,'ENABLED',40,'供应商在涉税/涉刑问题企业名单','2026-09-06 12:34:59','2026-09-06 12:34:59'),(7,NULL,'COMP-BLACKLIST-002','开票方黑名单校验','BLACKLIST','HIGH','BLOCK','blacklisted == true','{\"checkDimension\":\"issuer\"}','开票方[{issuerName}]在问题企业名单（失控/异常凭证）,发票不得入账',NULL,'ENABLED',41,'开票方为走逃/失控/异常凭证企业','2026-09-06 12:34:59','2026-09-06 12:34:59');
/*!40000 ALTER TABLE `md_compliance_rule` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:13:41
