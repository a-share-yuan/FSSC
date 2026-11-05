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
-- Table structure for table `inv_invoice`
--

DROP TABLE IF EXISTS `inv_invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inv_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `invoice_code` varchar(64) NOT NULL DEFAULT '' COMMENT '发票代码，全电票为空串',
  `invoice_no` varchar(32) NOT NULL COMMENT '发票号码',
  `invoice_type` varchar(20) NOT NULL DEFAULT 'VAT' COMMENT '发票类型:VAT增值税/PLAIN普票/SPECIAL专票/CONSUME消费票/OTHER',
  `invoice_title` varchar(200) DEFAULT NULL COMMENT '购方名称',
  `tax_no` varchar(64) DEFAULT NULL COMMENT '购方税号',
  `seller_name` varchar(200) DEFAULT NULL COMMENT '销方名称',
  `seller_tax_no` varchar(64) DEFAULT NULL COMMENT '销方税号',
  `invoice_date` date DEFAULT NULL COMMENT '开票日期',
  `amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '价税合计金额',
  `tax_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '税额',
  `deduct_amount` decimal(18,2) DEFAULT '0.00' COMMENT '可抵扣税额',
  `net_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '不含税金额',
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `check_code` varchar(64) DEFAULT NULL COMMENT '校验码(后6位)',
  `machine_no` varchar(32) DEFAULT NULL COMMENT '机器编号',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT '状态:NORMAL正常/INVALID作废/RED_FLUSH已红冲',
  `verify_status` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '验真状态:NONE未验/DOING验真中/PASS验真通过/FAIL验真失败',
  `verify_time` datetime DEFAULT NULL COMMENT '验真时间',
  `verify_msg` varchar(500) DEFAULT NULL COMMENT '验真返回信息',
  `certify_status` varchar(16) DEFAULT 'NOT_CERTIFIED' COMMENT '认证状态:NOT_CERTIFIED未勾选/CERTIFIED已勾选/REVERSED进项转出',
  `is_used` tinyint NOT NULL DEFAULT '0' COMMENT '是否已被报销使用:0否 1是(重复报销防控)',
  `used_count` int NOT NULL DEFAULT '0' COMMENT '被引用次数',
  `img_file_id` bigint DEFAULT NULL COMMENT '关联影像文件ID',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `invoice_nature` varchar(16) DEFAULT 'BLUE' COMMENT '发票性质 BLUE蓝票/RED红票',
  `invoice_kind` varchar(32) DEFAULT 'SPECIAL' COMMENT '发票种类 SPECIAL专票/NORMAL普票/ELECTRONIC电子',
  `certify_period` varchar(6) DEFAULT NULL COMMENT '勾选认证所属期yyyyMM',
  `certify_date` datetime DEFAULT NULL COMMENT '勾选认证时间',
  `reverse_date` datetime DEFAULT NULL COMMENT '进项转出时间',
  `transfer_reason` varchar(64) DEFAULT NULL COMMENT '进项转出原因（集体福利/个人消费/非正常损失/简易计税/免税项目/其他）',
  `buyer_name` varchar(255) DEFAULT NULL COMMENT '购方抬头(企业名称)',
  `buyer_tax_no` varchar(64) DEFAULT NULL COMMENT '购方税号(企业统一社会信用代码)',
  `lock_status` varchar(32) DEFAULT 'NONE' COMMENT '占用锁定:NONE未占用/LOCKED_IN_USE已占用',
  `anomaly_flag` varchar(64) DEFAULT 'NORMAL' COMMENT '异常标记:NORMAL/RED_FLUSH/SELLER_BLACK/INVALID',
  `company_id` bigint DEFAULT NULL COMMENT '所属公司(多分子公司审批路由 V1.3.17)',
  `source_doc_type` varchar(32) DEFAULT NULL COMMENT '来源单据类型:BILL单据/IMG_FILE影像/Manual手工录入',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据ID（source_doc_type 对应表的主键）',
  `original_id` bigint DEFAULT NULL COMMENT '红字发票关联的原蓝票ID(inv_invoice.id)',
  `direction` varchar(10) NOT NULL DEFAULT 'INPUT' COMMENT '方向 INPUT进项(收票)/OUTPUT销项(开票)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_inv` (`tenant_id`,`invoice_no`,`invoice_code`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_date` (`tenant_id`,`invoice_date`),
  KEY `idx_tenant_verify` (`tenant_id`,`verify_status`),
  KEY `idx_tenant_anomaly` (`tenant_id`,`anomaly_flag`),
  KEY `idx_tenant_owner` (`tenant_id`,`create_by`),
  KEY `idx_ii_tenant_del_comp_id` (`tenant_id`,`del_flag`,`company_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发票主表(发票池)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inv_invoice`
--

LOCK TABLES `inv_invoice` WRITE;
/*!40000 ALTER TABLE `inv_invoice` DISABLE KEYS */;
/*!40000 ALTER TABLE `inv_invoice` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:30:34
