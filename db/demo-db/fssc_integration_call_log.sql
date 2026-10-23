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
-- Table structure for table `integration_call_log`
--

DROP TABLE IF EXISTS `integration_call_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT 'tenant id',
  `category` varchar(64) NOT NULL COMMENT 'ext_interface_config.category: AI_MODEL / OCR / INVOICE_VERIFY / INVOICE_CERTIFY / LINKU / BANK',
  `interface_code` varchar(128) DEFAULT NULL COMMENT 'ext_interface_config.interface_code',
  `method` varchar(10) NOT NULL COMMENT 'GET / POST',
  `request_url` varchar(512) NOT NULL COMMENT 'actual URL called',
  `request_body` text COMMENT 'JSON request body (truncated 4000 chars)',
  `response_body` text COMMENT 'JSON response body (truncated 4000 chars)',
  `status_code` int DEFAULT NULL COMMENT 'HTTP status code',
  `success` tinyint(1) NOT NULL DEFAULT '0' COMMENT '1=success, 0=fail',
  `duration_ms` int DEFAULT NULL COMMENT 'call duration in ms',
  `error_msg` varchar(1000) DEFAULT NULL COMMENT 'error message if failed',
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int DEFAULT '0',
  `idempotency_key` varchar(128) DEFAULT NULL COMMENT 'P0-2 幂等键 {domain}:{tenantId}:{businessNo}',
  `is_duplicate` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'P0-2 1=幂等命中回放',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT 'P0-3 已重试/重投次数',
  `trace_id` varchar(64) DEFAULT NULL COMMENT 'P0-3 MDC traceId',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_category_time` (`tenant_id`,`category`,`create_time`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_idem` (`tenant_id`,`idempotency_key`)
) ENGINE=InnoDB AUTO_INCREMENT=88 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Integration gateway unified call log';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `integration_call_log`
--

LOCK TABLES `integration_call_log` WRITE;
/*!40000 ALTER TABLE `integration_call_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `integration_call_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:14:47
