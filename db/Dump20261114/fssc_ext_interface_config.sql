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
-- Table structure for table `ext_interface_config`
--

DROP TABLE IF EXISTS `ext_interface_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ext_interface_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `category` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '接口分类: INVOICE_VERIFY发票验真/INVOICE_CERTIFY发票认证/OCR影像识别/AI_MODEL AI模型',
  `interface_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '接口名称（如：百度发票验真）',
  `interface_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '接口编码（租户内唯一）',
  `provider` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务商: BAIDU/TENCENT/ALIYUN/OPENAI/QIANFAN/CUSTOM',
  `api_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接口请求地址',
  `api_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API Key / AppKey',
  `api_secret` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API Secret / SecretKey',
  `model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型名称（AI模型专用，如 ERNIE-4.0-Turbo-8K）',
  `extra_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '附加配置JSON（如字段映射、超时、重试策略等）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DISABLED' COMMENT 'ENABLED启用/DISABLED停用',
  `is_default` int NOT NULL DEFAULT '0' COMMENT '1=该分类下默认接口（同分类仅一个）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `last_test_time` datetime DEFAULT NULL COMMENT '最后一次连接测试时间',
  `last_test_result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后一次测试结果: SUCCESS/FAIL',
  `last_test_msg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后一次测试消息',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ext_iface_code` (`tenant_id`,`interface_code`,`del_flag`),
  KEY `idx_ext_iface_category` (`tenant_id`,`category`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部接口统一配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ext_interface_config`
--

LOCK TABLES `ext_interface_config` WRITE;
/*!40000 ALTER TABLE `ext_interface_config` DISABLE KEYS */;
INSERT INTO `ext_interface_config` VALUES (1,1,'OCR','OCR影像识别通道','DEFAULT_OCR','CUSTOM',NULL,NULL,NULL,NULL,'{\"channel\":\"mock\"}','DISABLED',1,'mock 桩通道：不接真实 OCR，仅供本地联调。OCR 分类默认启用通道为 Baidu OCR(id=9)','2026-10-17 06:33:40','FAIL','未配置 API 地址',NULL,'2026-09-05 12:31:05','admin','2026-10-17 06:33:40',0),(2,1,'INVOICE_VERIFY','发票验真接口','DEFAULT_INVOICE_VERIFY','CUSTOM',NULL,'test-key',NULL,NULL,'{}','DISABLED',1,'待接入：需客户提供真实发票验真服务地址与凭据。原配置误填为本服务管理端点（http://localhost:8089/api/tenant/system/ext-interface/2），GET 自身必然返回鉴权响应，会被误判为已连通，故置空。','2026-11-06 09:38:22','FAIL','未配置 API 地址',NULL,'2026-09-05 12:31:05','admin','2026-11-06 09:38:22',0),(3,1,'INVOICE_CERTIFY','发票认证勾选接口','DEFAULT_INVOICE_CERTIFY','CUSTOM',NULL,NULL,NULL,NULL,'{}','DISABLED',1,'发票进项认证勾选接口，启用后认证操作将调用此接口',NULL,NULL,NULL,NULL,'2026-09-05 12:31:05',NULL,'2026-09-05 12:31:05',0),(4,1,'AI_MODEL','AI大模型接口','DEFAULT_AI_MODEL','BAILIAN','https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions','sk-sp-H.DYLXXI.StP3.MEUCIHj2O7obZCG1S_Rh1W1Ac8tLtcuy6A2vMVTWkQMQxntbAiEApfOal-8PVCqvOJVP8QwH5AWQp72Ht0LQ8Jc4qzlfD2w',NULL,'qwen3.8-flash','{\"timeout\":25}','ENABLED',0,'Token Plan 套餐 key（2026-09-24 由用户提供并实测通过）','2026-10-26 09:04:12','FAIL','HTTP 403 ({\"error\":{\"message\":\"Access to model denied. Please make sure you are eligible for using the model.\",\"id\":\"9e8bee12-47e2-48a6-acd5-f446d1ee64f4\",\"type\":\"AccessDenied.Unpurchased\",\"code\":\"AccessDenied.)',NULL,'2026-09-05 12:31:05','admin','2026-10-26 09:34:09',0),(9,1,'OCR','Baidu OCR','BAIDU_OCR_01','BAIDU','https://aip.baidubce.com/rest/2.0/ocr/v1/vat_invoice','baidu-key',NULL,NULL,'{\"channel\":\"http\"}','ENABLED',0,'Baidu VAT Invoice OCR','2026-10-17 06:33:40','FAIL','HTTP 200 ({\"error_code\":23,\"error_msg\":\"IAM API-KEY authentication failed\"}) | 业务错误: error_code=23 IAM API-KEY authentication failed',NULL,'2026-09-05 12:41:16','admin','2026-10-17 06:33:40',0),(10,1,'OCR','Test OCR','TEST_OCR_02','CUSTOM','https://example.com/ocr',NULL,NULL,NULL,NULL,'DISABLED',0,'待接入：占位地址 example.com，需替换为真实 OCR 服务端点',NULL,NULL,NULL,NULL,'2026-09-05 12:45:23',NULL,'2026-10-17 06:28:53',1),(11,1,'LINKU','LinkU Platform','LINKU_DEFAULT','LINKU','https://linku.example.com/api','','',NULL,'','DISABLED',1,'待接入：占位地址 linku.example.com，需替换为真实 LinkU 平台地址',NULL,NULL,NULL,NULL,'2026-09-05 14:06:12',NULL,'2026-10-17 06:28:53',0),(12,1,'BANK','银企直联（通用HTTP）','BANK_HTTP','GENERIC','',NULL,NULL,NULL,NULL,'DISABLED',1,'待接入：通用 HTTP 银企直联，需填入银行网关地址与鉴权凭据',NULL,NULL,NULL,NULL,'2026-09-16 01:25:15',NULL,'2026-10-17 06:28:53',0),(13,1,'SMS','短信通知（阿里云）','SMS_ALIYUN','aliyun','',NULL,NULL,NULL,NULL,'DISABLED',1,'待接入：需填入阿里云短信 AccessKey/Secret 与签名',NULL,NULL,NULL,NULL,'2026-09-16 01:25:15',NULL,'2026-10-17 06:28:53',0),(14,1,'SMS','短信通知（腾讯云）','SMS_TENCENT','tencent','',NULL,NULL,NULL,NULL,'DISABLED',1,'待接入：需填入腾讯云短信 SecretId/SecretKey 与签名',NULL,NULL,NULL,NULL,'2026-09-16 01:25:15',NULL,'2026-10-17 06:28:53',0),(15,1,'TAX_DECLARE','纳税申报数据推送（电子税务局）','TAX_DECLARE_PUSH','GENERIC','',NULL,NULL,NULL,NULL,'DISABLED',1,'待接入：需对接电子税务局申报推送地址与证书',NULL,NULL,NULL,NULL,'2026-09-16 01:25:15',NULL,'2026-10-17 06:28:53',0),(16,1,'TAX_CERTIFY','进项发票认证（勾选/撤销）','TAX_CERTIFY','GENERIC','',NULL,NULL,NULL,NULL,'DISABLED',1,'待接入：需对接勾选/撤销认证平台地址与凭据',NULL,NULL,NULL,NULL,'2026-09-16 01:25:15',NULL,'2026-10-17 06:28:53',0),(17,1,'BANK','银企直联（配置驱动形态样例）','BANK_TPL','TEMPLATE','https://bank.example.com/api/pay/batch',NULL,NULL,NULL,'{\"header\":{\"batchNo\":\"batchNo\",\"count\":\"cnt\",\"total\":\"sumAmt\"},\n         \"item\":{\"payeeName\":\"payeName\",\"bankName\":\"bankName\",\n                 \"account\":\"acctNo\",\"amount\":\"amt\",\"postscript\":\"remark\"},\n         \"items\":\"detailList\",\"serialList\":\"serialNos\"}','DISABLED',0,'待接入：占位地址 bank.example.com，需替换为真实银行直联地址',NULL,NULL,NULL,'system','2026-10-14 16:45:04','system','2026-10-17 06:28:53',0),(18,1,'AI_MODEL','DeepSeek大模型','DEEPSEEK_AI_MODEL','DEEPSEEK','https://api.deepseek.com/chat/completions','sk-fbb3d40e99b644b488b9127b298318c9',NULL,'deepseek-chat','{\"timeout\":30}','ENABLED',1,'P0 AI 能力完善：主模型通道',NULL,NULL,NULL,NULL,'2026-10-26 09:34:09',NULL,'2026-10-26 09:34:09',0);
/*!40000 ALTER TABLE `ext_interface_config` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:28:12
