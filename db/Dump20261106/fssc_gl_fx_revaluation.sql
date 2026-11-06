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
-- Table structure for table `gl_fx_revaluation`
--

DROP TABLE IF EXISTS `gl_fx_revaluation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gl_fx_revaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `period` varchar(6) NOT NULL COMMENT '会计期间 yyyyMM',
  `company_id` bigint NOT NULL COMMENT '公司ID（费用承担单位；应收取租户首个公司兜底）',
  `from_currency` varchar(10) NOT NULL COMMENT '外币币种',
  `item_type` varchar(20) NOT NULL COMMENT '应收应付方向:AP外币应付/AR外币应收/LOAN员工外币借款',
  `book_count` int DEFAULT NULL COMMENT '纳入重估的单据/明细条数',
  `book_local` decimal(20,2) DEFAULT NULL COMMENT '账面本位币（按入账汇率折算的期末余额）',
  `reval_local` decimal(20,2) DEFAULT NULL COMMENT '重估本位币（原币余额×期末汇率）',
  `diff` decimal(20,2) DEFAULT NULL COMMENT '汇兑差异 = 重估本位币 − 账面本位币（正/负=损失/收益方向依项目性质）',
  `rate` decimal(20,10) DEFAULT NULL COMMENT '期末汇率（外币→CNY；缺失时为空=SKIPPED）',
  `voucher_id` bigint DEFAULT NULL COMMENT '生成的重估凭证 op_voucher.id（DRAFT；SKIPPED 为空）',
  `status` varchar(20) NOT NULL COMMENT 'GENERATED已生成凭证/SKIPPED未生成',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_period_cur` (`tenant_id`,`period`,`company_id`,`from_currency`,`item_type`),
  KEY `idx_fxreval_period` (`tenant_id`,`period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='月末外币汇兑重估流水（CAS 19）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gl_fx_revaluation`
--

LOCK TABLES `gl_fx_revaluation` WRITE;
/*!40000 ALTER TABLE `gl_fx_revaluation` DISABLE KEYS */;
/*!40000 ALTER TABLE `gl_fx_revaluation` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:28
