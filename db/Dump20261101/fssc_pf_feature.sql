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
-- Table structure for table `pf_feature`
--

DROP TABLE IF EXISTS `pf_feature`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pf_feature` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `feature_code` varchar(64) NOT NULL COMMENT '功能编码,如 F_EXPENSE_CLAIM',
  `feature_name` varchar(100) NOT NULL COMMENT '功能名称',
  `module` varchar(50) NOT NULL COMMENT '所属模块:SYSTEM/MASTERDATA/EXPENSE/BUDGET/WORKFLOW/SHARING/IMAGE/INVOICE/REPORT',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '说明',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feature_code` (`feature_code`),
  KEY `idx_module` (`module`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='功能点定义[平台表-无tenant_id]';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pf_feature`
--

LOCK TABLES `pf_feature` WRITE;
/*!40000 ALTER TABLE `pf_feature` DISABLE KEYS */;
INSERT INTO `pf_feature` VALUES (1,'F_EXPENSE_CLAIM','费用报销','EXPENSE',10,NULL,NULL,'2026-08-11 14:51:53',NULL,'2026-08-11 14:51:53',0),(2,'F_EXPENSE_APPLY','事前申请','EXPENSE',20,NULL,NULL,'2026-08-11 14:51:53',NULL,'2026-08-11 14:51:53',0),(3,'F_LOAN','借款管理','EXPENSE',30,NULL,NULL,'2026-08-11 14:51:53',NULL,'2026-08-11 14:51:53',0),(4,'F_BUDGET','预算管理','BUDGET',40,NULL,NULL,'2026-08-11 14:51:53',NULL,'2026-08-11 14:51:53',0),(5,'F_IMAGE','影像管理','IMAGE',50,NULL,NULL,'2026-08-11 14:51:53',NULL,'2026-08-11 14:51:53',0),(6,'FSSC_TAX','税务管理','INVOICE',100,'进项发票认证/勾选/转出 + 退税三场景（出口免抵退/留抵退税/即征即退）',NULL,'2026-08-29 08:29:34',NULL,'2026-08-29 08:29:34',0),(7,'FSSC_BANK','银企对账','SHARING',110,'银企直连对账：流水勾对 / 收款认领核销 / 回单归档 / 未达账项调节表',NULL,'2026-08-29 08:29:34',NULL,'2026-08-29 08:29:34',0),(8,'FSSC_ARCHIVE','电子档案','SHARING',120,'电子会计档案：归档 / 调阅 / 到期销毁（双人复核）',NULL,'2026-08-29 08:29:34',NULL,'2026-08-29 08:29:34',0),(9,'FSSC_SHARED','共享运营','SHARING',130,'共享运营实质化域：员工信用 / SLA 监控与阈值配置 / 质检抽检 / 满意度调查',NULL,'2026-10-17 06:36:27',NULL,'2026-10-17 06:36:27',0);
/*!40000 ALTER TABLE `pf_feature` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:05:37
