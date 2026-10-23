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
-- Table structure for table `bg_item`
--

DROP TABLE IF EXISTS `bg_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bg_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `scheme_id` bigint NOT NULL COMMENT '预算方案ID',
  `company_id` bigint NOT NULL COMMENT '公司ID',
  `budget_year` int NOT NULL COMMENT '预算年度',
  `budget_period` int NOT NULL DEFAULT '0' COMMENT '预算期间:月度1-12,季度1-4,年度0',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID(维度)',
  `item_id` bigint DEFAULT NULL COMMENT '费用项目ID(维度)',
  `project_id` bigint DEFAULT NULL COMMENT '项目ID(维度)',
  `dim_key` varchar(200) NOT NULL COMMENT '维度组合键,如 D10_I20_P0,用于唯一约束与快速定位',
  `total_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '预算总额',
  `adjust_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '累计调整额(可正可负)',
  `occupy_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '已占用(申请中,未核销)',
  `used_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '已使用(已核销)',
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `nc_account` varchar(64) DEFAULT NULL COMMENT 'NC体系科目码',
  `ifs_account` varchar(64) DEFAULT NULL COMMENT 'IFS体系科目码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_budget_dim` (`tenant_id`,`scheme_id`,`budget_period`,`dim_key`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_scheme` (`tenant_id`,`scheme_id`),
  KEY `idx_tenant_dept_item` (`tenant_id`,`dept_id`,`item_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预算明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bg_item`
--

LOCK TABLES `bg_item` WRITE;
/*!40000 ALTER TABLE `bg_item` DISABLE KEYS */;
INSERT INTO `bg_item` VALUES (1,1,100,1,2026,11,NULL,333,NULL,'2026-COMPANY1-DEPTnull-ITEM333',50000.00,0.00,0.00,0.00,'CNY',NULL,3925,'2026-09-29 10:50:05',3925,'2026-10-20 00:37:00',0,NULL,NULL),(2,1,100,1,2026,11,NULL,335,NULL,'2026-COMPANY1-DEPTnull-ITEM335',100000.00,0.00,0.00,0.00,'CNY',NULL,3925,'2026-09-29 10:50:05',3925,'2026-10-13 13:35:59',0,NULL,NULL),(3,1,100,1,2026,11,NULL,336,NULL,'2026-COMPANY1-DEPTnull-ITEM336',4000.00,0.00,0.00,0.00,'CNY',NULL,3925,'2026-09-29 10:50:05',3925,'2026-10-13 13:35:59',0,NULL,NULL),(4,1,100,1,2026,11,NULL,342,NULL,'2026-COMPANY1-DEPTnull-ITEM342',30000.00,0.00,0.00,0.00,'CNY',NULL,3925,'2026-09-29 10:50:05',3925,'2026-09-29 10:50:05',0,NULL,NULL),(5,1,101,1,2026,0,NULL,333,NULL,'2026-COMPANY1-DEPTnull-ITEM333',50000.00,0.00,0.00,0.00,'CNY','',3925,'2026-09-29 11:14:07',3925,'2026-10-23 09:52:11',0,'',''),(6,1,101,1,2026,0,NULL,335,NULL,'2026-COMPANY1-DEPTnull-ITEM335',100000.00,0.00,0.00,0.00,'CNY','',3925,'2026-09-29 11:16:52',3925,'2026-10-21 22:03:02',0,'',''),(7,1,101,1,2026,0,NULL,336,NULL,'2026-COMPANY1-DEPTnull-ITEM336',4000.00,0.00,0.00,0.00,'CNY','',3925,'2026-09-29 11:17:33',3925,'2026-10-20 00:37:00',0,'',''),(8,1,101,1,2026,0,NULL,342,NULL,'2026-COMPANY1-DEPTnull-ITEM342',200000.00,0.00,0.00,0.00,'CNY','',3925,'2026-09-29 11:18:13',3925,'2026-10-21 09:37:25',0,'',''),(9,1,102,1,2026,9,1014,331,NULL,'2099-COMPANY1-DEPT1014-ITEM331',50000.00,0.00,0.00,0.00,'CNY','ENV-V3-29 修正：录入时年度控件回显失真(2026→存 2099)、总额控件 el-input-number 吞值(存 0)，UI 列表按 2026 年过滤后该行不可见无法自纠；沿 D-V3-132 先例最小 SQL 修正为本轮创建行自身（30 秒内），非他人数据',3837,'2026-09-30 13:32:18',3837,'2026-10-20 00:37:00',0,'','');
/*!40000 ALTER TABLE `bg_item` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:15:00
