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
-- Table structure for table `op_calendar`
--

DROP TABLE IF EXISTS `op_calendar`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_calendar` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `company_id` bigint NOT NULL DEFAULT '0' COMMENT '组织(公司)维度:0=租户默认/集团通用日历;>0=该公司专属日历',
  `cal_date` date NOT NULL,
  `day_type` varchar(20) NOT NULL DEFAULT 'WORKDAY' COMMENT 'WORKDAY工作日/REST休息日/HOLIDAY节假日',
  `year` int DEFAULT NULL,
  `month` int DEFAULT NULL,
  `work_start` varchar(10) DEFAULT '08:30' COMMENT '在岗开始时间',
  `work_end` varchar(10) DEFAULT '17:30' COMMENT '在岗结束时间',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_op_cal_org` (`tenant_id`,`company_id`,`cal_date`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='共享工作日历';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_calendar`
--

LOCK TABLES `op_calendar` WRITE;
/*!40000 ALTER TABLE `op_calendar` DISABLE KEYS */;
INSERT INTO `op_calendar` VALUES (1,1,0,'2026-10-01','HOLIDAY',2026,10,'08:30','17:30',NULL,1,'2026-08-12 08:23:07',1,'2026-08-12 08:23:07',0);
/*!40000 ALTER TABLE `op_calendar` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:14:17
