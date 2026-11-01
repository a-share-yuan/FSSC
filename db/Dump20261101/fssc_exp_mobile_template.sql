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
-- Table structure for table `exp_mobile_template`
--

DROP TABLE IF EXISTS `exp_mobile_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exp_mobile_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `bill_type` varchar(20) NOT NULL COMMENT '单据主类型：APPLY事前申请/CLAIM费用报销/LOAN借款/REPAY还款/PAY对公付款（与 exp_bill.bill_type 同码，不另造枚举）',
  `bill_sub_type` varchar(20) NOT NULL DEFAULT '' COMMENT '单据子类型（与 exp_bill.bill_sub_type 同码）：空串＝该主型不带子型。主库该列可空，本表取空串口径，避免同一含义出现 NULL 与空串两种写法',
  `mobile_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '移动端是否可提报：1可提 0不可提。0 的行【照样下发】，供前端"以下类型请在电脑端提报"明示区使用，不是隐藏开关',
  `sort` int NOT NULL DEFAULT '0' COMMENT '同 mobile_enabled 档内的展示顺序（升序）；seed 与改造前前端常量 H5_DOC_TYPES 的排列同序',
  `template_json` text COMMENT '【预留列·当前零消费者】字段级移动模版（表头字段可见性/必填性）。本期无写入方、无读取方，seed 一律 NULL；接上前不要填值、不要按它做分支',
  `remark` varchar(200) DEFAULT NULL COMMENT '人读说明：该型在/不在移动清单的理由。本表刻意无 label 列——类型中文名仍由前端常量映射，避免同一文案两处定义',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_emt_tenant_enabled_sort` (`tenant_id`,`mobile_enabled`,`sort`,`del_flag`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端单据启用清单（H5 提报入口可配面；template_json 为预留列，当前零消费者）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exp_mobile_template`
--

LOCK TABLES `exp_mobile_template` WRITE;
/*!40000 ALTER TABLE `exp_mobile_template` DISABLE KEYS */;
INSERT INTO `exp_mobile_template` VALUES (1,1,'CLAIM','',1,1,NULL,'移动端已启用：表头/费用明细/附件/发票四腿在 H5 已齐，能交出一张不缺件的单',NULL,'2026-10-04 02:09:18',NULL,'2026-10-04 02:09:18',0),(2,1,'CLAIM','TRAVEL',0,2,NULL,'差旅报销：移动端缺差旅明细段（exp_travel 腿），提交即被打回 ⇒ 引导电脑端 /expense/claim-travel',NULL,'2026-10-04 02:09:18',NULL,'2026-10-04 02:09:18',0),(3,1,'APPLY','',0,3,NULL,'事前申请：移动端缺事由与关联预算腿 ⇒ 引导电脑端 /expense/apply',NULL,'2026-10-04 02:09:18',NULL,'2026-10-04 02:09:18',0),(4,1,'LOAN','',0,4,NULL,'借款：移动端缺冲销与还款计划口径 ⇒ 引导电脑端 /expense/loan',NULL,'2026-10-04 02:09:18',NULL,'2026-10-04 02:09:18',0),(5,1,'PAY','',0,5,NULL,'对公付款：改造前前端常量未含该型（库内实测 14 行、体量居首），收款方与付款通道明细段不在移动端 ⇒ 电脑端提报',NULL,'2026-10-04 02:09:18',NULL,'2026-10-04 02:09:18',0),(6,1,'REPAY','',0,6,NULL,'还款：改造前前端常量未含该型（库内实测 1 行），还款计划与冲抵口径不在移动端 ⇒ 电脑端提报',NULL,'2026-10-04 02:09:18',NULL,'2026-10-04 02:09:18',0);
/*!40000 ALTER TABLE `exp_mobile_template` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:06:16
