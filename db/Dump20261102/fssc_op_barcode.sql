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
-- Table structure for table `op_barcode`
--

DROP TABLE IF EXISTS `op_barcode`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_barcode` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `barcode` varchar(100) NOT NULL COMMENT '条码值（一维码/二维码内容）',
  `barcode_type` varchar(20) NOT NULL DEFAULT 'CODE128' COMMENT '条码类型: CODE128/QRCODE',
  `biz_type` varchar(20) NOT NULL COMMENT '业务类型: BILL单据/ARCHIVE档案/BOX档案盒',
  `ref_id` bigint DEFAULT NULL COMMENT '关联业务ID（单据ID/档案ID/档案盒ID）',
  `ref_no` varchar(50) DEFAULT NULL COMMENT '关联业务编号',
  `print_by` bigint DEFAULT NULL COMMENT '打印人',
  `print_name` varchar(50) DEFAULT NULL COMMENT '打印人姓名',
  `print_time` datetime DEFAULT NULL COMMENT '打印时间',
  `scan_by` bigint DEFAULT NULL COMMENT '扫描人',
  `scan_name` varchar(50) DEFAULT NULL COMMENT '扫描人姓名',
  `scan_time` datetime DEFAULT NULL COMMENT '扫描时间',
  `status` varchar(20) NOT NULL DEFAULT 'GENERATED' COMMENT '状态: GENERATED已生成/PRINTED已打印/SCANNED已扫描/SHELVED已上架',
  `location` varchar(100) DEFAULT NULL COMMENT '上架位置',
  `shelve_time` datetime DEFAULT NULL COMMENT '上架时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_barcode_tenant` (`tenant_id`,`barcode`),
  KEY `idx_barcode_ref` (`tenant_id`,`biz_type`,`ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='条码管理';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_barcode`
--

LOCK TABLES `op_barcode` WRITE;
/*!40000 ALTER TABLE `op_barcode` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_barcode` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:29:58
