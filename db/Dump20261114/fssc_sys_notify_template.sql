CREATE DATABASE  IF NOT EXISTS `fssc` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `fssc`;
-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: 192.168.20.77    Database: fssc
-- ------------------------------------------------------
-- Server version	8.0.46-0ubuntu0.22.04.5

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
-- Table structure for table `sys_notify_template`
--

DROP TABLE IF EXISTS `sys_notify_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notify_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户',
  `trigger_code` varchar(64) NOT NULL COMMENT '触发点编码，如 EXP_BILL_REJECTED',
  `trigger_name` varchar(128) NOT NULL COMMENT '触发点名称（人读）',
  `msg_type` varchar(32) NOT NULL DEFAULT 'NOTICE' COMMENT '消息类型：NOTICE/APPROVAL/ALERT',
  `title_tpl` varchar(200) NOT NULL COMMENT '标题模板，支持 ${billNo} 等占位符',
  `content_tpl` varchar(1000) NOT NULL COMMENT '正文模板',
  `link_tpl` varchar(255) DEFAULT NULL COMMENT '跳转链接模板，可空（空＝不可点击）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '1=启用 0=停用（停用即不通知）',
  `is_builtin` tinyint NOT NULL DEFAULT '0' COMMENT '1=内置模板（升级不覆盖，仅提示）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注/变量说明',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_trigger` (`tenant_id`,`trigger_code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息模板（触发点 → 标题/正文/深链）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_notify_template`
--

LOCK TABLES `sys_notify_template` WRITE;
/*!40000 ALTER TABLE `sys_notify_template` DISABLE KEYS */;
INSERT INTO `sys_notify_template` VALUES (1,1,'EXP_BILL_REJECTED','报销单被驳回','ALERT','单据 ${billNo} 被驳回，请处理后重新提交','您的《${title}》（${billNo}）在审批中被驳回（${rejectType}）。驳回意见：${comment}。修改后可在「我的单据」直接编辑重提。','/expense/my?billNo=${billNo}',1,1,'变量：${billNo} 单号 / ${title} 事由 / ${billType} 单据类型 / ${rejectType} 驳回类型 / ${comment} 驳回意见 / ${applyUser} 申请人',NULL,'2026-11-11 22:59:40',NULL,'2026-11-11 22:59:40',0),(2,1,'EXP_QUALITY_BACK','报销单质检退回整改','ALERT','单据 ${billNo} 质检退回，请整改后重提','您的《${title}》（${billNo}）在质检中被退回整改，得分 ${score}。退回原因：${comment}。整改后可在「我的单据」重新提交。','/expense/my?billNo=${billNo}',1,1,'变量：${billNo} 单号 / ${title} 事由 / ${billType} 单据类型 / ${score} 质检得分 / ${comment} 退回原因 / ${applyUser} 申请人',NULL,'2026-11-12 10:15:32',NULL,'2026-11-12 10:15:32',0);
/*!40000 ALTER TABLE `sys_notify_template` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:29:11
