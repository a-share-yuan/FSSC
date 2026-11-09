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
-- Table structure for table `linku_sync_log`
--

DROP TABLE IF EXISTS `linku_sync_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `linku_sync_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型: RECON_CONFIRM/RECON_MATCH/DIFF_RESOLVED/RECON_CONFIRMED/INVOICE_VERIFIED',
  `linku_recon_id` bigint DEFAULT NULL COMMENT 'LinkU侧对账单ID',
  `supplier_id` bigint DEFAULT NULL COMMENT 'FSSC侧供应商ID',
  `request_body` text COMMENT '回调请求体JSON',
  `idempotency_key` varchar(128) DEFAULT NULL COMMENT '幂等键（开放接口写入端点：租户+业务主键+期间，重复调用据此判定 alreadyProcessed）',
  `response_status` varchar(16) DEFAULT 'SUCCESS' COMMENT '处理结果: SUCCESS/FAILED',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `duration_ms` int DEFAULT NULL COMMENT '处理耗时(毫秒)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
  `active_idem_key` varchar(200) GENERATED ALWAYS AS ((case when ((`response_status` = _utf8mb4'SUCCESS') and (`idempotency_key` is not null) and (`idempotency_key` <> _utf8mb4'')) then concat(`event_type`,_utf8mb4':',`idempotency_key`) else NULL end)) VIRTUAL COMMENT '成功且带幂等键的行才参与去重；FAILED 与无键查询均为 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_linku_sync_log_idem` (`active_idem_key`),
  KEY `idx_linku_recon_id` (`linku_recon_id`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='LinkU回调同步日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `linku_sync_log`
--

LOCK TABLES `linku_sync_log` WRITE;
/*!40000 ALTER TABLE `linku_sync_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `linku_sync_log` ENABLE KEYS */;
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
