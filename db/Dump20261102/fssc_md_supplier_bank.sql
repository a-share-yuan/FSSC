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
-- Table structure for table `md_supplier_bank`
--

DROP TABLE IF EXISTS `md_supplier_bank`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_supplier_bank` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `supplier_id` bigint NOT NULL,
  `bank_name` varchar(200) NOT NULL COMMENT '开户行',
  `bank_branch` varchar(200) DEFAULT NULL COMMENT '支行',
  `bank_account` varchar(64) NOT NULL COMMENT '银行账号',
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '1=默认收款账户',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_msb_supplier` (`supplier_id`),
  KEY `idx_msb_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商银行账户(N)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_supplier_bank`
--

LOCK TABLES `md_supplier_bank` WRITE;
/*!40000 ALTER TABLE `md_supplier_bank` DISABLE KEYS */;
INSERT INTO `md_supplier_bank` VALUES (1,1,6100,'中国工商银行昆山分行','昆山玉山镇支行','6222020000000000167','CNY',0,'NORMAL',NULL,3897,'2026-09-20 05:29:38',3897,'2026-10-23 20:23:48',0),(2,1,28,'工商银行长沙河西支行',NULL,'6222020000000000168','CNY',0,'NORMAL','供应商收款账户 [V3-第2轮]',3897,'2026-09-30 07:44:10',3897,'2026-10-23 20:23:48',0),(3,1,18,'中国工商银行上海分行','工行上海静安支行','6222020000000000169','CNY',0,'NORMAL',NULL,3897,'2026-10-02 23:19:11',3897,'2026-10-23 20:23:48',0),(4,1,19,'中国建设银行上海分行','建行上海浦东支行','6222020000000000170','CNY',0,'NORMAL',NULL,3897,'2026-10-03 00:52:12',3897,'2026-10-23 20:23:48',0),(5,1,29167,'演示银行营业部',NULL,'6222020000000171','CNY',1,'NORMAL','G册环境补齐：V1003 主数据在册账号（手册 G2.4a 假设存在）',3897,'2026-10-17 00:06:45',3897,'2026-10-23 20:23:48',0);
/*!40000 ALTER TABLE `md_supplier_bank` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:14:08
