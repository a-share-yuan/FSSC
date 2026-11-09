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
-- Table structure for table `img_batch`
--

DROP TABLE IF EXISTS `img_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `img_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `batch_no` varchar(64) NOT NULL COMMENT '影像批次号',
  `scan_user_id` bigint DEFAULT NULL COMMENT '扫描/上传人',
  `source` varchar(20) NOT NULL DEFAULT 'H5' COMMENT '来源:H5/EOS扫描仪/PC/API',
  `doc_count` int NOT NULL DEFAULT '0' COMMENT '影像张数',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '关联业务类型',
  `biz_id` bigint DEFAULT NULL COMMENT '关联业务单据ID',
  `status` varchar(20) NOT NULL DEFAULT 'UPLOADED' COMMENT '状态:UPLOADED已上传/CHECKING核验中/CHECKED已核验/BACK退回',
  `check_user_id` bigint DEFAULT NULL COMMENT '核验人',
  `check_time` datetime DEFAULT NULL COMMENT '核验时间',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `seal_status` varchar(20) NOT NULL DEFAULT 'UNSEALED' COMMENT '封单状态 SEALED/UNSEALED',
  `review_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '调阅审核 PENDING/OK/REJECTED',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '业务单号冗余',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_batch_no` (`tenant_id`,`batch_no`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_biz` (`tenant_id`,`biz_type`,`biz_id`),
  KEY `idx_tenant_status` (`tenant_id`,`status`),
  KEY `idx_ib_tenant_del_biz` (`tenant_id`,`del_flag`,`biz_type`,`doc_count`,`review_status`,`seal_status`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='影像批次';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `img_batch`
--

LOCK TABLES `img_batch` WRITE;
/*!40000 ALTER TABLE `img_batch` DISABLE KEYS */;
INSERT INTO `img_batch` VALUES (2,1,'IMG2026110400001',3832,'PC',1,'PAY',NULL,'UPLOADED',NULL,NULL,NULL,'2026-11-04 17:45:18','2026-11-09 13:50:14',0,'UNSEALED','PENDING',''),(3,1,'IMG2026110400002',3832,'PC',1,'PAY',NULL,'UPLOADED',NULL,NULL,NULL,'2026-11-04 17:45:46','2026-11-09 13:50:14',0,'UNSEALED','PENDING','');
/*!40000 ALTER TABLE `img_batch` ENABLE KEYS */;
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
