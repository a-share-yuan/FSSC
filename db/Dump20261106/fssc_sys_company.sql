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
-- Table structure for table `sys_company`
--

DROP TABLE IF EXISTS `sys_company`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_company` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `company_code` varchar(64) NOT NULL COMMENT '公司编码',
  `company_name` varchar(200) NOT NULL COMMENT '公司全称',
  `short_name` varchar(100) DEFAULT NULL COMMENT '公司简称',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '上级公司ID,0为顶级',
  `ancestors` varchar(500) DEFAULT '' COMMENT '祖级路径,逗号分隔,如 0,1,3',
  `credit_code` varchar(64) DEFAULT NULL COMMENT '统一社会信用代码',
  `tax_no` varchar(64) DEFAULT NULL COMMENT '纳税人识别号',
  `tax_type` varchar(20) DEFAULT 'GENERAL' COMMENT '纳税人类型:GENERAL一般纳税人/SMALL小规模',
  `bank_name` varchar(200) DEFAULT NULL COMMENT '开户银行',
  `bank_account` varchar(64) DEFAULT NULL COMMENT '银行账号',
  `address` varchar(500) DEFAULT NULL COMMENT '注册地址',
  `tel` varchar(30) DEFAULT NULL COMMENT '联系电话',
  `legal_person` varchar(50) DEFAULT NULL COMMENT '法定代表人',
  `currency` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '本位币',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT '状态:NORMAL正常/DISABLED停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `archive_mode` varchar(16) NOT NULL DEFAULT 'CENTRAL' COMMENT '档案管理模式：CENTRAL 集中（纸质原件寄送共享中心）/ NON_CENTRAL 非集中（原件留原单位，不产生实物流）',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_company_code` (`tenant_id`,`company_code`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_parent` (`tenant_id`,`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=99091 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司/法人主体';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_company`
--

LOCK TABLES `sys_company` WRITE;
/*!40000 ALTER TABLE `sys_company` DISABLE KEYS */;
INSERT INTO `sys_company` VALUES (1,1,'AIG-HQ','上海爱共享集团有限公司','爱共享集团',0,'0','91310000MA1FL8XY00','91310000MA1FL8XY00','GENERAL',NULL,NULL,'上海市浦东新区张江高科技园区爱共享路88号','021-5888 0001','陈志远','CNY',1,'NORMAL','集团母公司/共享中心所在地','CENTRAL',NULL,'2026-08-11 15:52:01',NULL,'2026-09-15 23:44:18',0),(31,1,'AIG-SH','上海爱共享销售有限公司','上海分公司',1,'0,1','91310000MA1FL9AB1X',NULL,'GENERAL',NULL,NULL,'上海市静安区南京西路1168号',NULL,'陈志远','CNY',2,'NORMAL','华东销售平台','CENTRAL',NULL,'2026-08-25 07:37:06',NULL,'2026-09-15 23:44:18',0),(32,1,'AIG-HN','湖南爱共享科技有限公司','湖南分公司',1,'0,1','91430100MA4L9CD2Y',NULL,'GENERAL',NULL,NULL,'长沙市岳麓区麓谷大道658号',NULL,'陈志远','CNY',3,'NORMAL','华中运营中心','CENTRAL',NULL,'2026-08-25 07:37:06',NULL,'2026-09-15 23:44:18',0),(33,1,'AIG-KS','昆山爱共享精密制造有限公司','昆山制造基地',1,'0,1','91305831MA1KM3EF4T',NULL,'GENERAL',NULL,NULL,'昆山市昆山经济技术开发区精密制造产业园18号',NULL,'陈志远','CNY',4,'NORMAL','精密制造基地(制造类成本中心)','NON_CENTRAL',NULL,'2026-08-25 07:37:06',NULL,'2026-10-24 08:41:11',0),(34,1,'AIG-SZ','苏州爱共享智能制造有限公司','苏州智造基地',1,'0,1','91320594MA1XN5GH8K',NULL,'GENERAL',NULL,NULL,'苏州工业园区金鸡湖大道99号',NULL,'陈志远','CNY',5,'NORMAL','智能制造基地','CENTRAL',NULL,'2026-08-25 07:37:06',NULL,'2026-09-15 23:44:18',0),(35,1,'AIG-RD','上海爱共享研发中心有限公司','研发中心',1,'0,1','91310115MA1K9PQ3R',NULL,'GENERAL',NULL,NULL,'上海市杨浦区创智天地5号楼',NULL,'陈志远','CNY',6,'NORMAL','集团研发中枢','CENTRAL',NULL,'2026-08-25 16:28:49',NULL,'2026-09-15 23:44:18',0),(36,1,'AIG-GZ','广州爱共享贸易有限公司','广州分公司',1,'0,1','91440101MA9UN7JK2L',NULL,'GENERAL',NULL,NULL,'广州市天河区珠江新城华夏路26号',NULL,'陈志远','CNY',7,'NORMAL','华南销售平台','CENTRAL',NULL,'2026-08-27 07:38:05',NULL,'2026-09-15 23:44:18',0),(37,1,'AIG-BJ','北京爱共享信息咨询有限公司','北京分公司',1,'0,1','91110108MA01R8MN5P',NULL,'GENERAL',NULL,NULL,'北京市海淀区中关村软件园二期',NULL,'陈志远','CNY',8,'NORMAL','华北销售平台','CENTRAL',NULL,'2026-08-27 07:38:05',NULL,'2026-09-15 23:44:18',0),(38,1,'AIG-CS','长沙爱共享服务有限公司','长沙服务公司',32,'0,1,32','91430100MA4LQW6T8B',NULL,'GENERAL',NULL,NULL,'长沙市雨花区韶山中路489号',NULL,'陈志远','CNY',9,'NORMAL','湖南分公司控股子公司(三级法人)','CENTRAL',NULL,'2026-08-27 07:38:05',NULL,'2026-09-15 23:44:18',0);
/*!40000 ALTER TABLE `sys_company` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-06 18:25:41
