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
-- Table structure for table `rc_receipt`
--

DROP TABLE IF EXISTS `rc_receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rc_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `bill_no` varchar(64) DEFAULT NULL COMMENT '单据号',
  `receivable_ids` varchar(512) DEFAULT NULL COMMENT '核销的多个应收单id(逗号分隔)',
  `customer_id` bigint DEFAULT NULL COMMENT '往来单位',
  `receipt_date` date DEFAULT NULL COMMENT '收款日期',
  `pay_method` varchar(20) DEFAULT NULL COMMENT '收款方式:银行/现金/票据',
  `amount` decimal(20,2) DEFAULT '0.00' COMMENT '收款金额',
  `operator_id` bigint DEFAULT NULL COMMENT '经办人(sys_user.id)',
  `status` varchar(20) DEFAULT 'WRITTEN_OFF' COMMENT 'WRITTEN_OFF已核销',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除0否/1是',
  `bank_serial` varchar(64) DEFAULT NULL COMMENT '勾对的银行流水号(收款方向未达账项判定用)',
  `bank_matched` tinyint NOT NULL DEFAULT '0' COMMENT '是否与银行流水勾对:0未勾对(企业已收银行未收)/1已勾对',
  `bank_matched_amount` decimal(20,2) DEFAULT NULL COMMENT '宸蹭笌閾惰?娴佹按鍕惧?閲戦?锛堥儴鍒嗗嬀瀵规寜閲戦?绱??锛岄潪甯冨皵锛',
  PRIMARY KEY (`id`),
  KEY `idx_rc_receipt_tenant` (`tenant_id`),
  KEY `idx_tenant_receipt_date` (`tenant_id`,`receipt_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收款核销单(OTC-P0-6)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rc_receipt`
--

LOCK TABLES `rc_receipt` WRITE;
/*!40000 ALTER TABLE `rc_receipt` DISABLE KEYS */;
/*!40000 ALTER TABLE `rc_receipt` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:30
