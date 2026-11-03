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
-- Table structure for table `sys_login_log`
--

DROP TABLE IF EXISTS `sys_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `user_name` varchar(64) DEFAULT NULL COMMENT '登录账号',
  `ipaddr` varchar(64) DEFAULT NULL COMMENT '登录IP',
  `login_location` varchar(200) DEFAULT NULL COMMENT 'IP归属地',
  `browser` varchar(100) DEFAULT NULL COMMENT '浏览器',
  `os` varchar(100) DEFAULT NULL COMMENT '操作系统',
  `device_type` varchar(20) DEFAULT 'PC' COMMENT '设备类型:PC/H5/APP',
  `login_type` varchar(20) DEFAULT 'LOGIN' COMMENT '类型:LOGIN登录/LOGOUT登出',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0成功 1失败',
  `msg` varchar(500) DEFAULT NULL COMMENT '提示消息',
  `login_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_time` (`tenant_id`,`login_time`),
  KEY `idx_tenant_user` (`tenant_id`,`user_name`)
) ENGINE=InnoDB AUTO_INCREMENT=215 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户登录日志[审计表-不可删]';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_login_log`
--

LOCK TABLES `sys_login_log` WRITE;
/*!40000 ALTER TABLE `sys_login_log` DISABLE KEYS */;
INSERT INTO `sys_login_log` VALUES (183,1,'admin','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:09:26'),(184,1,'admin','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:17:45'),(185,1,'admin','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:17:50'),(186,1,'100032','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:17:50'),(187,1,'100065','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:17:50'),(188,1,'100029','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:17:50'),(189,1,'100033','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:17:50'),(190,1,'100044','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:17:50'),(191,1,'admin','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:18:00'),(192,1,'admin','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:20:47'),(193,1,'admin','127.0.0.1',NULL,'Chrome','Windows 10','PC','LOGIN',0,'登录成功','2026-11-03 10:21:14'),(194,1,'100029','127.0.0.1',NULL,'Chrome','Windows 10','PC','LOGIN',0,'登录成功','2026-11-03 10:21:33'),(195,1,'100065','127.0.0.1',NULL,'Chrome','Windows 10','PC','LOGIN',0,'登录成功','2026-11-03 10:21:44'),(196,1,'100033','127.0.0.1',NULL,'Chrome','Windows 10','PC','LOGIN',0,'登录成功','2026-11-03 10:21:56'),(197,1,'100044','127.0.0.1',NULL,'Chrome','Windows 10','PC','LOGIN',0,'登录成功','2026-11-03 10:22:07'),(198,1,'100032','127.0.0.1',NULL,'Chrome','Windows 10','PC','LOGIN',0,'登录成功','2026-11-03 10:22:18'),(199,1,'100044','127.0.0.1',NULL,'Chrome','Windows 10','PC','LOGIN',0,'登录成功','2026-11-03 10:22:33'),(200,1,'admin','127.0.0.1',NULL,'Chrome','Windows 10','PC','LOGIN',0,'登录成功','2026-11-03 10:23:01'),(201,1,'admin','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:27:23'),(202,1,'100032','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:27:23'),(203,1,'100065','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:27:23'),(204,1,'100029','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:27:23'),(205,1,'100033','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:27:24'),(206,1,'100044','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:27:24'),(207,1,'admin','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:32:17'),(208,1,'100032','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:32:17'),(209,1,'100065','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:32:17'),(210,1,'100029','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:32:17'),(211,1,'100033','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:32:17'),(212,1,'100044','127.0.0.1',NULL,'Other','Other','PC','LOGIN',0,'登录成功','2026-11-03 10:32:17'),(213,1,'admin','127.0.0.1',NULL,'Chrome','Windows 10','PC','LOGIN',0,'登录成功','2026-11-03 10:33:24'),(214,1,'admin','127.0.0.1',NULL,'Chrome','Windows 10','PC','LOGIN',0,'登录成功','2026-11-03 10:40:03');
/*!40000 ALTER TABLE `sys_login_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:47:59
