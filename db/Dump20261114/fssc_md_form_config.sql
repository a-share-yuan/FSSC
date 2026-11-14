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
-- Table structure for table `md_form_config`
--

DROP TABLE IF EXISTS `md_form_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_form_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `form_code` varchar(50) NOT NULL,
  `bill_type` varchar(32) DEFAULT NULL COMMENT '单据大类 APPLY事前申请/CLAIM员工报销/PAY对公支付/LOAN借款/REPAY还款',
  `bill_sub_type` varchar(32) DEFAULT NULL COMMENT '子类型 TRAVEL/PREPAY/SETTLE/AGENCY/AIR等',
  `category` varchar(32) DEFAULT NULL COMMENT '入口分类 APPLY/CLAIM/PAY/LOAN/REPAY',
  `form_name` varchar(100) NOT NULL,
  `config_json` text,
  `model_key` varchar(64) DEFAULT NULL COMMENT '关联流程模型key(参考绑定)',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '流程路由键 wf_process_config.biz_type，空则取 form_code',
  `store_mode` varchar(16) NOT NULL DEFAULT 'JSON' COMMENT '存储方式 JSON 通用运行库 / EXPENSE_WIDE 费用宽表',
  `menu_path` varchar(100) DEFAULT NULL COMMENT '发起页路径，空则 /form/{form_code}',
  `menu_name` varchar(100) DEFAULT NULL COMMENT '侧栏显示名，空则取 form_name',
  `menu_parent_id` bigint DEFAULT NULL COMMENT '挂在哪个菜单目录下',
  `title_expr` varchar(200) DEFAULT NULL COMMENT '流程标题取值字段 key，空则 form_name+申请人',
  `allow_draft` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许存草稿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_form` (`tenant_id`,`form_code`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表单详情配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_form_config`
--

LOCK TABLES `md_form_config` WRITE;
/*!40000 ALTER TABLE `md_form_config` DISABLE KEYS */;
INSERT INTO `md_form_config` VALUES (1,1,'EXP_MEETING','APPLY','MEETING','APPLY','会议申请单','{\"sections\":[\"概要\",\"会议信息\",\"分摊\",\"附件\"],\"needBudget\":true}','MEETING','NORMAL','按需求4.1.1',NULL,'2026-08-12 09:05:32',NULL,'2026-11-07 18:59:38',0,NULL,'EXPENSE_WIDE',NULL,NULL,NULL,NULL,1),(2,1,'EXP_TRAVEL','APPLY','TRAVEL','APPLY','出差申请单','{\"sections\":[\"概要\",\"出差信息\",\"分摊\",\"附件\"],\"needBudget\":true}','TRAVEL','NORMAL','事前申请-出差',1,'2026-08-13 09:01:46',NULL,'2026-11-07 18:59:38',0,NULL,'EXPENSE_WIDE',NULL,NULL,NULL,NULL,1),(3,1,'EXP_TRAIN','APPLY','TRAIN','APPLY','培训申请单','{\"sections\":[\"概要\",\"培训信息\",\"分摊\",\"附件\"],\"needBudget\":true}','TRAIN','NORMAL','事前申请-培训',1,'2026-08-13 09:01:46',NULL,'2026-11-07 18:59:38',0,NULL,'EXPENSE_WIDE',NULL,NULL,NULL,NULL,1),(4,1,'EXP_CLAIM','CLAIM',NULL,'CLAIM','员工费用报销单','{\"sections\":[\"概要\",\"费用明细\",\"分摊\",\"附件\"],\"needInvoice\":true}','CLAIM','NORMAL','员工报销',1,'2026-08-13 09:01:46',NULL,'2026-11-07 18:59:38',0,NULL,'EXPENSE_WIDE',NULL,NULL,NULL,NULL,1),(5,1,'EXP_CLAIM_TRAVEL','CLAIM','TRAVEL','CLAIM','差旅培训报销单','{\"sections\":[\"概要\",\"差旅明细\",\"分摊\",\"附件\"],\"needInvoice\":true}','CLAIM_TRAVEL','NORMAL','员工报销-差旅培训',1,'2026-08-13 09:01:46',NULL,'2026-11-07 18:59:38',0,NULL,'EXPENSE_WIDE',NULL,NULL,NULL,NULL,1),(6,1,'EXP_PREPAY','PAY','PREPAY','PAY','费用预付款单','{\"sections\":[\"概要\",\"付款信息\",\"合同\",\"附件\"],\"needContract\":false}','PREPAY','NORMAL','对公支付-费用预付',1,'2026-08-13 09:01:46',NULL,'2026-11-07 18:59:38',0,NULL,'EXPENSE_WIDE',NULL,NULL,NULL,NULL,1),(7,1,'EXP_SETTLE','PAY','SETTLE','PAY','对公结算单','{\"sections\":[\"概要\",\"付款信息\",\"合同\",\"附件\"],\"needContract\":false}','SETTLE','NORMAL','对公支付-对公结算',1,'2026-08-13 09:01:46',NULL,'2026-11-07 18:59:38',0,NULL,'EXPENSE_WIDE',NULL,NULL,NULL,NULL,1),(8,1,'EXP_AGENCY','PAY','AGENCY','PAY','代收代付单','{\"sections\":[\"概要\",\"代收代付明细\",\"附件\"]}','AGENCY','NORMAL','对公支付-代收代付',1,'2026-08-13 09:01:46',NULL,'2026-11-07 18:59:38',0,NULL,'EXPENSE_WIDE',NULL,NULL,NULL,NULL,1),(9,1,'EXP_AIR','PAY','AIR','PAY','机票结算单','{\"sections\":[\"概要\",\"机票明细\",\"附件\"]}','AIR','NORMAL','对公支付-机票结算',1,'2026-08-13 09:01:46',NULL,'2026-11-07 18:59:38',0,NULL,'EXPENSE_WIDE',NULL,NULL,NULL,NULL,1),(10,1,'EXP_AIR_APPLY','PAY','AIR_APPLY','PAY','机票结算申请单','{\"sections\":[\"概要\",\"机票申请\",\"附件\"]}','AIR_APPLY','NORMAL','对公支付-机票结算申请',1,'2026-08-13 09:01:46',NULL,'2026-11-07 18:59:38',0,NULL,'EXPENSE_WIDE',NULL,NULL,NULL,NULL,1),(11,1,'EXP_LOAN','LOAN',NULL,'LOAN','借款申请单','{\"sections\":[\"概要\",\"借款信息\",\"附件\"]}','LOAN','NORMAL','借款还款-借款',1,'2026-08-13 09:01:46',NULL,'2026-11-07 18:59:38',0,NULL,'EXPENSE_WIDE',NULL,NULL,NULL,NULL,1),(12,1,'EXP_REPAY','REPAY',NULL,'REPAY','还款申请单','{\"sections\":[\"概要\",\"还款信息\",\"附件\"]}','REPAY','NORMAL','借款还款-还款',1,'2026-08-13 09:01:46',NULL,'2026-11-07 18:59:38',0,NULL,'EXPENSE_WIDE',NULL,NULL,NULL,NULL,1),(13,1,'TEST_FORM','CLAIM','TEST','CLAIM','测试单据','{\"sections\":[\"概要\"]}','CLAIM','NORMAL','CRUD测试',1,'2026-08-13 09:05:04',1,'2026-08-13 09:05:05',1,NULL,'JSON',NULL,NULL,NULL,NULL,1),(14,1,'DEMO_FORM','FORM',NULL,'FORM','通用申请单',NULL,'MD_MASTER','NORMAL','可配置表单平台的配置样例，客户可删除',NULL,'2026-11-07 19:00:23',NULL,'2026-11-07 19:00:23',0,'DEMO_FORM','JSON','/form/DEMO_FORM','通用申请单',3550,NULL,1);
/*!40000 ALTER TABLE `md_form_config` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:28:46
