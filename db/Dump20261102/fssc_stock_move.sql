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
-- Table structure for table `stock_move`
--

DROP TABLE IF EXISTS `stock_move`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_move` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `move_no` varchar(32) NOT NULL COMMENT '移动单号',
  `move_type` varchar(20) NOT NULL COMMENT 'RECEIVE入库/ISSUE领用出库/SCRAP报废/DISPOSAL处置/INTERNAL_SALE内部销售/CHECK盘点/DEVAL跌价',
  `item_code` varchar(64) NOT NULL COMMENT '物料编码(md_inventory.item_code)',
  `item_name` varchar(128) DEFAULT NULL,
  `warehouse_id` bigint DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `qty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '数量(正数)',
  `unit_cost` decimal(18,6) NOT NULL DEFAULT '0.000000' COMMENT '单位成本',
  `amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '金额=qty*unit_cost',
  `source_doc_type` varchar(40) DEFAULT NULL COMMENT '来源单据类型(PO_RECEIPT/EXPENSE/INVENTORY)',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `flow_key` varchar(60) DEFAULT NULL COMMENT '关联流程KEY(INV_ISSUE/INV_DISPOSE等)',
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING(审批中)/APPROVED/REJECTED/CANCELLED',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_move_no` (`tenant_id`,`move_no`),
  KEY `idx_move_type_status` (`tenant_id`,`move_type`,`status`),
  KEY `idx_move_item` (`tenant_id`,`item_code`),
  KEY `idx_move_source` (`source_doc_type`,`source_doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='存货移动单(逐笔出入库交易)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stock_move`
--

LOCK TABLES `stock_move` WRITE;
/*!40000 ALTER TABLE `stock_move` DISABLE KEYS */;
/*!40000 ALTER TABLE `stock_move` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:14:10
