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
-- Table structure for table `pf_package`
--

DROP TABLE IF EXISTS `pf_package`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pf_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `package_code` varchar(64) NOT NULL COMMENT '套餐编码',
  `package_name` varchar(100) NOT NULL COMMENT '套餐名称',
  `package_type` varchar(20) NOT NULL DEFAULT 'STANDARD' COMMENT '类型:TRIAL试用/STANDARD标准/PRO专业/ENTERPRISE旗舰/CUSTOM定制',
  `price_month` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '月单价',
  `price_year` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '年单价',
  `max_user` int NOT NULL DEFAULT '0' COMMENT '用户数上限,0不限',
  `max_company` int NOT NULL DEFAULT '0' COMMENT '纳税主体数上限,0不限',
  `max_storage_mb` bigint NOT NULL DEFAULT '0' COMMENT '存储空间上限MB,0不限',
  `max_api_daily` bigint NOT NULL DEFAULT '0' COMMENT '日API调用次数上限,0不限',
  `max_bill_month` bigint NOT NULL DEFAULT '0' COMMENT '月单据量上限,0不限',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0禁用',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_code` (`package_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='套餐定义[平台表-无tenant_id]';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pf_package`
--

LOCK TABLES `pf_package` WRITE;
/*!40000 ALTER TABLE `pf_package` DISABLE KEYS */;
INSERT INTO `pf_package` VALUES (1,'STANDARD','标准版','STANDARD',0.00,0.00,0,0,0,0,0,1,0,NULL,NULL,'2026-08-11 14:51:53',NULL,'2026-11-07 08:42:03',0),(2,'PRO','专业版','PRO',0.00,0.00,0,0,0,0,0,1,0,NULL,NULL,'2026-08-11 14:51:53',NULL,'2026-11-07 08:42:03',0),(3,'DEMO','演示版','CUSTOM',0.00,0.00,0,0,0,0,0,1,90,'演示环境套餐：人数不限（0=不限），功能与标准版一致',NULL,'2026-11-06 14:27:04',NULL,'2026-11-06 14:27:04',0),(7,'FC_BASIC','报销版','BASIC',0.00,0.00,0,0,0,0,0,1,10,'费用报销产品档位：报销版',NULL,'2026-11-07 00:43:27',NULL,'2026-11-07 08:42:03',0),(8,'FC_STANDARD','费控版','STANDARD',0.00,0.00,0,0,0,0,0,1,20,'费用报销产品档位：费控版',NULL,'2026-11-07 00:43:27',NULL,'2026-11-07 08:42:03',0),(9,'FC_PRO','共享版','PRO',0.00,0.00,0,0,0,0,0,1,30,'费用报销产品档位：共享版',NULL,'2026-11-07 00:43:27',NULL,'2026-11-07 00:43:27',0);
/*!40000 ALTER TABLE `pf_package` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:23:55
