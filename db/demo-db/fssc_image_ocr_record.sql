CREATE DATABASE  IF NOT EXISTS `fssc_image` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `fssc_image`;
-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: fssc_image
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
-- Table structure for table `ocr_record`
--

DROP TABLE IF EXISTS `ocr_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ocr_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `batch_id` bigint DEFAULT NULL COMMENT '影像批次ID',
  `img_file_id` bigint DEFAULT NULL COMMENT '影像页面ID (img_file.id)',
  `file_id` bigint DEFAULT NULL COMMENT '系统文件ID (sys_file.id)',
  `invoice_code` varchar(64) DEFAULT NULL COMMENT '发票代码',
  `invoice_no` varchar(64) DEFAULT NULL COMMENT '发票号码',
  `invoice_type` varchar(32) DEFAULT NULL COMMENT '发票类型 VAT/VATN/TRAIN/OTHER',
  `invoice_date` varchar(16) DEFAULT NULL COMMENT '开票日期 yyyy-MM-dd',
  `net_amount` decimal(18,4) DEFAULT NULL COMMENT '不含税金额',
  `tax_amount` decimal(18,4) DEFAULT NULL COMMENT '税额',
  `amount` decimal(18,4) DEFAULT NULL COMMENT '价税合计',
  `seller_name` varchar(256) DEFAULT NULL COMMENT '销方名称',
  `seller_tax_no` varchar(64) DEFAULT NULL COMMENT '销方税号',
  `buyer_name` varchar(256) DEFAULT NULL COMMENT '购方名称',
  `buyer_tax_no` varchar(64) DEFAULT NULL COMMENT '购方税号',
  `raw_text` text COMMENT '识别全文/原始报文摘要',
  `channel_name` varchar(32) DEFAULT NULL COMMENT 'OCR 通道名 mock/http',
  `consistent` tinyint(1) DEFAULT '1' COMMENT '票面金额自洽 1=是 0=否',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_batch` (`tenant_id`,`batch_id`),
  KEY `idx_invoice_no` (`tenant_id`,`invoice_no`),
  KEY `idx_img_file` (`img_file_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OCR 识别结果落库记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ocr_record`
--

LOCK TABLES `ocr_record` WRITE;
/*!40000 ALTER TABLE `ocr_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `ocr_record` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:14:21
