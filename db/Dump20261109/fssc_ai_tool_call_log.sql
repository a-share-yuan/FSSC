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
-- Table structure for table `ai_tool_call_log`
--

DROP TABLE IF EXISTS `ai_tool_call_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_tool_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '由 MetaObjectHandler 从租户上下文填充',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '与 X-Trace-Id / sys_oper_log.trace_id 同值',
  `instruction_text` varchar(2000) DEFAULT NULL COMMENT '用户原始指令（审计第一现场）',
  `model` varchar(128) DEFAULT NULL COMMENT '客户端自报模型标识，不作信任依据',
  `action_code` varchar(256) DEFAULT NULL COMMENT '目录动作码',
  `action_url` varchar(512) DEFAULT NULL,
  `action_method` varchar(10) DEFAULT NULL,
  `action_kind` varchar(20) DEFAULT NULL,
  `risk_level` varchar(10) DEFAULT NULL,
  `args` varchar(2000) DEFAULT NULL COMMENT '脱敏后请求参数',
  `http_status` int DEFAULT NULL,
  `biz_code` int DEFAULT NULL COMMENT 'Result.code，区分传输成功但业务拒绝',
  `biz_result` varchar(2000) DEFAULT NULL,
  `status` tinyint DEFAULT NULL COMMENT '1 成功 0 失败',
  `error_msg` varchar(2000) DEFAULT NULL,
  `operator_user_id` bigint DEFAULT NULL COMMENT '下令真人（AI 不拥有身份）',
  `operator_username` varchar(64) DEFAULT NULL,
  `confirm_id` bigint DEFAULT NULL COMMENT '高风险写绑定的确认票据',
  `idempotency_key` varchar(128) DEFAULT NULL,
  `client_ip` varchar(64) DEFAULT NULL,
  `cost_ms` bigint DEFAULT NULL,
  `call_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_trace` (`trace_id`),
  KEY `idx_ai_tenant_time` (`tenant_id`,`call_time`),
  KEY `idx_ai_action` (`action_code`(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 工具调用日志：无 del_flag，Mapper 仅 insert，不可删（SOX 友好）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_tool_call_log`
--

LOCK TABLES `ai_tool_call_log` WRITE;
/*!40000 ALTER TABLE `ai_tool_call_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_tool_call_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:24:26
