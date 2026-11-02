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
-- Table structure for table `op_job`
--

DROP TABLE IF EXISTS `op_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `job_code` varchar(50) NOT NULL,
  `job_name` varchar(100) NOT NULL,
  `work_items` varchar(200) DEFAULT NULL COMMENT '作业项（审核/记账/支付处理等）',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_op_job` (`tenant_id`,`job_code`)
) ENGINE=InnoDB AUTO_INCREMENT=700000005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='共享作业岗位';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_job`
--

LOCK TABLES `op_job` WRITE;
/*!40000 ALTER TABLE `op_job` DISABLE KEYS */;
INSERT INTO `op_job` VALUES (700000001,1,'JOB-AUDIT','单据审核岗','审核','NORMAL','V1.4.81 造数作业',NULL,'2026-10-18 10:56:58',NULL,'2026-10-18 10:56:58',0),(700000002,1,'JOB-BOOK','记账岗','记账','NORMAL','V1.4.81 造数作业',NULL,'2026-10-18 10:56:58',NULL,'2026-10-18 10:56:58',0),(700000003,1,'JOB-PAY','支付处理岗','支付处理','NORMAL','V1.4.81 造数作业',NULL,'2026-10-18 10:56:58',NULL,'2026-10-18 10:56:58',0),(700000004,1,'JOB-CASH','出纳岗','资金收付','NORMAL','V1.4.81 造数作业',NULL,'2026-10-18 10:56:58',NULL,'2026-10-18 10:56:58',0);
/*!40000 ALTER TABLE `op_job` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:13:57
