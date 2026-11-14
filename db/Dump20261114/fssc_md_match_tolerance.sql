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
-- Table structure for table `md_match_tolerance`
--

DROP TABLE IF EXISTS `md_match_tolerance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_match_tolerance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `company_id` bigint DEFAULT NULL COMMENT '适用公司(NULL=全组织)',
  `supplier_id` bigint DEFAULT NULL COMMENT '适用供应商(NULL=全部)',
  `material_group` varchar(50) DEFAULT NULL COMMENT '适用物料组(NULL=全部,预留)',
  `dimension_code` varchar(32) NOT NULL COMMENT '维度:PAY_VS_INVOICE/INVOICE_VS_RECEIPT/RECEIPT_VS_PO',
  `tolerance_type` varchar(16) DEFAULT 'PERCENT' COMMENT '容差类型:PERCENT百分比/FIXED固定金额',
  `tolerance_value` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '容差值(百分比用如2.0000=2%;固定金额用元)',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `effect_date` date DEFAULT NULL COMMENT '生效日(NULL=立即)',
  `expire_date` date DEFAULT NULL COMMENT '失效日(NULL=长期)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否/1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `scope_key` varchar(64) GENERATED ALWAYS AS (concat(if((`company_id` is null),_gbk'-',`company_id`),_gbk'|',if((`supplier_id` is null),_gbk'-',`supplier_id`))) STORED COMMENT 'D-TOL-1: 浣滅敤鍩熸寚绾?company|supplier锛圢ULL 褰掍竴涓?-锛',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_tol_dim_scope` (`tenant_id`,`dimension_code`,`scope_key`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='三单匹配参数化容差配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_match_tolerance`
--

LOCK TABLES `md_match_tolerance` WRITE;
/*!40000 ALTER TABLE `md_match_tolerance` DISABLE KEYS */;
INSERT INTO `md_match_tolerance` (`id`, `tenant_id`, `company_id`, `supplier_id`, `material_group`, `dimension_code`, `tolerance_type`, `tolerance_value`, `remark`, `effect_date`, `expire_date`, `create_time`, `update_time`, `del_flag`, `create_by`, `update_by`) VALUES (2,1,NULL,NULL,NULL,'INVOICE_VS_RECEIPT','PERCENT',2.0000,'smoke-restore',NULL,NULL,'2026-08-28 19:09:47','2026-08-28 19:46:38',0,NULL,1),(3,1,NULL,NULL,NULL,'RECEIPT_VS_PO','PERCENT',2.0000,'收货≤订单：超容差 WARN',NULL,NULL,'2026-08-28 19:09:47','2026-08-28 19:09:47',0,NULL,NULL),(6,1,31,NULL,NULL,'PAY_VS_INVOICE','PERCENT',1.5000,'D-TOL-1回归验证-公司级规则',NULL,NULL,'2026-09-21 07:45:10','2026-09-21 07:45:10',0,3797,3797),(7,1,NULL,NULL,NULL,'PAY_VS_INVOICE','PERCENT',1.5000,'全局兜底-三单匹配联调(20261015)',NULL,NULL,'2026-10-15 18:13:07','2026-10-15 18:13:07',0,NULL,NULL);
/*!40000 ALTER TABLE `md_match_tolerance` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:28:53
