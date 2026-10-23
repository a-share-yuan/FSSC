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
-- Table structure for table `so_order_line`
--

DROP TABLE IF EXISTS `so_order_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `so_order_line` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `so_id` bigint NOT NULL COMMENT '销售订单头主键（so_order.id）',
  `line_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行号（报文 lineNo）',
  `material_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物料代码（报文 materialCode）',
  `material_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物料名称（报文 materialName）',
  `quantity` decimal(18,4) DEFAULT NULL COMMENT '数量（报文 quantity）',
  `unit` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '计量单位（报文 unit）',
  `unit_price` decimal(18,6) DEFAULT NULL COMMENT '含税单价（报文 unitPrice）',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '行金额（不含税，报文 amount）',
  `tax_rate` decimal(9,4) DEFAULT NULL COMMENT '税率，如 0.13（报文 taxRate）',
  `tax_amount` decimal(18,2) DEFAULT NULL COMMENT '税额（报文 lineTaxAmount）',
  `shipped_qty` decimal(18,4) DEFAULT NULL COMMENT '已发数量（发货腿维护；报文 shippedQty）',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'OPEN/PARTIAL/FULL/CLOSED（报文 lineStatus）',
  `create_time` datetime DEFAULT NULL,
  `del_flag` int NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 正常 / 1 删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_so_order_line` (`so_id`,`line_no`),
  KEY `idx_so_order_line_so` (`so_id`)
) ENGINE=InnoDB AUTO_INCREMENT=700000397 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售订单行（AR 三单匹配订单腿行级数据源，P0-C）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `so_order_line`
--

LOCK TABLES `so_order_line` WRITE;
/*!40000 ALTER TABLE `so_order_line` DISABLE KEYS */;
/*!40000 ALTER TABLE `so_order_line` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:15:55
