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
-- Table structure for table `tax_risk_indicator`
--

DROP TABLE IF EXISTS `tax_risk_indicator`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tax_risk_indicator` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `indicator_code` varchar(64) NOT NULL COMMENT '指标编码',
  `indicator_name` varchar(128) NOT NULL COMMENT '指标名称',
  `risk_type` varchar(32) NOT NULL COMMENT '风险类型：INPUT_OUTPUT_RATIO进销项比/TAX_BURDEN税负率/INVOICE_ANOMALY发票异常/DECLARE_GAP申报差异',
  `period` varchar(6) DEFAULT NULL COMMENT '风险属期 yyyyMM',
  `threshold_value` decimal(18,4) DEFAULT NULL COMMENT '阈值',
  `actual_value` decimal(18,4) DEFAULT NULL COMMENT '实际值',
  `risk_score` int NOT NULL DEFAULT '0' COMMENT '风险评分（0-100）',
  `risk_level` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL正常/ATTENTION关注/WARNING预警/HIGH高风险',
  `description` varchar(512) DEFAULT NULL COMMENT '风险描述',
  `suggestion` varchar(512) DEFAULT NULL COMMENT '处理建议',
  `resolved` tinyint NOT NULL DEFAULT '0' COMMENT '是否已处理：0=未处理 1=已处理',
  `resolved_by` bigint DEFAULT NULL COMMENT '处理人',
  `resolved_time` datetime DEFAULT NULL COMMENT '处理时间',
  `resolved_remark` varchar(512) DEFAULT NULL COMMENT '处理说明',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_risk_type` (`risk_type`,`period`),
  KEY `idx_risk_level` (`risk_level`,`resolved`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='税务风险指标（风险评分/等级/预警监控）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tax_risk_indicator`
--

LOCK TABLES `tax_risk_indicator` WRITE;
/*!40000 ALTER TABLE `tax_risk_indicator` DISABLE KEYS */;
/*!40000 ALTER TABLE `tax_risk_indicator` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:05:58
