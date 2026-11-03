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
-- Table structure for table `audit_trail`
--

DROP TABLE IF EXISTS `audit_trail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_trail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `biz_type` varchar(32) NOT NULL COMMENT '业务类型（单据/凭证/付款/预算）',
  `biz_id` bigint DEFAULT NULL COMMENT '业务ID',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '业务单号',
  `obj_type` varchar(64) DEFAULT NULL COMMENT '变更对象类型',
  `obj_id` varchar(64) DEFAULT NULL COMMENT '变更对象ID',
  `action` varchar(32) NOT NULL COMMENT 'INSERT/UPDATE/APPROVE/PAY/REJECT/CHECKSUM',
  `before_json` longtext COMMENT '变更前JSON',
  `after_json` longtext COMMENT '变更后JSON',
  `operator` bigint DEFAULT NULL COMMENT '操作人',
  `remark` varchar(500) DEFAULT NULL COMMENT '变更说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tr_biz` (`tenant_id`,`biz_type`,`biz_id`),
  KEY `idx_tr_no` (`tenant_id`,`biz_no`),
  KEY `idx_tr_time` (`tenant_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关键变更审计流水（只追加）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_trail`
--

LOCK TABLES `audit_trail` WRITE;
/*!40000 ALTER TABLE `audit_trail` DISABLE KEYS */;
INSERT INTO `audit_trail` VALUES (47,1,'SUPPLIER_ADD',133299,'TEST-SUP-101749','md_supplier','133299','UPDATE','{\"status\":\"PENDING\"}','{\"status\":\"ACTIVE\"}',3874,'状态位核准（页面直接核准）','2026-11-03 10:17:53'),(48,1,'SUPPLIER_ADD',133300,'TEST-SUP-102721','md_supplier','133300','UPDATE','{\"status\":\"PENDING\"}','{\"status\":\"ACTIVE\"}',3874,'状态位核准（页面直接核准）','2026-11-03 10:27:25'),(49,1,'SUPPLIER_ADD',133301,'TEST-SUP-103216','md_supplier','133301','UPDATE','{\"status\":\"PENDING\"}','{\"status\":\"ACTIVE\"}',3874,'状态位核准（页面直接核准）','2026-11-03 10:32:18');
/*!40000 ALTER TABLE `audit_trail` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:37
