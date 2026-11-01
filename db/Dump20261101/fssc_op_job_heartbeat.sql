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
-- Table structure for table `op_job_heartbeat`
--

DROP TABLE IF EXISTS `op_job_heartbeat`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_job_heartbeat` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_name` varchar(64) NOT NULL COMMENT '任务名（类名，如 RcOverdueJob）',
  `last_run_at` datetime DEFAULT NULL COMMENT '最近一次开始执行时间',
  `last_result` varchar(16) DEFAULT NULL COMMENT 'SUCCESS/FAIL/RUNNING',
  `duration_ms` bigint DEFAULT NULL COMMENT '最近一次执行耗时(ms)',
  `fail_count` int NOT NULL DEFAULT '0' COMMENT '连续失败次数',
  `last_error` varchar(1000) DEFAULT NULL COMMENT '最近一次失败错误摘要',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job` (`job_name`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时任务心跳与失败告警';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_job_heartbeat`
--

LOCK TABLES `op_job_heartbeat` WRITE;
/*!40000 ALTER TABLE `op_job_heartbeat` DISABLE KEYS */;
INSERT INTO `op_job_heartbeat` VALUES (1,'WfDelegateExpireJob','2026-11-01 17:57:23','SUCCESS',18,0,NULL,'2026-11-01 17:57:23'),(2,'HealthProbeJob','2026-11-01 18:02:24','SUCCESS',24,0,NULL,'2026-11-01 18:02:24'),(3,'AutoDispatchJob','2026-11-01 18:06:03','SUCCESS',19,0,NULL,'2026-11-01 18:06:03'),(4,'WfTimeoutJob','2026-11-01 18:06:03','SUCCESS',5,0,NULL,'2026-11-01 18:06:03'),(5,'WfLedgerReconcileJob','2026-11-01 17:58:24','SUCCESS',13,0,NULL,'2026-11-01 17:58:24'),(6,'WfDelegateTakeoverSweep','2026-11-01 17:58:24','SUCCESS',5,0,NULL,'2026-11-01 17:58:24'),(7,'InvoiceAbnormalAlertJob','2026-11-01 18:05:54','SUCCESS',27,0,NULL,'2026-11-01 18:05:54'),(8,'InvoiceVerifyScheduler','2026-11-01 18:01:24','SUCCESS',25,0,NULL,'2026-11-01 18:01:24'),(9,'OpTaskDueTimeScanJob','2026-11-01 18:01:24','SUCCESS',8,0,NULL,'2026-11-01 18:01:24');
/*!40000 ALTER TABLE `op_job_heartbeat` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:06:13
