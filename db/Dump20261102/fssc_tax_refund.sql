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
-- Table structure for table `tax_refund`
--

DROP TABLE IF EXISTS `tax_refund`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tax_refund` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `refund_no` varchar(64) DEFAULT NULL COMMENT '退税申请单号',
  `refund_type` varchar(32) NOT NULL COMMENT '退税类型 EXPORT_REFUND=出口免抵退/CREDIT_REFUND=留抵退税/IMMEDIATE_REFUND=即征即退',
  `period` varchar(16) DEFAULT NULL COMMENT '所属期(YYYYMM)',
  `base_amount` decimal(18,2) DEFAULT NULL COMMENT '计税基数(出口销售额=免抵退基数/即征即退=已缴增值税)',
  `rate` decimal(10,2) DEFAULT NULL COMMENT '退税率(百分数，如 13=13%)',
  `tax_amount` decimal(18,2) DEFAULT NULL COMMENT '可退税额',
  `refunded_amount` decimal(18,2) DEFAULT '0.00' COMMENT '已到账金额',
  `avg_quota` decimal(18,2) DEFAULT NULL COMMENT '留抵退税: 可退留抵税额上限',
  `status` varchar(20) DEFAULT 'DRAFT' COMMENT 'DRAFT草稿/APPLYING审批中/APPROVED已批准/REFUNDING退税中/REFUNDED已到账/REJECTED驳回',
  `apply_by` bigint DEFAULT NULL COMMENT '申请用户',
  `apply_time` datetime DEFAULT NULL COMMENT '申请时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_type_period` (`tenant_id`,`refund_type`,`period`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='税务退税三场景(出口免抵退/留抵退税/即征即退)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tax_refund`
--

LOCK TABLES `tax_refund` WRITE;
/*!40000 ALTER TABLE `tax_refund` DISABLE KEYS */;
/*!40000 ALTER TABLE `tax_refund` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:30:36
