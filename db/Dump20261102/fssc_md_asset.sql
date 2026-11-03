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
-- Table structure for table `md_asset`
--

DROP TABLE IF EXISTS `md_asset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_asset` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `asset_no` varchar(64) DEFAULT NULL COMMENT '资产编号',
  `asset_name` varchar(200) DEFAULT NULL COMMENT '资产名称',
  `category_id` bigint DEFAULT NULL COMMENT '资产类别(md_asset_category)',
  `dept_id` bigint DEFAULT NULL COMMENT '使用部门',
  `company_id` bigint DEFAULT NULL COMMENT '所属公司',
  `purchase_date` date DEFAULT NULL COMMENT '购置日期',
  `original_amount` decimal(20,2) DEFAULT '0.00' COMMENT '原值',
  `net_amount` decimal(20,2) DEFAULT '0.00' COMMENT '账面净值',
  `residual_value` decimal(20,2) DEFAULT '0.00' COMMENT '残值',
  `depreciation_method` varchar(20) DEFAULT 'LINEAR' COMMENT '折旧方法:LINEAR直线/ACCELERATED加速',
  `depreciation_months` int DEFAULT '60' COMMENT '折旧月数',
  `tax_life_months` int DEFAULT NULL COMMENT '税法折旧年限(月)：与会计折旧年限 depreciation_months 分离，支撑税会差异(A105080)',
  `monthly_depr` decimal(20,2) DEFAULT '0.00' COMMENT '月折旧额',
  `accum_depr` decimal(20,2) DEFAULT '0.00' COMMENT '累计折旧',
  `status` varchar(20) DEFAULT 'INIT' COMMENT 'INIT在库/USING在用/SCRAP报废',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商',
  `invoice_no` varchar(64) DEFAULT NULL COMMENT '发票号',
  `last_depr_date` date DEFAULT NULL COMMENT '最后折旧日期(月度批处理幂等用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL COMMENT '建档人用户ID(sys_user.id)，D-DEC-433：deny_self 自避载体列',
  PRIMARY KEY (`id`),
  KEY `idx_md_asset_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=120112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产卡片(FA-P0-7)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_asset`
--

LOCK TABLES `md_asset` WRITE;
/*!40000 ALTER TABLE `md_asset` DISABLE KEYS */;
/*!40000 ALTER TABLE `md_asset` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:12
