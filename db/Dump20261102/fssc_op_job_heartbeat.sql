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
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时任务心跳与失败告警';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_job_heartbeat`
--

LOCK TABLES `op_job_heartbeat` WRITE;
/*!40000 ALTER TABLE `op_job_heartbeat` DISABLE KEYS */;
INSERT INTO `op_job_heartbeat` VALUES (1,'WfDelegateExpireJob','2026-11-02 08:07:30','SUCCESS',14,0,NULL,'2026-11-02 08:07:30'),(2,'HealthProbeJob','2026-11-02 08:12:31','SUCCESS',41,0,NULL,'2026-11-02 08:12:31'),(3,'AutoDispatchJob','2026-11-02 08:13:54','SUCCESS',17,0,NULL,'2026-11-02 08:13:54'),(4,'WfTimeoutJob','2026-11-02 08:13:56','SUCCESS',4,0,NULL,'2026-11-02 08:13:56'),(5,'WfLedgerReconcileJob','2026-11-02 08:08:27','SUCCESS',30,0,NULL,'2026-11-02 08:08:27'),(6,'WfDelegateTakeoverSweep','2026-11-02 08:08:27','SUCCESS',10,0,NULL,'2026-11-02 08:08:27'),(7,'InvoiceAbnormalAlertJob','2026-11-02 08:11:01','SUCCESS',12,0,NULL,'2026-11-02 08:11:01'),(8,'InvoiceVerifyScheduler','2026-11-02 08:11:30','SUCCESS',21,0,NULL,'2026-11-02 08:11:30'),(9,'OpTaskDueTimeScanJob','2026-11-02 08:11:30','SUCCESS',5,0,NULL,'2026-11-02 08:11:30'),(10,'StabilityLogJob','2026-11-01 23:55:02','SUCCESS',5182,0,NULL,'2026-11-01 23:55:08'),(11,'FxRevalJob','2026-11-02 01:10:00','SUCCESS',2126,0,NULL,'2026-11-02 01:10:02'),(12,'RcOverdueJob','2026-11-02 01:20:00','SUCCESS',363,0,NULL,'2026-11-02 01:20:01'),(13,'RptSummaryService','2026-11-02 01:30:01','SUCCESS',431,0,NULL,'2026-11-02 01:30:01'),(14,'ReconcileService','2026-11-02 02:00:00','SUCCESS',184,0,NULL,'2026-11-02 02:00:00'),(15,'ArchiveService','2026-11-02 02:00:01','SUCCESS',318,0,NULL,'2026-11-02 02:00:01'),(16,'ArchiveExpireJob','2026-11-02 02:30:00','SUCCESS',300,0,NULL,'2026-11-02 02:30:01'),(17,'PoOrderFinalStateJob','2026-11-02 03:10:00','SUCCESS',815,0,NULL,'2026-11-02 03:10:01'),(18,'WfHistoryArchiveService','2026-11-02 03:30:01','SUCCESS',184,0,NULL,'2026-11-02 03:30:01'),(19,'WfZombieReconcileJob','2026-11-02 03:40:00','SUCCESS',268,0,NULL,'2026-11-02 03:40:01'),(20,'ColdArchiveJob','2026-11-02 03:40:01','SUCCESS',43,0,NULL,'2026-11-02 03:40:01'),(21,'OrgHealthJob','2026-11-02 04:10:00','SUCCESS',733,0,NULL,'2026-11-02 04:10:01');
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

-- Dump completed on 2026-11-02  8:14:00
