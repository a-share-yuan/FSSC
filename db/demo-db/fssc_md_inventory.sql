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
-- Table structure for table `md_inventory`
--

DROP TABLE IF EXISTS `md_inventory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `item_code` varchar(64) DEFAULT NULL COMMENT '物料编码',
  `item_name` varchar(200) DEFAULT NULL COMMENT '物料名称',
  `spec` varchar(128) DEFAULT NULL COMMENT '规格',
  `unit` varchar(32) DEFAULT NULL COMMENT '计量单位',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库',
  `opening_num` decimal(20,4) DEFAULT '0.0000' COMMENT '期初数量',
  `opening_amount` decimal(20,2) DEFAULT '0.00' COMMENT '期初金额',
  `in_num` decimal(20,4) DEFAULT '0.0000' COMMENT '入库数量',
  `in_amount` decimal(20,2) DEFAULT '0.00' COMMENT '入库金额',
  `out_num` decimal(20,4) DEFAULT '0.0000' COMMENT '出库数量',
  `out_amount` decimal(20,2) DEFAULT '0.00' COMMENT '出库金额(发出成本)',
  `remaining_num` decimal(20,4) DEFAULT '0.0000' COMMENT '结存数量',
  `remaining_amount` decimal(20,2) DEFAULT '0.00' COMMENT '结存金额',
  `price_method` varchar(20) DEFAULT 'MA' COMMENT '计价方式:MA移动平均',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `company_id` bigint DEFAULT NULL COMMENT '所属公司(多分子公司审批路由 V1.3.17)',
  `create_by` bigint DEFAULT NULL COMMENT '领用/入库经手人用户ID(sys_user.id)，D-DEC-433：deny_self 自避载体列',
  PRIMARY KEY (`id`),
  KEY `idx_inventory_tenant` (`tenant_id`),
  KEY `idx_inventory_item` (`item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='存货收发存(FA-P0-7)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_inventory`
--

LOCK TABLES `md_inventory` WRITE;
/*!40000 ALTER TABLE `md_inventory` DISABLE KEYS */;
/*!40000 ALTER TABLE `md_inventory` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:14:29
