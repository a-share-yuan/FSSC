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
-- Table structure for table `image_exception`
--

DROP TABLE IF EXISTS `image_exception`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `image_exception` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `img_batch_id` bigint DEFAULT NULL,
  `batch_no` varchar(64) DEFAULT NULL,
  `biz_no` varchar(64) DEFAULT NULL,
  `exception_type` varchar(32) NOT NULL,
  `exception_desc` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN待处理/FIXED已处理',
  `handle_user_id` bigint DEFAULT NULL,
  `handle_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_exc` (`tenant_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='影像异常';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `image_exception`
--

LOCK TABLES `image_exception` WRITE;
/*!40000 ALTER TABLE `image_exception` DISABLE KEYS */;
INSERT INTO `image_exception` VALUES (4,1,4,'IMG2026110900001','','OCR_AMOUNT_INCONSISTENT','第 1 页发票【04721804】票面金额不自洽或关键要素缺失，未入池：价税合计=500.0 不含税=null 税额=null','OPEN',NULL,NULL,NULL,3832,'2026-11-09 10:52:59',3832,'2026-11-09 10:52:59',0),(5,1,5,'IMG2026110900002','FK202611090002','OCR_NOT_RECOGNIZED','第 1 页未识别出正式发票票面；可改传 XML/OFD 版式文件重新上传；或在「发票管理 › 发票补录」手工录入发票要素（技术详情见运维日志）','OPEN',NULL,NULL,NULL,3832,'2026-11-09 11:04:26',3832,'2026-11-09 11:04:26',0),(6,1,8,'IMG2026110900003','FK202611090002','OCR_AMOUNT_INCONSISTENT','第 1 页未识别出发票号码，票面要素不足以入池：价税合计=null 不含税=null 税额=null','OPEN',NULL,NULL,NULL,3832,'2026-11-09 13:46:42',3832,'2026-11-09 13:46:42',0),(7,1,9,'IMG2026110900004','FK202611090002','OCR_AMOUNT_INCONSISTENT','第 1 页未识别出发票号码，票面要素不足以入池：价税合计=null 不含税=null 税额=null','OPEN',NULL,NULL,NULL,3832,'2026-11-09 13:55:49',3832,'2026-11-09 13:55:49',0),(8,1,10,'IMG2026110900002','FK202611090002','OCR_AMOUNT_INCONSISTENT','第 1 页未识别出发票号码，票面要素不足以入池：价税合计=null 不含税=null 税额=null','OPEN',NULL,NULL,NULL,3832,'2026-11-09 14:02:02',3832,'2026-11-09 14:02:02',0),(9,1,11,'IMG2026110900005','FK202611090002','OCR_AMOUNT_INCONSISTENT','第 1 页未识别出发票号码，票面要素不足以入池：价税合计=null 不含税=null 税额=null','OPEN',NULL,NULL,NULL,3832,'2026-11-09 14:02:59',3832,'2026-11-09 14:02:59',0),(10,1,12,'IMG2026110900006','FK202611090002','OCR_AMOUNT_INCONSISTENT','第 1 页未识别出发票号码，票面要素不足以入池：价税合计=null 不含税=null 税额=null','OPEN',NULL,NULL,NULL,3832,'2026-11-09 14:05:35',3832,'2026-11-09 14:05:35',0),(11,1,13,'IMG2026110900001','FK202611090001','OCR_AMOUNT_INCONSISTENT','第 1 页未识别出发票号码，票面要素不足以入池：价税合计=null 不含税=null 税额=null','OPEN',NULL,NULL,NULL,3832,'2026-11-09 15:03:40',3832,'2026-11-09 15:03:40',0);
/*!40000 ALTER TABLE `image_exception` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:24:29
