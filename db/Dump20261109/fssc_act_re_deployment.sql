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
-- Table structure for table `act_re_deployment`
--

DROP TABLE IF EXISTS `act_re_deployment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_re_deployment` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `NAME_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CATEGORY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `KEY_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TENANT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT '',
  `DEPLOY_TIME_` timestamp(3) NULL DEFAULT NULL,
  `DERIVED_FROM_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DERIVED_FROM_ROOT_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PARENT_DEPLOYMENT_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ENGINE_VERSION_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_re_deployment`
--

LOCK TABLES `act_re_deployment` WRITE;
/*!40000 ALTER TABLE `act_re_deployment` DISABLE KEYS */;
INSERT INTO `act_re_deployment` VALUES ('0534a497-b69e-11f1-8056-d8bbc1e55a1a','会计凭证借阅审批流程',NULL,NULL,'1','2026-09-22 15:55:23.362',NULL,NULL,'0534a497-b69e-11f1-8056-d8bbc1e55a1a',NULL),('1632e20a-b761-11f1-890e-d8bbc1e55a1a','会计凭证销毁审批流程',NULL,NULL,'1','2026-09-23 15:11:43.734',NULL,NULL,'1632e20a-b761-11f1-890e-d8bbc1e55a1a',NULL),('26589173-c855-11f1-a6c4-d8bbc1e55a1a','员工费用报销审批流程（差旅/招待/金额档）',NULL,NULL,'1','2026-10-15 04:59:06.630',NULL,NULL,'26589173-c855-11f1-a6c4-d8bbc1e55a1a',NULL),('26dd9ff7-c855-11f1-a6c4-d8bbc1e55a1a','借款与还款审批流程',NULL,NULL,'1','2026-10-15 04:59:07.503',NULL,NULL,'26dd9ff7-c855-11f1-a6c4-d8bbc1e55a1a',NULL),('275c45db-c855-11f1-a6c4-d8bbc1e55a1a','开票与收入确认审批流程（含服务申请档）',NULL,NULL,'1','2026-10-15 04:59:08.333',NULL,NULL,'275c45db-c855-11f1-a6c4-d8bbc1e55a1a',NULL),('3180ff63-bb63-11f1-bc06-d8bbc1e55a1a','资产新增审批流程（含在建转固档）',NULL,NULL,'1','2026-09-28 17:36:53.189',NULL,NULL,'3180ff63-bb63-11f1-bc06-d8bbc1e55a1a',NULL),('31c1b1e7-bb63-11f1-bc06-d8bbc1e55a1a','资产调拨审批流程（同公司/跨法人档）',NULL,NULL,'1','2026-09-28 17:36:53.613',NULL,NULL,'31c1b1e7-bb63-11f1-bc06-d8bbc1e55a1a',NULL),('31ea217b-bb63-11f1-bc06-d8bbc1e55a1a','资产处置审批流程（报废/对外销售档）',NULL,NULL,'1','2026-09-28 17:36:53.878',NULL,NULL,'31ea217b-bb63-11f1-bc06-d8bbc1e55a1a',NULL),('322aacef-bb63-11f1-bc06-d8bbc1e55a1a','资产价值调整审批流程（原值/减值/跌价档）',NULL,NULL,'1','2026-09-28 17:36:54.300',NULL,NULL,'322aacef-bb63-11f1-bc06-d8bbc1e55a1a',NULL),('38bd875a-bb62-11f1-8f3d-d8bbc1e55a1a','折旧摊销（作业）',NULL,NULL,'1','2026-09-28 17:29:55.832',NULL,NULL,'38bd875a-bb62-11f1-8f3d-d8bbc1e55a1a',NULL),('3915e08e-bb62-11f1-8f3d-d8bbc1e55a1a','资产/存货盘点差异（作业）',NULL,NULL,'1','2026-09-28 17:29:56.412',NULL,NULL,'3915e08e-bb62-11f1-8f3d-d8bbc1e55a1a',NULL),('39393ee0-bb65-11f1-bc06-d8bbc1e55a1a','应收对账（作业）',NULL,NULL,'1','2026-09-28 17:51:25.134',NULL,NULL,'39393ee0-bb65-11f1-bc06-d8bbc1e55a1a',NULL),('394220b2-bb62-11f1-8f3d-d8bbc1e55a1a','存货领用出库（作业）',NULL,NULL,'1','2026-09-28 17:29:56.701',NULL,NULL,'394220b2-bb62-11f1-8f3d-d8bbc1e55a1a',NULL),('395fb2a4-bb65-11f1-bc06-d8bbc1e55a1a','应收催收（作业）',NULL,NULL,'1','2026-09-28 17:51:25.385',NULL,NULL,'395fb2a4-bb65-11f1-bc06-d8bbc1e55a1a',NULL),('3971e346-bb62-11f1-8f3d-d8bbc1e55a1a','存货处置审批流程（申请→确认→执行）',NULL,NULL,'1','2026-09-28 17:29:57.015',NULL,NULL,'3971e346-bb62-11f1-8f3d-d8bbc1e55a1a',NULL),('39831928-bb65-11f1-bc06-d8bbc1e55a1a','资金计划审批流程（编制/调整档）',NULL,NULL,'1','2026-09-28 17:51:25.618',NULL,NULL,'39831928-bb65-11f1-bc06-d8bbc1e55a1a',NULL),('39b6100c-bb65-11f1-bc06-d8bbc1e55a1a','银行对账（作业）',NULL,NULL,'1','2026-09-28 17:51:25.952',NULL,NULL,'39b6100c-bb65-11f1-bc06-d8bbc1e55a1a',NULL),('39dd9540-bb65-11f1-bc06-d8bbc1e55a1a','收款确认（作业）',NULL,NULL,'1','2026-09-28 17:51:26.210',NULL,NULL,'39dd9540-bb65-11f1-bc06-d8bbc1e55a1a',NULL),('3eabef21-c850-11f1-9186-d8bbc1e55a1a','总账凭证审批流程（做账）',NULL,NULL,'1','2026-10-15 04:23:59.959',NULL,NULL,'3eabef21-c850-11f1-9186-d8bbc1e55a1a',NULL),('4bd0e1cf-bb64-11f1-bc06-d8bbc1e55a1a','发票校验复审（作业）',NULL,NULL,'1','2026-09-28 17:44:46.830',NULL,NULL,'4bd0e1cf-bb64-11f1-bc06-d8bbc1e55a1a',NULL),('4c987793-bb64-11f1-bc06-d8bbc1e55a1a','供应商对账（作业）',NULL,NULL,'1','2026-09-28 17:44:48.138',NULL,NULL,'4c987793-bb64-11f1-bc06-d8bbc1e55a1a',NULL),('4cb94607-bb64-11f1-bc06-d8bbc1e55a1a','付款计划（作业）',NULL,NULL,'1','2026-09-28 17:44:48.353',NULL,NULL,'4cb94607-bb64-11f1-bc06-d8bbc1e55a1a',NULL),('4e682bf3-b053-11f1-8f08-d8bbc1e55a1a','采购订单审批',NULL,NULL,'1','2026-09-14 15:45:26.942',NULL,NULL,'4e682bf3-b053-11f1-8f08-d8bbc1e55a1a',NULL),('5235e18c-c844-11f1-8e2d-d8bbc1e55a1a','应收账款收款审批流程',NULL,NULL,'1','2026-10-15 02:58:38.779',NULL,NULL,'5235e18c-c844-11f1-8e2d-d8bbc1e55a1a',NULL),('6a57b574-bb65-11f1-bc06-d8bbc1e55a1a','主数据核准流程（五档合一）',NULL,NULL,'1','2026-09-28 09:52:47.541',NULL,NULL,'6a57b574-bb65-11f1-bc06-d8bbc1e55a1a',NULL),('6fd7ca38-bb65-11f1-bc06-d8bbc1e55a1a','凭证归档（作业）',NULL,NULL,'1','2026-09-28 17:52:56.770',NULL,NULL,'6fd7ca38-bb65-11f1-bc06-d8bbc1e55a1a',NULL),('87ce042b-cf43-11f1-94f2-d8bbc1e55a1a','费用报销（非集中）',NULL,NULL,'1','2026-10-24 00:40:37.338',NULL,NULL,'87ce042b-cf43-11f1-94f2-d8bbc1e55a1a',NULL),('915dfbdf-cf43-11f1-94f2-d8bbc1e55a1a','对公付款（非集中）',NULL,NULL,'1','2026-10-24 00:40:53.381',NULL,NULL,'915dfbdf-cf43-11f1-94f2-d8bbc1e55a1a',NULL),('91a9f902-cf43-11f1-94f2-d8bbc1e55a1a','资产新增（非集中）',NULL,NULL,'1','2026-10-24 00:40:53.879',NULL,NULL,'91a9f902-cf43-11f1-94f2-d8bbc1e55a1a',NULL),('91fa3fb3-cde4-11f1-bd9e-d8bbc1e55a1a','付款执行审批流程（网银/票据档）',NULL,NULL,'1','2026-10-22 06:48:21.053',NULL,NULL,'91fa3fb3-cde4-11f1-bd9e-d8bbc1e55a1a',NULL),('9222f996-cf43-11f1-94f2-d8bbc1e55a1a','资产调拨（非集中）',NULL,NULL,'1','2026-10-24 00:40:54.672',NULL,NULL,'9222f996-cf43-11f1-94f2-d8bbc1e55a1a',NULL),('abf53bf3-b6b4-11f1-8056-d8bbc1e55a1a','出差事前申请审批流程',NULL,NULL,'1','2026-09-22 18:37:32.054',NULL,NULL,'abf53bf3-b6b4-11f1-8056-d8bbc1e55a1a',NULL),('ac25c1db-b6b4-11f1-8056-d8bbc1e55a1a','合同审批流程',NULL,NULL,'1','2026-09-22 18:37:32.372',NULL,NULL,'ac25c1db-b6b4-11f1-8056-d8bbc1e55a1a',NULL),('bcb87c97-b697-11f1-8056-d8bbc1e55a1a','资金上存/调拨审批流程',NULL,NULL,'1','2026-09-22 15:10:24.772',NULL,NULL,'bcb87c97-b697-11f1-8056-d8bbc1e55a1a',NULL),('c0a9d1da-dbee-11f1-baa9-d8bbc1e55a1a','对公付款审批流程（采购/预付/税缴/薪酬档）',NULL,NULL,'1','2026-11-09 03:36:30.625',NULL,NULL,'c0a9d1da-dbee-11f1-baa9-d8bbc1e55a1a',NULL),('c28b621f-b671-11f1-b3a2-d8bbc1e55a1a','应收账款管理分析报告审批流程',NULL,NULL,'1','2026-09-22 02:38:33.667',NULL,NULL,'c28b621f-b671-11f1-b3a2-d8bbc1e55a1a',NULL),('d3a644c4-b46d-11f1-8a2d-d8bbc1e55a1a','预算方案审批流程',NULL,NULL,'1','2026-09-19 21:05:22.051',NULL,NULL,'d3a644c4-b46d-11f1-8a2d-d8bbc1e55a1a',NULL),('d5d2c9d5-db25-11f1-baa9-d8bbc1e55a1a','费用报销审批',NULL,NULL,'1','2026-11-08 03:38:17.283',NULL,NULL,'d5d2c9d5-db25-11f1-baa9-d8bbc1e55a1a',NULL),('d74d2385-bb5f-11f1-8f3d-d8bbc1e55a1a','期间关账（作业）',NULL,NULL,'1','2026-09-28 17:12:53.363',NULL,NULL,'d74d2385-bb5f-11f1-8f3d-d8bbc1e55a1a',NULL),('da7146ed-bb5f-11f1-8f3d-d8bbc1e55a1a','内部往来抵消（作业）',NULL,NULL,'1','2026-09-28 17:12:58.626',NULL,NULL,'da7146ed-bb5f-11f1-8f3d-d8bbc1e55a1a',NULL),('e2358133-b68c-11f1-8056-d8bbc1e55a1a','资本性支出申请(CER)审批流程',NULL,NULL,'1','2026-09-22 13:52:43.203',NULL,NULL,'e2358133-b68c-11f1-8056-d8bbc1e55a1a',NULL),('f18b2d3f-da68-11f1-884c-d8bbc1e55a1a','费用报销（两级审批）',NULL,NULL,'1','2026-11-07 05:06:08.908',NULL,NULL,'f18b2d3f-da68-11f1-884c-d8bbc1e55a1a',NULL),('f3902e67-c84f-11f1-9186-d8bbc1e55a1a','合并报表（作业）',NULL,NULL,'1','2026-10-15 04:21:53.948',NULL,NULL,'f3902e67-c84f-11f1-9186-d8bbc1e55a1a',NULL),('f399e902-da68-11f1-884c-d8bbc1e55a1a','费用报销（三级审批）',NULL,NULL,'1','2026-11-07 05:06:12.360',NULL,NULL,'f399e902-da68-11f1-884c-d8bbc1e55a1a',NULL);
/*!40000 ALTER TABLE `act_re_deployment` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:24:23
