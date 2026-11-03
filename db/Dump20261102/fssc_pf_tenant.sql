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
-- Table structure for table `pf_tenant`
--

DROP TABLE IF EXISTS `pf_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pf_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '租户ID',
  `tenant_code` varchar(64) NOT NULL COMMENT '租户编码(登录时识别企业)',
  `tenant_name` varchar(200) NOT NULL COMMENT '租户企业全称',
  `short_name` varchar(100) DEFAULT NULL COMMENT '简称/展示名',
  `credit_code` varchar(64) DEFAULT NULL COMMENT '统一社会信用代码',
  `industry` varchar(50) DEFAULT NULL COMMENT '所属行业',
  `scale` varchar(20) DEFAULT NULL COMMENT '企业规模',
  `contact_name` varchar(50) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(30) DEFAULT NULL COMMENT '联系电话',
  `contact_email` varchar(100) DEFAULT NULL COMMENT '联系邮箱',
  `domain` varchar(200) DEFAULT NULL COMMENT '独立访问域名',
  `logo` varchar(500) DEFAULT NULL COMMENT '企业Logo地址',
  `sys_title` varchar(100) DEFAULT NULL COMMENT '系统标题(租户个性化)',
  `status` varchar(20) NOT NULL DEFAULT 'APPLYING' COMMENT '状态:APPLYING待审核/NORMAL正常/FROZEN冻结/CANCELLING注销中/CANCELLED已注销/REJECTED已拒绝',
  `freeze_reason` varchar(500) DEFAULT NULL COMMENT '冻结原因',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '拒绝原因',
  `effect_time` datetime DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime DEFAULT NULL COMMENT '到期时间(过期自动冻结)',
  `admin_user_id` bigint DEFAULT NULL COMMENT '租户超管用户ID(指向sys_user)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint NOT NULL DEFAULT '0' COMMENT '0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`),
  KEY `idx_status` (`status`),
  KEY `idx_expire` (`expire_time`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户主表[平台表-无tenant_id]';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pf_tenant`
--

LOCK TABLES `pf_tenant` WRITE;
/*!40000 ALTER TABLE `pf_tenant` DISABLE KEYS */;
INSERT INTO `pf_tenant` VALUES (1,'DEMO1','爱共享公司','爱共享',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'NORMAL',NULL,NULL,'2026-08-11 14:51:53','2027-08-11 14:51:53',NULL,NULL,NULL,'2026-08-11 14:51:53',NULL,'2026-10-15 14:02:42',0);
/*!40000 ALTER TABLE `pf_tenant` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:25
