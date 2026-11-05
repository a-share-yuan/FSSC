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
-- Table structure for table `md_violation_reason`
--

DROP TABLE IF EXISTS `md_violation_reason`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_violation_reason` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `reason_code` varchar(32) NOT NULL COMMENT '原因码（唯一，如 VR_TRUTH）',
  `reason_name` varchar(64) NOT NULL COMMENT '原因名称（展示用）',
  `reason_category` varchar(32) DEFAULT NULL COMMENT '分类：TRUTH真实性/SPLIT拆分/DOC票据与业务/FORM填报规范',
  `deduct_points` int NOT NULL DEFAULT '0' COMMENT '扣分（正数，扣多少分）',
  `severity` varchar(16) DEFAULT 'LOW' COMMENT '严重度：LOW/MEDIUM/HIGH',
  `sort` int DEFAULT '0',
  `status` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/DISABLED',
  `remark` varchar(500) DEFAULT NULL COMMENT '说明（写清判定边界，避免被当成"发票问题的扣分口"）',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_violation_reason_code` (`tenant_id`,`reason_code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='违规原因字典（仅行为类；发票真伪/重复/作废由预检拦截，不进信用扣分）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_violation_reason`
--

LOCK TABLES `md_violation_reason` WRITE;
/*!40000 ALTER TABLE `md_violation_reason` DISABLE KEYS */;
INSERT INTO `md_violation_reason` VALUES (1,1,'VR_OVERDUE','超期报销','FORM',5,'LOW',10,'NORMAL','超过制度规定的报销时限。数电票可长期留存，滞后报销更常见，但属流程纪律问题',NULL,'2026-11-05 22:01:09',NULL,'2026-11-05 22:01:09',0),(2,1,'VR_ACCOUNT','费用归属错误','FORM',5,'MEDIUM',20,'NORMAL','部门/项目/费用项错配，导致预算与核算口径失真（非恶意，可整改）',NULL,'2026-11-05 22:01:09',NULL,'2026-11-05 22:01:09',0),(3,1,'VR_ATTACH','非发票类附件缺失','DOC',5,'MEDIUM',30,'NORMAL','发票已随票夹/OCR 进系统，缺的是行程单、审批单、情况说明等业务佐证材料',NULL,'2026-11-05 22:01:09',NULL,'2026-11-05 22:01:09',0),(4,1,'VR_INV_MISMATCH','票面与业务不符','DOC',10,'MEDIUM',40,'NORMAL','发票开票内容与实际业务事项不一致（如办公用品票报餐饮）。真伪/重复由系统拦截，这一类只能人工判',NULL,'2026-11-05 22:01:09',NULL,'2026-11-05 22:01:09',0),(5,1,'VR_SPLIT','拆分报销规避审批','SPLIT',20,'HIGH',50,'NORMAL','同一事项拆成多单绕过审批权限或预算控制。数电票可任意拆分，这是电子发票时代的高发风险',NULL,'2026-11-05 22:01:09',NULL,'2026-11-05 22:01:09',0),(6,1,'VR_TRUTH','虚假业务/虚构事项','TRUTH',30,'HIGH',60,'NORMAL','无真实业务发生、伪造事项或虚增金额。最重档，直接降级并触发质检必检',NULL,'2026-11-05 22:01:09',NULL,'2026-11-05 22:01:09',0);
/*!40000 ALTER TABLE `md_violation_reason` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-05 22:04:35
