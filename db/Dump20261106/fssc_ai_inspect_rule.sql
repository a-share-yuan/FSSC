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
-- Table structure for table `ai_inspect_rule`
--

DROP TABLE IF EXISTS `ai_inspect_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_inspect_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `rule_name` varchar(200) NOT NULL,
  `action_code` varchar(256) NOT NULL COMMENT '鐩?綍鍐呭彧璇讳綆椋庨櫓鍔ㄤ綔',
  `args_template` varchar(1000) DEFAULT NULL COMMENT 'JSON锛涙敮鎸?{{today}}/{{month}}/{{year}} 鍗犱綅',
  `json_path` varchar(200) DEFAULT NULL,
  `op` varchar(10) DEFAULT NULL COMMENT 'LT/LTE/GT/GTE/EQ/EMPTY/NOT_EMPTY',
  `expected` varchar(64) DEFAULT NULL,
  `on_breach` varchar(32) NOT NULL DEFAULT 'QUEUE' COMMENT '鏈?樁娈典粎 QUEUE=杩涗汉宸ョ‘璁ら槦鍒',
  `interval_minutes` int NOT NULL DEFAULT '60',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `last_run_at` datetime DEFAULT NULL,
  `last_result` varchar(16) DEFAULT NULL COMMENT 'OK/BREACHED/ERROR',
  `last_detail` varchar(500) DEFAULT NULL,
  `created_by` varchar(64) NOT NULL COMMENT '浠ュ叾鏉冮檺瑙嗚?鎵ц?锛涘け鏉冨嵆瑙勫垯澶辨晥',
  `instruction_text` varchar(2000) DEFAULT NULL COMMENT '鑷?劧璇?█鍘熸枃锛堝彲缁勭粐瀹¤?锛',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rule_tenant_enabled` (`tenant_id`,`enabled`),
  KEY `idx_rule_next` (`last_run_at`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 甯搁┗宸℃?瑙勫垯锛堝彲缁勭粐锛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_inspect_rule`
--

LOCK TABLES `ai_inspect_rule` WRITE;
/*!40000 ALTER TABLE `ai_inspect_rule` DISABLE KEYS */;
INSERT INTO `ai_inspect_rule` VALUES (1,1,'PROBE-P5 共享任务池观察','controller.Sharing.pool','{\"pageNum\":1,\"pageSize\":1}','$.data.total','GTE','999999','QUEUE',5,0,'2026-09-24 07:09:23','BREACHED','actual=null','Y000273','P5 探针创建，验证后即停用','2026-09-24 06:26:57','2026-09-24 07:09:28'),(2,1,'PROBE-P5 共享任务池观察','controller.Sharing.pool','{\"pageNum\":1,\"pageSize\":1}','$.data.total','GTE','999999','QUEUE',5,0,'2026-09-24 07:14:23','BREACHED','actual=null','Y000273','P5 探针创建，验证后即停用','2026-09-24 07:10:13','2026-09-24 07:14:24');
/*!40000 ALTER TABLE `ai_inspect_rule` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:20
