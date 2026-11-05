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
-- Table structure for table `exp_bill_cold`
--

DROP TABLE IF EXISTS `exp_bill_cold`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exp_bill_cold` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `bill_no` varchar(64) NOT NULL COMMENT '单据编号(由编号规则生成)',
  `paperless` varchar(8) DEFAULT 'N' COMMENT '无纸化标记：Y 无纸化 / N 需实物',
  `delivery_status` varchar(16) DEFAULT NULL COMMENT '实物签收状态：NOT_REQUIRED/UNSIGNED/SIGNED',
  `reject_type` varchar(16) DEFAULT NULL COMMENT '驳回类型：THROW_BACK 漏单驳回 / GRADE_BY_GRADE 逐级驳回',
  `bill_type` varchar(20) NOT NULL COMMENT '单据类型:APPLY事前申请/CLAIM费用报销/LOAN员工借款/REPAY借款还款/PAY对公支付',
  `bill_sub_type` varchar(50) DEFAULT NULL COMMENT '单据子类:TRAVEL差旅/DAILY日常/ENTERTAIN招待/TRAIN培训等(取字典)',
  `title` varchar(300) NOT NULL COMMENT '事由/标题',
  `company_id` bigint DEFAULT NULL,
  `settle_company_id` bigint DEFAULT NULL COMMENT '结算(报销)单位：跨组织场景下与费用承担单位 company_id 分离',
  `pay_company_id` bigint DEFAULT NULL COMMENT '支付单位：实际付款主体，跨组织资金结算使用',
  `loan_company_id` bigint DEFAULT NULL COMMENT '借款单位：跨组织借款时款项借入的公司（P2 #18 四维之一，默认同费用承担单位）',
  `dept_id` bigint DEFAULT NULL,
  `cost_center_id` bigint DEFAULT NULL COMMENT '成本中心ID',
  `project_id` bigint DEFAULT NULL COMMENT '项目ID',
  `apply_user_id` bigint NOT NULL COMMENT '申请人用户ID',
  `apply_user_name` varchar(50) DEFAULT NULL COMMENT '申请人姓名(冗余,防离职后查不到)',
  `agent_user_id` bigint DEFAULT NULL COMMENT '代填人用户ID(秘书代报销场景)',
  `receiver_user_id` bigint DEFAULT NULL COMMENT '收款人：代理报销时报销人(apply_user_id)≠收款人，款项付给 receiver_user_id',
  `apply_date` date NOT NULL COMMENT '申请日期',
  `period_year` int DEFAULT NULL COMMENT '归属会计年度',
  `period_month` int DEFAULT NULL COMMENT '归属会计月份',
  `currency_code` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '单据币种',
  `exchange_rate` decimal(20,10) NOT NULL DEFAULT '1.0000000000' COMMENT '汇率(折本位币)',
  `price_type` varchar(20) DEFAULT NULL COMMENT '录入口径：TAX_IN含税录入(默认)/TAX_EX不含税录入',
  `total_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '申请/报销总金额(含税)',
  `amount_cn` varchar(200) DEFAULT NULL COMMENT '金额大写(中文大写,打印/OA用)',
  `tax_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '税额合计',
  `net_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '不含税金额合计',
  `approved_amount` decimal(20,2) DEFAULT NULL COMMENT '审批核定金额(审批人可核减)',
  `approved_pay_amount` decimal(20,2) DEFAULT NULL COMMENT '核定付款金额：≤核定报销金额，付款控制口径',
  `approved_refund_amount` decimal(20,2) DEFAULT NULL COMMENT '核定退款金额：=核定报销 − 核定付款（多退回少的部分）',
  `offset_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '冲抵金额(冲借款/预付)',
  `payable_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '应付金额 = 核定 - 冲抵',
  `paid_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '已付金额',
  `amount_checksum` varchar(64) DEFAULT NULL COMMENT '关键字段校验和(SHA-256，提交时生成)',
  `loan_balance` decimal(20,2) DEFAULT NULL COMMENT '借款未还余额(bill_type=LOAN时维护)',
  `expect_repay_date` date DEFAULT NULL COMMENT '预计还款日期',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '单据状态:DRAFT草稿/APPROVING审批中/REJECTED已驳回/APPROVED审批通过/AUDITING共享审核中/AUDIT_BACK审核退回/PAYING支付中/PAID已支付/ACCOUNTED已入账/CLOSED已关闭/CANCELLED已作废',
  `proc_inst_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `approve_time` datetime DEFAULT NULL COMMENT '审批通过时间',
  `pay_time` datetime DEFAULT NULL COMMENT '支付完成时间',
  `audit_user_id` bigint DEFAULT NULL COMMENT '共享审核人用户ID',
  `audit_time` datetime DEFAULT NULL COMMENT '共享审核时间',
  `audit_remark` varchar(1000) DEFAULT NULL COMMENT '审核意见',
  `image_status` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '影像状态:NONE无/WAITING待上传/UPLOADED已上传/CHECKED已核验/BACK影像退回',
  `archive_status` varchar(20) NOT NULL DEFAULT 'UNARCHIVED' COMMENT '归档状态 UNARCHIVED未归档/ARCHIVED已归档',
  `archive_time` datetime DEFAULT NULL COMMENT '归档时间',
  `archive_by` bigint DEFAULT NULL COMMENT '归档人',
  `voucher_no` varchar(64) DEFAULT NULL COMMENT '会计凭证号',
  `voucher_date` date DEFAULT NULL COMMENT '记账日期',
  `urgent` tinyint NOT NULL DEFAULT '0' COMMENT '是否加急:0否 1是',
  `source` varchar(20) NOT NULL DEFAULT 'PC' COMMENT '录入来源:PC/H5/APP/IMPORT/API',
  `source_bill_id` bigint DEFAULT NULL COMMENT '来源单据ID(报销单关联的事前申请单)',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  `ext_json` text COMMENT '扩展字段JSON(租户自定义表单项)',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `contract_id` bigint DEFAULT NULL COMMENT '关联合同ID(cont_contract)',
  `po_receipt_id` bigint DEFAULT NULL COMMENT '关联收货单ID',
  `po_id` bigint DEFAULT NULL COMMENT '关联采购订单ID',
  `customer_id` bigint DEFAULT NULL COMMENT '关联往来单位ID(crm_customer)',
  `payee_name` varchar(200) DEFAULT NULL COMMENT '收款方名称(对公支付)',
  `pay_target` varchar(20) DEFAULT NULL COMMENT '收款对象：EMPLOYEE员工/SUPPLIER供应商/CUSTOMER客户',
  `payee_account_no` varchar(64) DEFAULT NULL COMMENT '收款账号：按 pay_target 驱动必填（员工→个人卡，供应商/客户→对公卡）',
  `archived_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exp_bill_cold`
--

LOCK TABLES `exp_bill_cold` WRITE;
/*!40000 ALTER TABLE `exp_bill_cold` DISABLE KEYS */;
/*!40000 ALTER TABLE `exp_bill_cold` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:30:08
