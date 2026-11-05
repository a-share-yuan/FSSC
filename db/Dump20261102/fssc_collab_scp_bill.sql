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
-- Table structure for table `collab_scp_bill`
--

DROP TABLE IF EXISTS `collab_scp_bill`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collab_scp_bill` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `bill_no` varchar(48) NOT NULL COMMENT 'SCP业务单号',
  `recon_bill_no` varchar(48) NOT NULL COMMENT '对账单号(三单匹配最小单位,打通点1)',
  `pre_invoice_no` varchar(64) DEFAULT NULL COMMENT '预制发票号=对账单号+三位流水(R-31,打通点2)',
  `invoice_no` varchar(64) DEFAULT NULL COMMENT '发票号码',
  `invoice_code` varchar(64) DEFAULT NULL COMMENT '发票代码',
  `po_no` varchar(64) DEFAULT NULL COMMENT 'PO编号',
  `supplier_code` varchar(48) DEFAULT NULL COMMENT '供应商代码',
  `supplier_name` varchar(128) DEFAULT NULL,
  `buyer_company_code` varchar(32) DEFAULT NULL COMMENT '购方公司代码',
  `invoice_type` varchar(16) DEFAULT NULL COMMENT '专票/普票(R-30)',
  `ccy` varchar(8) DEFAULT 'CNY',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '发票金额',
  `tax_amount` decimal(18,2) DEFAULT NULL COMMENT '发票税额',
  `total_amount` decimal(18,2) DEFAULT NULL COMMENT '价税合计',
  `invoice_date` date DEFAULT NULL COMMENT '发票日期(六要素)',
  `recon_amount` decimal(18,2) DEFAULT NULL COMMENT '对账单基准金额(自动匹配比对)',
  `recon_tax_amount` decimal(18,2) DEFAULT NULL COMMENT '对账单基准税额',
  `recon_total` decimal(18,2) DEFAULT NULL COMMENT '对账单基准价税合计',
  `buyer_tax_no` varchar(64) DEFAULT NULL COMMENT '购方税号(R-20六要素)',
  `seller_tax_no` varchar(64) DEFAULT NULL COMMENT '销方税号(R-20六要素)',
  `image_ref` varchar(255) DEFAULT NULL COMMENT '扫描件影像引用',
  `courier_company` varchar(64) DEFAULT NULL COMMENT '快递公司',
  `courier_no` varchar(64) DEFAULT NULL COMMENT '快递单号',
  `pay_voucher_no` varchar(48) DEFAULT NULL COMMENT '付款凭证号(打通点5)',
  `pay_date` date DEFAULT NULL COMMENT '付款日期(打通点5)',
  `pay_amount` decimal(18,2) DEFAULT NULL COMMENT '付款金额(打通点5)',
  `certify_result` varchar(128) DEFAULT NULL COMMENT '认证结果(打通点6)',
  `certify_date` date DEFAULT NULL COMMENT '认证日期(打通点6)',
  `status` varchar(24) NOT NULL DEFAULT 'WAIT_MATCH' COMMENT 'WAIT_MATCH/WAIT_SEND/MATCH_FAILED/WAIT_SIGN/WAIT_SCAN/CHECKING/CHECK_OK/CHECK_FAILED/POSTED/CERTIFIED',
  `force_status` varchar(16) DEFAULT NULL COMMENT 'NONE/PENDING/APPROVED/REJECTED',
  `force_reason` varchar(500) DEFAULT NULL COMMENT '寮哄埗鍖归厤鍘熷洜(R-18)',
  `force_apply_user` varchar(64) DEFAULT NULL COMMENT '寮哄尮鐢宠?浜',
  `force_appr_user` varchar(64) DEFAULT NULL COMMENT '寮哄尮瀹℃壒浜?鍥涚溂:鈮犵敵璇蜂汉)',
  `force_prev_status` varchar(24) DEFAULT NULL COMMENT '瀹℃壒椹冲洖鍥為?鐘舵?',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '匹配/票核失败原因',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bill_no` (`tenant_id`,`bill_no`),
  KEY `idx_recon` (`tenant_id`,`recon_bill_no`),
  KEY `idx_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SCP供应商协同发票全流程(蓝图SCC_AP_01~10,打通点1~7)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `collab_scp_bill`
--

LOCK TABLES `collab_scp_bill` WRITE;
/*!40000 ALTER TABLE `collab_scp_bill` DISABLE KEYS */;
/*!40000 ALTER TABLE `collab_scp_bill` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:30:06
