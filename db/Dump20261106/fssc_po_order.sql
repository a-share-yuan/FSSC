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
-- Table structure for table `po_order`
--

DROP TABLE IF EXISTS `po_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `po_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `po_no` varchar(64) NOT NULL COMMENT '采购订单号',
  `contract_id` bigint DEFAULT NULL COMMENT '关联合同（框架协议，可选）',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商ID',
  `supplier_name` varchar(200) DEFAULT NULL COMMENT '供应商名称',
  `order_date` date DEFAULT NULL COMMENT '下单日期',
  `amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '订单金额',
  `received_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '累计收货金额',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `status` varchar(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN执行中/CLOSED已关闭',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` tinyint NOT NULL DEFAULT '0' COMMENT '删除标志',
  `company_id` bigint DEFAULT NULL COMMENT '所属公司(多分子公司审批路由 V1.3.17)',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `delivery_date` date DEFAULT NULL COMMENT '预计交货日期',
  `payment_terms` varchar(200) DEFAULT NULL COMMENT '付款条件',
  `buyer_name` varchar(100) DEFAULT NULL COMMENT '采购员',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `delivery_address` varchar(500) DEFAULT NULL COMMENT '交货地址',
  `capital_flag` tinyint NOT NULL DEFAULT '0' COMMENT '资本化标识：1=固定资产投资(收货即转固建卡) 0=普通采购',
  `cip_id` bigint DEFAULT NULL COMMENT '关联在建工程ID(md_cip.id，DF2-08)：收货/付款成本自动归集到该CIP',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_po_no` (`tenant_id`,`po_no`),
  KEY `idx_po_supplier` (`tenant_id`,`supplier_id`),
  KEY `idx_tenant_owner` (`tenant_id`,`create_by`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `po_order`
--

LOCK TABLES `po_order` WRITE;
/*!40000 ALTER TABLE `po_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `po_order` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:39
