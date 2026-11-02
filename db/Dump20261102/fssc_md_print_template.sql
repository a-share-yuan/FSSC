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
-- Table structure for table `md_print_template`
--

DROP TABLE IF EXISTS `md_print_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_print_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `template_code` varchar(64) NOT NULL COMMENT '模板编码(全局唯一,租户内)',
  `template_name` varchar(128) NOT NULL COMMENT '模板名称',
  `biz_type` varchar(32) NOT NULL COMMENT '业务单据类型:EXPENSE_CLAIM费用报销/CORP_PAY对公付款/CONTRACT合同/INVOICE发票/LOAN借款/VOUCHER记账凭证/AR_STATEMENT对账单/AP_REQUEST付款申请',
  `paper_size` varchar(16) NOT NULL DEFAULT 'A4' COMMENT '纸张:A4/A5/自定义',
  `orientation` varchar(8) NOT NULL DEFAULT 'PORTRAIT' COMMENT '方向:PORTRAIT纵向/LANDSCAPE横向',
  `width_mm` decimal(10,2) NOT NULL DEFAULT '210.00' COMMENT '宽(mm)',
  `height_mm` decimal(10,2) NOT NULL DEFAULT '297.00' COMMENT '高(mm)',
  `margin_top_mm` decimal(10,2) NOT NULL DEFAULT '15.00' COMMENT '上边距(mm)',
  `margin_bottom_mm` decimal(10,2) NOT NULL DEFAULT '15.00' COMMENT '下边距(mm)',
  `margin_left_mm` decimal(10,2) NOT NULL DEFAULT '15.00' COMMENT '左边距(mm)',
  `margin_right_mm` decimal(10,2) NOT NULL DEFAULT '15.00' COMMENT '右边距(mm)',
  `grid_rows` int NOT NULL DEFAULT '30' COMMENT '设计网格行数(套打设计器坐标基准)',
  `grid_cols` int NOT NULL DEFAULT '12' COMMENT '设计网格列数',
  `repeat_header` tinyint NOT NULL DEFAULT '1' COMMENT '明细表跨页是否重复表头',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号(改模板不覆盖历史版本)',
  `is_system` tinyint NOT NULL DEFAULT '0' COMMENT '是否系统预置:1-是(禁止删除)0-否(租户自建)',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED启用/DISABLED停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_tpl_code` (`tenant_id`,`template_code`,`version`),
  KEY `idx_pt_tenant_biz` (`tenant_id`,`biz_type`,`status`),
  KEY `idx_pt_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打印模板头（套打设计器载体，套打平台/单据设计器）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_print_template`
--

LOCK TABLES `md_print_template` WRITE;
/*!40000 ALTER TABLE `md_print_template` DISABLE KEYS */;
INSERT INTO `md_print_template` VALUES (1,1,'PT_EXPENSE_CLAIM','费用报销单','EXPENSE_CLAIM','A4','PORTRAIT',210.00,297.00,15.00,15.00,15.00,15.00,30,12,1,1,1,'ENABLED','由前端 usePrintTemplate.js TEMPLATES.EXPENSE_CLAIM 迁移(V1.4.82)',NULL,'2026-10-18 10:59:11',NULL,NULL,0),(2,1,'PT_CORP_PAY','付款申请单','CORP_PAY','A4','PORTRAIT',210.00,297.00,15.00,15.00,15.00,15.00,30,12,1,1,1,'ENABLED','由前端 usePrintTemplate.js TEMPLATES.CORP_PAY 迁移(V1.4.82)',NULL,'2026-10-18 10:59:11',NULL,NULL,0),(3,1,'PT_CONTRACT','合同登记表','CONTRACT','A4','PORTRAIT',210.00,297.00,15.00,15.00,15.00,15.00,30,12,1,1,1,'ENABLED','由前端 usePrintTemplate.js TEMPLATES.CONTRACT 迁移(V1.4.82)',NULL,'2026-10-18 10:59:11',NULL,NULL,0),(4,1,'PT_INVOICE','发票登记表','INVOICE','A4','PORTRAIT',210.00,297.00,15.00,15.00,15.00,15.00,30,12,1,1,1,'ENABLED','由前端 usePrintTemplate.js TEMPLATES.INVOICE 迁移(V1.4.82)',NULL,'2026-10-18 10:59:11',NULL,NULL,0),(5,1,'PT_LOAN','借款申请单','LOAN','A4','PORTRAIT',210.00,297.00,15.00,15.00,15.00,15.00,30,12,1,1,1,'ENABLED','由前端 usePrintTemplate.js TEMPLATES.LOAN 迁移(V1.4.82)',NULL,'2026-10-18 10:59:11',NULL,NULL,0);
/*!40000 ALTER TABLE `md_print_template` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:13:26
