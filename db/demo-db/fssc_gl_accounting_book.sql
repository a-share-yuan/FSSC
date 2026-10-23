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
-- Table structure for table `gl_accounting_book`
--

DROP TABLE IF EXISTS `gl_accounting_book`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gl_accounting_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `book_code` varchar(32) NOT NULL COMMENT '账簿编码',
  `book_name` varchar(100) NOT NULL COMMENT '账簿名称',
  `book_type` varchar(16) NOT NULL DEFAULT 'MAIN' COMMENT '账簿类型:MAIN主账簿/SUB副账簿(对齐口径)',
  `company_id` bigint NOT NULL COMMENT '核算组织 sys_company.id',
  `standard_id` bigint NOT NULL COMMENT '会计准则 gl_standard.id',
  `base_currency_id` bigint NOT NULL COMMENT '本位币 gl_currency.id',
  `accounting_year` int NOT NULL COMMENT '会计年度',
  `fiscal_start` date DEFAULT NULL COMMENT '会计年度起始日(非1月1日时用,如4月制)',
  `fiscal_end` date DEFAULT NULL COMMENT '会计年度结束日',
  `status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN启用/CLOOSED已关账',
  `closed_time` datetime DEFAULT NULL COMMENT '关账时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gab_code` (`tenant_id`,`book_code`),
  UNIQUE KEY `uk_gab_main` (`tenant_id`,`company_id`,`standard_id`,`accounting_year`,`book_type`),
  KEY `idx_gab_tenant_company` (`tenant_id`,`company_id`,`accounting_year`),
  KEY `idx_gab_status` (`tenant_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会计账簿（主账簿/副账簿，含准则与本位币）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gl_accounting_book`
--

LOCK TABLES `gl_accounting_book` WRITE;
/*!40000 ALTER TABLE `gl_accounting_book` DISABLE KEYS */;
INSERT INTO `gl_accounting_book` VALUES (18,1,'BK-AIG-HQ-ASBE-2026','上海爱共享集团有限公司 企业会计准则主账簿','MAIN',1,4,6,2026,'2026-01-01','2026-12-31','OPEN',NULL,'ASBE 主账簿(V1.4.88)',NULL,'2026-10-18 11:23:07',NULL,NULL,0),(19,1,'BK-AIG-SH-ASBE-2026','上海爱共享销售有限公司 企业会计准则主账簿','MAIN',31,4,6,2026,'2026-01-01','2026-12-31','OPEN',NULL,'ASBE 主账簿(V1.4.88)',NULL,'2026-10-18 11:23:07',NULL,NULL,0),(20,1,'BK-AIG-HN-ASBE-2026','湖南爱共享科技有限公司 企业会计准则主账簿','MAIN',32,4,6,2026,'2026-01-01','2026-12-31','OPEN',NULL,'ASBE 主账簿(V1.4.88)',NULL,'2026-10-18 11:23:07',NULL,NULL,0),(21,1,'BK-AIG-KS-ASBE-2026','昆山爱共享精密制造有限公司 企业会计准则主账簿','MAIN',33,4,6,2026,'2026-01-01','2026-12-31','OPEN',NULL,'ASBE 主账簿(V1.4.88)',NULL,'2026-10-18 11:23:07',NULL,NULL,0),(22,1,'BK-AIG-SZ-ASBE-2026','苏州爱共享智能制造有限公司 企业会计准则主账簿','MAIN',34,4,6,2026,'2026-01-01','2026-12-31','OPEN',NULL,'ASBE 主账簿(V1.4.88)',NULL,'2026-10-18 11:23:07',NULL,NULL,0),(23,1,'BK-AIG-RD-ASBE-2026','上海爱共享研发中心有限公司 企业会计准则主账簿','MAIN',35,4,6,2026,'2026-01-01','2026-12-31','OPEN',NULL,'ASBE 主账簿(V1.4.88)',NULL,'2026-10-18 11:23:07',NULL,NULL,0),(24,1,'BK-AIG-GZ-ASBE-2026','广州爱共享贸易有限公司 企业会计准则主账簿','MAIN',36,4,6,2026,'2026-01-01','2026-12-31','OPEN',NULL,'ASBE 主账簿(V1.4.88)',NULL,'2026-10-18 11:23:07',NULL,NULL,0),(25,1,'BK-AIG-BJ-ASBE-2026','北京爱共享信息咨询有限公司 企业会计准则主账簿','MAIN',37,4,6,2026,'2026-01-01','2026-12-31','OPEN',NULL,'ASBE 主账簿(V1.4.88)',NULL,'2026-10-18 11:23:07',NULL,NULL,0),(26,1,'BK-AIG-CS-ASBE-2026','长沙爱共享服务有限公司 企业会计准则主账簿','MAIN',38,4,6,2026,'2026-01-01','2026-12-31','OPEN',NULL,'ASBE 主账簿(V1.4.88)',NULL,'2026-10-18 11:23:07',NULL,NULL,0),(33,1,'BK-AIG-HQ-SME-2026','上海爱共享集团 小企业会计准则主账簿','MAIN',1,5,6,2026,'2026-01-01','2026-12-31','OPEN',NULL,'SME 主账簿(与 ASBE 并存，验证多准则)(V1.4.88)',NULL,'2026-10-18 11:23:07',NULL,NULL,0),(34,1,'BK-AIG-HQ-ASBE-USD-2026','上海爱共享集团 外币核算副账簿(USD)','SUB',1,4,7,2026,'2026-01-01','2026-12-31','OPEN',NULL,'ASBE 副账簿·外币口径(V1.4.88)',NULL,'2026-10-18 11:23:07',NULL,NULL,0);
/*!40000 ALTER TABLE `gl_accounting_book` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:14:45
