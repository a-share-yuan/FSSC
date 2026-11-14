CREATE DATABASE  IF NOT EXISTS `fssc_image` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `fssc_image`;
-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: 192.168.20.77    Database: fssc_image
-- ------------------------------------------------------
-- Server version	8.0.46-0ubuntu0.22.04.5

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
-- Table structure for table `img_setting`
--

DROP TABLE IF EXISTS `img_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `img_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `setting_type` varchar(32) NOT NULL COMMENT 'BIZ_TYPE业务类型/FILE_KIND文件种类识别',
  `setting_code` varchar(64) NOT NULL,
  `setting_name` varchar(200) NOT NULL,
  `ext_info` varchar(1000) DEFAULT NULL COMMENT '扩展JSON(识别规则/关联配置)',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setting` (`tenant_id`,`setting_type`,`setting_code`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='影像系统设置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `img_setting`
--

LOCK TABLES `img_setting` WRITE;
/*!40000 ALTER TABLE `img_setting` DISABLE KEYS */;
INSERT INTO `img_setting` VALUES (1,1,'BIZ_TYPE','EXPENSE','费用报销单据','{\"bizTable\":\"exp_bill\"}','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(2,1,'BIZ_TYPE','CONTRACT','合同','{\"bizTable\":\"cont_contract\"}','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(3,1,'FILE_KIND','INVOICE','发票影像','{\"recognize\":\"OCR\"}','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(4,1,'FILE_KIND','ATTACH','附件','{\"recognize\":\"MANUAL\"}','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(5,1,'FILE_KIND','TRAIN','火车票','{\"recognize\":\"OCR\"}','NORMAL',NULL,1,'2026-08-12 10:59:47',1,'2026-08-12 10:59:47',0);
/*!40000 ALTER TABLE `img_setting` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:28:11
