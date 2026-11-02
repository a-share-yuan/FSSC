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
-- Table structure for table `op_archive_item`
--

DROP TABLE IF EXISTS `op_archive_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `op_archive_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `archive_id` bigint NOT NULL COMMENT '所属案卷 op_archive.id',
  `seq_no` int NOT NULL COMMENT '卷内件号，自 1 连续（DA/T 22 件号）',
  `bill_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源单据类型（他域按 bizType 解释）',
  `bill_id` bigint DEFAULT NULL COMMENT '来源单据主键；费用域＝exp_bill.id',
  `bill_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源单据编号（冗余，供卷内目录直显）',
  `voucher_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源凭证号（冗余）',
  `item_date` date DEFAULT NULL COMMENT '件日期（业务日期/凭证日期）',
  `title` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '件题名（默认取单据标题）',
  `page_from` int DEFAULT NULL COMMENT '起页（P1 编页；纸质卷必填）',
  `page_count` int DEFAULT NULL COMMENT '页数（P1；电子卷以文件数替代）',
  `file_ids` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '该件的文件ID清单，逗号串，顺序即卷内文件顺序',
  `content_hash` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '件级快照哈希（卷级哈希＝件清单哈希⊕各件哈希）',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '件金额（卷合计与审批核对用）',
  `company_id` bigint DEFAULT NULL COMMENT '件所属公司；跨公司混卷，入卷清单按本公司过滤',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_arc_item_seq` (`tenant_id`,`archive_id`,`seq_no`),
  KEY `idx_arc_item_bill` (`tenant_id`,`bill_id`,`del_flag`),
  KEY `idx_arc_item_archive` (`archive_id`,`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案卷卷内件清单（一卷多件）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `op_archive_item`
--

LOCK TABLES `op_archive_item` WRITE;
/*!40000 ALTER TABLE `op_archive_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `op_archive_item` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:13:30
