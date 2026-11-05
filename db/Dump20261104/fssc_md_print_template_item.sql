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
-- Table structure for table `md_print_template_item`
--

DROP TABLE IF EXISTS `md_print_template_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_print_template_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID【多租户隔离字段】',
  `template_id` bigint NOT NULL COMMENT '所属模板 md_print_template.id',
  `item_key` varchar(64) NOT NULL COMMENT '字段标识(模板内唯一)',
  `label` varchar(128) NOT NULL COMMENT '显示标题',
  `data_source` varchar(200) DEFAULT NULL COMMENT '数据源路径:head.billNo / lines[].amount / system.today',
  `item_type` varchar(16) NOT NULL DEFAULT 'FIELD' COMMENT 'FIELD字段/LINE_DETAIL明细行/QR_CODE二维码/BARCODE条码/IMAGE图片/DIVIDER分隔线',
  `row_index` int NOT NULL DEFAULT '1' COMMENT '起始行(基于 template.grid_rows)',
  `col_index` int NOT NULL DEFAULT '1' COMMENT '起始列(基于 template.grid_cols)',
  `row_span` int NOT NULL DEFAULT '1' COMMENT '跨行',
  `col_span` int NOT NULL DEFAULT '1' COMMENT '跨列',
  `width_mm` decimal(10,2) DEFAULT NULL COMMENT '宽(mm),为空按跨列自动均分',
  `height_mm` decimal(10,2) DEFAULT NULL COMMENT '高(mm)',
  `font_size` int NOT NULL DEFAULT '12' COMMENT '字号(pt)',
  `font_weight` varchar(8) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL常规/BOLD加粗',
  `align` varchar(8) NOT NULL DEFAULT 'LEFT' COMMENT 'LEFT/CENTER/RIGHT',
  `required` tinyint NOT NULL DEFAULT '0' COMMENT '必录属性:1-必录(缺值时打印前拦截)',
  `locked` tinyint NOT NULL DEFAULT '0' COMMENT '锁定属性:1-打印时灰显不可改',
  `visible` tinyint NOT NULL DEFAULT '1' COMMENT '是否显示',
  `format_type` varchar(16) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT文本/NUMBER金额/DATE日期/PERCENT百分比',
  `decimal_places` int NOT NULL DEFAULT '2' COMMENT '小数位(金额)',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pti_tpl_key` (`template_id`,`item_key`),
  KEY `idx_pti_tenant_tpl` (`tenant_id`,`template_id`),
  KEY `idx_pti_tpl_sort` (`template_id`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打印模板字段项（布局坐标+必录/锁定属性+数据源绑定）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_print_template_item`
--

LOCK TABLES `md_print_template_item` WRITE;
/*!40000 ALTER TABLE `md_print_template_item` DISABLE KEYS */;
INSERT INTO `md_print_template_item` VALUES (1,1,1,'title','费用报销单',NULL,'FIELD',1,1,1,12,NULL,NULL,22,'BOLD','CENTER',0,1,1,'TEXT',2,0,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(2,1,1,'billNo','单据编号','head.billNo','FIELD',3,1,1,4,NULL,NULL,12,'NORMAL','LEFT',1,0,1,'TEXT',2,1,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(3,1,1,'applyDate','申请日期','head.applyDate','FIELD',3,5,1,4,NULL,NULL,12,'NORMAL','LEFT',1,0,1,'DATE',2,2,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(4,1,1,'applyUserName','申请人','head.applyUserName','FIELD',4,1,1,4,NULL,NULL,12,'NORMAL','LEFT',1,1,1,'TEXT',2,3,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(5,1,1,'deptName','部门','head.deptName','FIELD',4,5,1,4,NULL,NULL,12,'NORMAL','LEFT',0,0,1,'TEXT',2,4,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(6,1,1,'currencyCode','币种','head.currencyCode','FIELD',5,1,1,4,NULL,NULL,12,'NORMAL','LEFT',0,0,1,'TEXT',2,5,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(7,1,1,'totalAmount','金额','head.totalAmount','FIELD',5,5,1,4,NULL,NULL,12,'BOLD','RIGHT',1,0,1,'NUMBER',2,6,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(8,1,1,'lineHeader','费用明细',NULL,'DIVIDER',7,1,1,12,NULL,NULL,14,'BOLD','LEFT',0,0,1,'TEXT',2,7,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(9,1,1,'lines','明细行','lines','LINE_DETAIL',8,1,1,12,NULL,NULL,11,'NORMAL','LEFT',0,0,1,'TEXT',2,8,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(10,1,1,'colSubject','科目','lines[].subjectName','FIELD',8,1,1,4,NULL,NULL,11,'NORMAL','LEFT',0,0,1,'TEXT',2,9,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(11,1,1,'colSummary','摘要','lines[].summary','FIELD',8,5,1,4,NULL,NULL,11,'NORMAL','LEFT',0,0,1,'TEXT',2,10,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(12,1,1,'colAmount','金额','lines[].amount','FIELD',8,9,1,4,NULL,NULL,11,'NORMAL','RIGHT',0,0,1,'NUMBER',2,11,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(13,1,1,'signMaker','制单','head.applyUserName','FIELD',20,1,1,3,NULL,NULL,11,'NORMAL','CENTER',0,0,1,'TEXT',2,12,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(14,1,1,'signApprove','审批','head.approveUserName','FIELD',20,5,1,3,NULL,NULL,11,'NORMAL','CENTER',0,0,1,'TEXT',2,13,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(15,1,1,'signFinance','财务',NULL,'FIELD',20,9,1,3,NULL,NULL,11,'NORMAL','CENTER',0,0,1,'TEXT',2,14,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(16,1,2,'title','付款申请单',NULL,'FIELD',1,1,1,12,NULL,NULL,22,'BOLD','CENTER',0,1,1,'TEXT',2,0,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(17,1,2,'billNo','单据编号','head.billNo','FIELD',3,1,1,6,NULL,NULL,12,'NORMAL','LEFT',1,0,1,'TEXT',2,1,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(18,1,2,'applyDate','日期','head.applyDate','FIELD',3,7,1,6,NULL,NULL,12,'NORMAL','LEFT',1,0,1,'DATE',2,2,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(19,1,2,'payeeName','收款方','head.payeeName','FIELD',4,1,1,6,NULL,NULL,12,'NORMAL','LEFT',1,1,1,'TEXT',2,3,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(20,1,2,'payeeBank','开户行','head.payeeBank','FIELD',4,7,1,6,NULL,NULL,12,'NORMAL','LEFT',0,0,1,'TEXT',2,4,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(21,1,2,'payeeAccount','收款账号','head.payeeAccount','FIELD',5,1,1,6,NULL,NULL,12,'NORMAL','LEFT',1,1,1,'TEXT',2,5,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(22,1,2,'amount','付款金额','head.amount','FIELD',5,7,1,6,NULL,NULL,12,'BOLD','RIGHT',1,0,1,'NUMBER',2,6,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(23,1,2,'reason','付款事由','head.reason','FIELD',7,1,1,12,NULL,NULL,12,'NORMAL','LEFT',0,0,1,'TEXT',2,7,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(24,1,2,'signOper','经办',NULL,'FIELD',20,1,1,4,NULL,NULL,11,'NORMAL','CENTER',0,0,1,'TEXT',2,8,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(25,1,2,'signApprove','审批','head.approveUserName','FIELD',20,5,1,4,NULL,NULL,11,'NORMAL','CENTER',0,0,1,'TEXT',2,9,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(26,1,2,'signFinance','财务',NULL,'FIELD',20,9,1,4,NULL,NULL,11,'NORMAL','CENTER',0,0,1,'TEXT',2,10,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(31,1,3,'title','合同登记表',NULL,'FIELD',1,1,1,12,NULL,NULL,22,'BOLD','CENTER',0,1,1,'TEXT',2,0,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(32,1,3,'contractNo','合同编号','head.contractNo','FIELD',3,1,1,4,NULL,NULL,12,'NORMAL','LEFT',1,0,1,'TEXT',2,1,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(33,1,3,'signDate','签订日期','head.signDate','FIELD',3,5,1,4,NULL,NULL,12,'NORMAL','LEFT',1,0,1,'DATE',2,2,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(34,1,3,'contractName','合同名称','head.contractName','FIELD',4,1,1,6,NULL,NULL,12,'NORMAL','LEFT',1,1,1,'TEXT',2,3,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(35,1,3,'supplierName','供应商','head.supplierName','FIELD',4,7,1,6,NULL,NULL,12,'NORMAL','LEFT',0,0,1,'TEXT',2,4,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(36,1,3,'contractAmount','合同金额','head.contractAmount','FIELD',5,1,1,4,NULL,NULL,12,'BOLD','RIGHT',0,0,1,'NUMBER',2,5,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(37,1,3,'currencyCode','币种','head.currencyCode','FIELD',5,5,1,4,NULL,NULL,12,'NORMAL','LEFT',0,0,1,'TEXT',2,6,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(38,1,3,'summary','合同内容摘要','head.summary','FIELD',7,1,1,12,NULL,NULL,12,'NORMAL','LEFT',0,0,1,'TEXT',2,7,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(46,1,4,'title','发票登记表',NULL,'FIELD',1,1,1,12,NULL,NULL,22,'BOLD','CENTER',0,1,1,'TEXT',2,0,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(47,1,4,'invoiceCode','发票代码','head.invoiceCode','FIELD',3,1,1,6,NULL,NULL,12,'NORMAL','LEFT',1,1,1,'TEXT',2,1,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(48,1,4,'invoiceNo','发票号码','head.invoiceNo','FIELD',3,7,1,6,NULL,NULL,12,'NORMAL','LEFT',1,1,1,'TEXT',2,2,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(49,1,4,'invoiceDate','开票日期','head.invoiceDate','FIELD',4,1,1,6,NULL,NULL,12,'NORMAL','LEFT',1,0,1,'DATE',2,3,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(50,1,4,'amount','金额','head.amount','FIELD',4,7,1,6,NULL,NULL,12,'BOLD','RIGHT',1,0,1,'NUMBER',2,4,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(51,1,4,'sellerName','销方名称','head.sellerName','FIELD',5,1,1,6,NULL,NULL,12,'NORMAL','LEFT',0,0,1,'TEXT',2,5,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(52,1,4,'buyerName','购方名称','head.buyerName','FIELD',5,7,1,6,NULL,NULL,12,'NORMAL','LEFT',0,0,1,'TEXT',2,6,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(53,1,5,'title','借款申请单',NULL,'FIELD',1,1,1,12,NULL,NULL,22,'BOLD','CENTER',0,1,1,'TEXT',2,0,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(54,1,5,'billNo','单据编号','head.billNo','FIELD',3,1,1,6,NULL,NULL,12,'NORMAL','LEFT',1,0,1,'TEXT',2,1,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(55,1,5,'applyDate','日期','head.applyDate','FIELD',3,7,1,6,NULL,NULL,12,'NORMAL','LEFT',1,0,1,'DATE',2,2,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(56,1,5,'applyUserName','借款人','head.applyUserName','FIELD',4,1,1,6,NULL,NULL,12,'NORMAL','LEFT',1,0,1,'TEXT',2,3,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(57,1,5,'deptName','部门','head.deptName','FIELD',4,7,1,6,NULL,NULL,12,'NORMAL','LEFT',0,0,1,'TEXT',2,4,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(58,1,5,'loanAmount','借款金额','head.loanAmount','FIELD',5,1,1,6,NULL,NULL,12,'BOLD','RIGHT',1,0,1,'NUMBER',2,5,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(59,1,5,'repayDate','预计还款日','head.repayDate','FIELD',5,7,1,6,NULL,NULL,12,'NORMAL','LEFT',0,0,1,'DATE',2,6,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(60,1,5,'reason','借款事由','head.reason','FIELD',7,1,1,12,NULL,NULL,12,'NORMAL','LEFT',0,0,1,'TEXT',2,7,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(61,1,5,'signBorrower','借款人','head.applyUserName','FIELD',20,1,1,4,NULL,NULL,11,'NORMAL','CENTER',0,0,1,'TEXT',2,8,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(62,1,5,'signApprove','审批',NULL,'FIELD',20,5,1,4,NULL,NULL,11,'NORMAL','CENTER',0,0,1,'TEXT',2,9,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0),(63,1,5,'signFinance','财务',NULL,'FIELD',20,9,1,4,NULL,NULL,11,'NORMAL','CENTER',0,0,1,'TEXT',2,10,NULL,NULL,'2026-10-18 10:59:11',NULL,NULL,0);
/*!40000 ALTER TABLE `md_print_template_item` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-05 22:33:52
