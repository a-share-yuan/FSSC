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
-- Table structure for table `op_stability_log`
--

DROP TABLE IF EXISTS `op_stability_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_stability_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `log_date` date NOT NULL COMMENT '记录日期',
  `uptime_seconds` bigint DEFAULT NULL COMMENT '当日运行时长(秒)',
  `restart_count` int DEFAULT '0' COMMENT '当日重启次数',
  `p0_count` int DEFAULT '0' COMMENT '当日P0缺陷数',
  `p1_count` int DEFAULT '0' COMMENT '当日P1缺陷数',
  `health_check_failures` int DEFAULT '0' COMMENT '健康探测失败次数',
  `job_failures` int DEFAULT '0' COMMENT '定时任务失败次数',
  `max_heap_used_mb` int DEFAULT NULL COMMENT '当日堆内存峰值(MB)',
  `max_pool_active` int DEFAULT NULL COMMENT '连接池最大活跃数',
  `oom_events` int DEFAULT '0' COMMENT 'OOM事件数',
  `notes` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `log_date` (`log_date`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='稳定性运行日志(STAB-02,每日23:55由StabilityLogJob写入)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_stability_log`
--

LOCK TABLES `op_stability_log` WRITE;
/*!40000 ALTER TABLE `op_stability_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_stability_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:47:59
