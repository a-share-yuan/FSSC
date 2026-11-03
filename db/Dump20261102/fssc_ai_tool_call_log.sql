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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 工具调用日志：无 del_flag，Mapper 仅 insert，不可删（SOX 友好）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_tool_call_log`
--

LOCK TABLES `ai_tool_call_log` WRITE;
/*!40000 ALTER TABLE `ai_tool_call_log` DISABLE KEYS */;
INSERT INTO `ai_tool_call_log` VALUES (1,1,'f8f43fab8ae04e9f',NULL,NULL,'controller.Budget.itemPage','/api/tenant/budget/item/page','GET','read','LOW','{\"pageNum\":1,\"pageSize\":5,\"schemeId\":101}',NULL,NULL,NULL,0,'无权限访问：动作 controller.Budget.itemPage 标记为 AI 不可调用（§6 红线：删除/审批/反结账/权限变更/发布流程等永不开放）。',1,'admin',NULL,NULL,'127.0.0.1',3,'2026-11-03 07:34:09'),(2,2,'d1c1a47f81874b9b',NULL,NULL,'controller.Budget.itemPage','/api/tenant/budget/item/page','GET','read','LOW','{\"pageNum\":1,\"pageSize\":5,\"schemeId\":101}',NULL,NULL,NULL,0,'无权限访问：动作 controller.Budget.itemPage 标记为 AI 不可调用（§6 红线：删除/审批/反结账/权限变更/发布流程等永不开放）。',5011,'admin2',NULL,NULL,'127.0.0.1',0,'2026-11-03 07:34:13'),(3,1,'b87069ed21874f6b',NULL,NULL,'controller.BudgetOccupy.page','/api/tenant/budget/occupy/page','GET','read','LOW','{\"pageNum\":1,\"pageSize\":10}',200,0,'{\"code\":0,\"data\":{\"list\":[{\"amount\":-100.00,\"bizId\":5,\"bizLineId\":null,\"bizNo\":\"JRN202701001\",\"bizType\":\"GL_ACCRUAL\",\"budgetItemId\":5,\"budgetItemName\":\"行政办公费\",\"createTime\":\"2026-11-03T07:25:56\",\"id\":7,\"operType\":\"RELEASE\",\"operUserId\":3797,\"operUserName\":\"赵勇\",\"operUserNo\":\"100002\",\"remark\":null,\"tenantId\":1},{\"amount\":100.00,\"bizId\":5,\"bizLineId\":null,\"bizNo\":\"JRN202701001\",\"bizType\":\"GL_ACCRUAL\",\"budgetItemId\":5,\"budgetItemName\":\"行政办公费\",\"createTime\":\"2026-11-03T07:25:42\",\"id\":6,\"operType\":\"OCCUPY\",\"operUserId\":1,\"operUserName\":\"系统管理员\",\"operUserNo\":null,\"remark\":null,\"tenantId\":1},{\"amount\":-1000.00,\"bizId\":4,\"bizLineId\":null,\"bizNo\":\"JRN202612002\",\"bizType\":\"GL_ACCRUAL\",\"budgetItemId\":5,\"budgetItemName\":\"行政办公费\",\"createTime\":\"2026-11-03T07:25:30\",\"id\":5,\"operType\":\"RELEASE\",\"operUserId\":1,\"operUserName\":\"系统管理员\",\"operUserNo\":null,\"remark\":null,\"tenantId\":1},{\"amount\":1000.00,\"bizId\":4,\"bizLineId\":null,\"bizNo\":\"JRN202612002\",\"bizType\":\"GL_ACCRUAL\",\"budgetItemId\":5,\"budgetItemName\":\"行政办公费\",\"createTime\":\"2026-11-03T07:24:55\",\"id\":4,\"operType\":\"OCCUPY\",\"operUserId\":1,\"operUserName\":\"系统管理员\",\"operUserNo\":null,\"remark\":null,\"tenantId\":1},{\"amount\":-999999.00,\"bizId\":3,\"bizLineId\":null,\"bizNo\":\"JRN202612002\",\"bizType\":\"GL_DEFER\",\"budgetItemId\":5,\"budgetItemName\":\"行政办公费\",\"createTime\":\"2026-11-03T07:21:14\",\"id\":3,\"operType\":\"RELEASE\",\"operUserId\":3797,\"operUserName\":\"赵勇\",\"operUserNo\":\"100002\",\"remark\":null,\"tenantId\":1},{\"amount\":999999.00,\"bizId\":3,\"bizLineId\":null,\"bizNo\":\"JRN202612002\",\"bizType\":\"GL_DEFER\",\"budgetItemId\":5,\"budgetItemName\":\"行政办公费\",\"createTime\":\"2026-11-03T07:20:47\",\"id\":2,\"operType\":\"OCCUPY\",\"operUserId\":1,\"operUserName\":\"系统管理员\",\"operUserNo\":null,\"remark\":\"超预算软控通过（理由：E2E 软控超支理由）\",\"tenantId\":1}],\"pageNum\":1,\"pageSize\":10,\"pages\":1,\"total\":6},\"msg\":\"成功\",\"success\":true}',1,NULL,1,'admin',NULL,NULL,'127.0.0.1',2452,'2026-11-03 07:34:27'),(4,2,'3ad594a4a62d4322',NULL,NULL,'controller.BudgetOccupy.page','/api/tenant/budget/occupy/page','GET','read','LOW','{\"pageNum\":1,\"pageSize\":10}',200,0,'{\"code\":0,\"data\":{\"list\":[],\"pageNum\":1,\"pageSize\":10,\"pages\":0,\"total\":0},\"msg\":\"成功\",\"success\":true}',1,NULL,5011,'admin2',NULL,NULL,'127.0.0.1',13,'2026-11-03 07:34:30');
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

-- Dump completed on 2026-11-03 10:48:26
