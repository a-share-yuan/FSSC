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
-- Table structure for table `sod_perm_conflict`
--

DROP TABLE IF EXISTS `sod_perm_conflict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sod_perm_conflict` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `rule_code` varchar(32) NOT NULL,
  `rule_name` varchar(128) DEFAULT NULL,
  `risk` varchar(8) DEFAULT '中',
  `pattern_a` varchar(255) NOT NULL,
  `pattern_b` varchar(255) NOT NULL,
  `note` varchar(255) DEFAULT NULL,
  `status` varchar(16) DEFAULT 'ACTIVE',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule` (`tenant_id`,`rule_code`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SoD permission-level conflict rules';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sod_perm_conflict`
--

LOCK TABLES `sod_perm_conflict` WRITE;
/*!40000 ALTER TABLE `sod_perm_conflict` DISABLE KEYS */;
INSERT INTO `sod_perm_conflict` VALUES (1,NULL,'SoD-01','供应商欺诈','高','^md:(supplier|payee|bankaccount):(create|update|delete)$','^(pay:batch:(execute|confirm)|apflow:request:pay)$','维护供应商/收款方银行账户的人不应同时执行付款','ACTIVE','2026-09-17 14:33:43'),(2,NULL,'SoD-02','自审自付（付款申请↔付款执行）','高','^(pay:batch:create|pay:payment:create|apflow:request:(create|dispatch))$','^(pay:batch:(execute|confirm)|apflow:request:pay)$','发起付款的人不应能确认/执行付款','ACTIVE','2026-09-17 14:33:43'),(3,NULL,'SoD-03','自报自批（报销提交↔报销审核）','高','^exp:bill:submit$','^exp:bill:audit$','2026-10-19 降级：粒度问题——判据为「同时持 submit+audit 两个权限」，凡审核岗必然命中（本规则实测 28 人全为审核岗，无一例外），属报表噪声非真实风险。真正的自报自批拦截在 approve.deny_self（见 SoD DUTY 维度，实测 23 条真实自审轨迹）。本规则降级为影子规则，不计入 total；恢复执行：UPDATE sod_perm_conflict SET status=\'ACTIVE\' WHERE rule_code=\'SoD-03\';','SHADOW','2026-09-17 14:33:43'),(4,NULL,'SoD-04','应付审批↔付款执行','高','^apflow:request:review$','^(apflow:request:pay|pay:batch:(execute|confirm))$','审批应付的人不应能执行付款','ACTIVE','2026-09-17 14:33:43'),(5,NULL,'SoD-05','凭证生成↔凭证过账','高','^(sharing:voucher:generate|gl:journal:generate)$','^(sharing:voucher:post|gl:entry:post)$','制单与过账须分离','ACTIVE','2026-09-17 14:33:43'),(6,NULL,'SoD-06','期间关账↔凭证据录入','高','^(gl:monthclose:close|apflow:close:close|arflow:monthclose:manage|md:period:close)$','^(gl:journal:generate|sharing:voucher:generate|gl:entry:post)$','能关账的人不应能补录凭证','ACTIVE','2026-09-17 14:33:43'),(7,NULL,'SoD-07','资产建档↔资产变动','中','^asset:create$','^asset:update$','建档与变动分离','ACTIVE','2026-09-17 14:33:43'),(8,NULL,'SoD-08','银行对账↔出纳收付','高','^(bank:reconcile:list|op:bank:import)$','^(pay:batch:execute|arflow:note:cashier)$','保管(收付)与复核(对账)须分离','ACTIVE','2026-09-17 14:33:43'),(9,NULL,'SoD-09','票据登记↔票据出纳','高','^arflow:note:register$','^arflow:note:cashier$','登记票据的人不应同时管理票据收付','ACTIVE','2026-09-17 14:33:43'),(10,NULL,'SoD-10','流程建模↔单据审批','高','^wf:(model|node|config):(create|update|delete)$','^(exp:bill:audit|apflow:request:review)$','可改写审批路径的人不应同时审批单据','ACTIVE','2026-09-17 14:33:43'),(11,NULL,'SoD-11','权限管理↔业务审批（自提权）','高','^system:(user:(create|delete|assignRole|resetPwd)|role:(create|delete|update|assignMenu))$','^(exp:bill:audit|apflow:request:review|pay:batch:(confirm|execute))$','能改用户/角色权限的人不应同时持有关键业务审批权','ACTIVE','2026-09-17 14:33:43'),(12,NULL,'SoD-12','反欺诈检测↔反欺诈复核','中','^compliance:fraud:detect$','^compliance:fraud:review$','检测与复核分离','ACTIVE','2026-09-17 14:33:43'),(13,NULL,'SoD-13','档案借阅申请↔审批','中','^compliance:archiveaccess:request$','^compliance:archiveaccess:approve$','申请与审批分离','ACTIVE','2026-09-17 14:33:43'),(14,NULL,'SoD-14','档案销毁申请↔审批执行','高','^compliance:archivedestroy:create$','^compliance:archivedestroy:(approve|execute)$','销毁档案须双人','ACTIVE','2026-09-17 14:33:43'),(15,NULL,'SoD-15','税务申报制单↔审批','高','^gl:taxdecl:(create|submit)$','^gl:taxdecl:approve$','申报与审批分离','ACTIVE','2026-09-17 14:33:43'),(16,NULL,'SoD-16','退税申请↔审批','中','^gl:taxrefund:create$','^gl:taxrefund:approve$','退税申请与审批分离','ACTIVE','2026-09-17 14:33:43'),(17,NULL,'SoD-17','薪资计算↔薪资发放','高','^gl:salary:calculate$','^gl:salary:pay$','算薪与发薪分离','ACTIVE','2026-09-17 14:33:43'),(18,NULL,'SoD-18','预算编制↔预算调整审批','中','^budget:scheme:create$','^budget:adjust:approve$','编制与调整审批分离','ACTIVE','2026-09-17 14:33:43'),(19,NULL,'SoD-19','资金调拨发起↔执行','高','^treasury:transfer:(create|update)$','^treasury:transfer:execute$','发起与执行分离','ACTIVE','2026-09-17 14:33:43'),(20,NULL,'SoD-20','票据结算发起↔审批','中','^treasury:billsettle:(create|update)$','^treasury:transfer:execute$','票据结算与资金执行分离','ACTIVE','2026-09-17 14:33:43'),(21,NULL,'SoD-21','主数据配置↔业务过账','中','^md:config$','^(gl:entry:post|sharing:voucher:post)$','改主数据的人不应能直接过账','ACTIVE','2026-09-17 14:33:43'),(22,NULL,'SoD-22','预检规则维护↔反欺诈检测','低','^compliance:(precheck:(create|update|delete)|rule:edit)$','^compliance:fraud:detect$','规则维护者不宜同时做检测判定','ACTIVE','2026-09-17 14:33:43'),(23,NULL,'SoD-23','自报自批（退货提交↔退货审核）','高','^returns:(pur|sale):(create|submit)$','^returns:(pur|sale):approve$','制单/提交退货单的人不应能审核该退货单','ACTIVE','2026-09-19 10:08:51'),(25,NULL,'SoD-24','供应商建档↔核准','高','^md:supplier:(create|update|delete)$','^md:supplier:approve$','供应商是付款对象的唯一来源，建档与核准须分离；同角色兼任可自建虚假供应商并核准后付款','ACTIVE','2026-11-03 08:15:35');
/*!40000 ALTER TABLE `sod_perm_conflict` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-09 17:24:03
