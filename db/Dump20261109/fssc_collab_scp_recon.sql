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
-- Table structure for table `collab_scp_recon`
--

DROP TABLE IF EXISTS `collab_scp_recon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collab_scp_recon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `recon_no` varchar(48) NOT NULL COMMENT '瀵硅处鍗曞彿(涓夊崟鍖归厤鏈?皬鍗曚綅)',
  `supplier_code` varchar(48) DEFAULT NULL COMMENT '渚涘簲鍟嗕唬鐮',
  `supplier_name` varchar(128) DEFAULT NULL,
  `buyer_company_code` varchar(32) DEFAULT NULL COMMENT '璐?柟鍏?徃浠ｇ爜',
  `invoice_type` varchar(16) DEFAULT NULL COMMENT '澧炰笓/澧炴櫘(R-30,纭??鏃堕?)',
  `ccy` varchar(8) DEFAULT 'CNY',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '涓嶅惈绋庨噾棰',
  `tax_amount` decimal(18,2) DEFAULT NULL COMMENT '绋庨?',
  `total_amount` decimal(18,2) DEFAULT NULL COMMENT '浠风◣鍚堣?',
  `lines_json` text COMMENT '鏀惰揣/鎶樻墸/绱㈣禂/閫?揣鏄庣粏琛?E1瀵煎叆)',
  `objection_note` varchar(500) DEFAULT NULL COMMENT '寮傝?鍙嶉?(E5)鍙婂?鐞嗙暀鐥',
  `objection_status` varchar(16) DEFAULT 'NONE' COMMENT 'NONE/SUBMITTED/RESOLVED',
  `status` varchar(16) NOT NULL DEFAULT 'PRELIM' COMMENT 'PRELIM棰勫埗寰呯‘璁?CONFIRMED鏈?粓瀵硅处鍗?VOIDED宸蹭綔搴',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recon_no` (`tenant_id`,`recon_no`),
  KEY `idx_supplier` (`tenant_id`,`supplier_code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SCP渚涘簲鍟嗗?璐﹀崟(钃濆浘SCC_AP_01/02,E1~E6)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `collab_scp_recon`
--

LOCK TABLES `collab_scp_recon` WRITE;
/*!40000 ALTER TABLE `collab_scp_recon` DISABLE KEYS */;
/*!40000 ALTER TABLE `collab_scp_recon` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:24:26
