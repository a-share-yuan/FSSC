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
-- Table structure for table `md_payee`
--

DROP TABLE IF EXISTS `md_payee`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_payee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `payee_type` varchar(20) NOT NULL DEFAULT 'EMPLOYEE' COMMENT '收款方类型:EMPLOYEE员工/SUPPLIER供应商/COMPANY内部公司/OTHER其他',
  `ref_id` bigint DEFAULT NULL COMMENT '关联主键(员工->sys_user.id / 供应商->md_supplier.id)',
  `payee_name` varchar(200) NOT NULL COMMENT '收款户名',
  `bank_name` varchar(200) DEFAULT NULL COMMENT '开户银行',
  `bank_branch` varchar(200) DEFAULT NULL COMMENT '开户支行',
  `bank_account` varchar(64) NOT NULL COMMENT '银行账号(加密存储)',
  `bank_code` varchar(64) DEFAULT NULL COMMENT '联行号',
  `account_type` varchar(20) NOT NULL DEFAULT 'PUBLIC' COMMENT '账户性质:PUBLIC对公/PRIVATE对私',
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认账户:0否 1是',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT '状态:NORMAL/DISABLED',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_ref` (`tenant_id`,`payee_type`,`ref_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3624 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收款方账户';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_payee`
--

LOCK TABLES `md_payee` WRITE;
/*!40000 ALTER TABLE `md_payee` DISABLE KEYS */;
INSERT INTO `md_payee` VALUES (3619,1,'SUPPLIER',18,'弘毅贸易有限公司','中国工商银行','工商银行北京顺义支行','6222020000000157',NULL,'对公','CNY',1,'NORMAL',NULL,NULL,'2026-09-12 11:41:43',NULL,'2026-10-23 20:23:48',0),(3620,1,'SUPPLIER',19,'晟泰贸易有限公司','中国工商银行','工商银行北京顺义支行','6222020000000158',NULL,'对公','CNY',1,'NORMAL',NULL,NULL,'2026-09-12 11:41:43',NULL,'2026-10-23 20:23:48',0),(3621,1,'SUPPLIER',20,'广联能源有限公司','中国工商银行','工商银行北京顺义支行','6222020000000159',NULL,'对公','CNY',1,'NORMAL',NULL,NULL,'2026-09-12 11:41:43',NULL,'2026-10-23 20:23:48',0),(3622,1,'CUSTOMER',1,'万象信息有限公司','中国建设银行','建设银行长沙湖大支行','6222020000000160',NULL,'对公','CNY',1,'NORMAL',NULL,NULL,'2026-09-12 11:41:43',NULL,'2026-10-23 20:23:48',0),(3623,1,'CUSTOMER',2,'东昇贸易有限公司','中国建设银行','建设银行长沙湖大支行','6222020000000161',NULL,'对公','CNY',1,'NORMAL',NULL,NULL,'2026-09-12 11:41:43',NULL,'2026-10-23 20:23:48',0);
/*!40000 ALTER TABLE `md_payee` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:14:06
