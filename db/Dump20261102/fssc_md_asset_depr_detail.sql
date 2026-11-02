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
-- Table structure for table `md_asset_depr_detail`
--

DROP TABLE IF EXISTS `md_asset_depr_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_asset_depr_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `asset_id` bigint NOT NULL COMMENT '资产卡片ID (md_asset.id)',
  `batch_id` bigint DEFAULT NULL COMMENT '折旧批单ID (asset_depr_batch.id)',
  `period` varchar(7) NOT NULL COMMENT '计提期间 YYYY-MM',
  `monthly_depr` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '本月折旧额',
  `accum_depr_after` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '计提后累计折旧',
  `net_amount_after` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '计提后账面净值',
  `depr_date` date NOT NULL COMMENT '计提日期',
  `source_voucher_id` bigint DEFAULT NULL COMMENT '关联凭证ID (op_voucher.id)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_asset_depr_detail_asset` (`tenant_id`,`asset_id`),
  KEY `idx_asset_depr_detail_batch` (`batch_id`),
  KEY `idx_asset_depr_detail_period` (`period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产折旧明细（逐资产逐月）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_asset_depr_detail`
--

LOCK TABLES `md_asset_depr_detail` WRITE;
/*!40000 ALTER TABLE `md_asset_depr_detail` DISABLE KEYS */;
/*!40000 ALTER TABLE `md_asset_depr_detail` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:13:53
