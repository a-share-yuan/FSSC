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
-- Table structure for table `md_bank`
--

DROP TABLE IF EXISTS `md_bank`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_bank` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `bank_code` varchar(64) NOT NULL,
  `bank_name` varchar(200) NOT NULL,
  `bank_type` varchar(64) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `uk_bank` (`tenant_id`,`bank_code`)
) ENGINE=InnoDB AUTO_INCREMENT=940 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='金融机构（银行主数据）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_bank`
--

LOCK TABLES `md_bank` WRITE;
/*!40000 ALTER TABLE `md_bank` DISABLE KEYS */;
INSERT INTO `md_bank` VALUES (800,1,'BK_银行','银行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(801,1,'BK_招商银行股份有限公司烟台幸福路支行','招商银行股份有限公司烟台幸福路支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(802,1,'BK_中国光大银行烟台分行营业部','中国光大银行烟台分行营业部','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(803,1,'BK_中国农业银行股份有限公司烟台芝罘支行','中国农业银行股份有限公司烟台芝罘支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(804,1,'BK_中国建设银行股份有限公司烟台分行','中国建设银行股份有限公司烟台分行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(805,1,'BK_中国银行股份有限公司烟台经济技术开发区支行','中国银行股份有限公司烟台经济技术开发区支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(806,1,'BK_交通银行烟台分行经济技术开发区支行','交通银行烟台分行经济技术开发区支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(807,1,'BK_示例集团财务有限公司','示例集团财务有限公司','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(808,1,'BK_中国农业银行青岛市市南区第三支行','中国农业银行青岛市市南区第三支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(809,1,'BK_中国农业银行股份有限公司深圳东部支行','中国农业银行股份有限公司深圳东部支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(810,1,'BK_国家开发银行深圳市分行','国家开发银行深圳市分行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(811,1,'BK_兴业银行股份有限公司烟台分行','兴业银行股份有限公司烟台分行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(812,1,'BK_烟台农村商业银行芝罘区大疃支行','烟台农村商业银行芝罘区大疃支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(813,1,'BK_中国工商银行股份有限公司烟台幸福支行','中国工商银行股份有限公司烟台幸福支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(814,1,'BK_浙商银行股份有限公司烟台分行','浙商银行股份有限公司烟台分行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(815,1,'BK_宁波银行股份有限公司深圳财富港支行','宁波银行股份有限公司深圳财富港支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(816,1,'BK_平安银行股份有限公司烟台分行','平安银行股份有限公司烟台分行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(817,1,'BK_昆仑银行总行营业部','昆仑银行总行营业部','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(818,1,'BK_中信银行总行营业部','中信银行总行营业部','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(819,1,'BK_交通银行深圳华融支行','交通银行深圳华融支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(820,1,'BK_中国农业银行股份有限公司深圳东部支行结算户','中国农业银行股份有限公司深圳东部支行(结算户）','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(900,1,'BK_银行','银行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(901,1,'BK_中国银行龙口开发区支行','中国银行龙口开发区支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(902,1,'BK_招商银行烟台分行龙口支行','招商银行烟台分行龙口支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(903,1,'BK_招商银行幸福路支行','招商银行幸福路支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(904,1,'BK_示例集团财务有限公司','示例集团财务有限公司','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(905,1,'BK_光大银行龙口支行','光大银行龙口支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(906,1,'BK_建行龙口支行','建行龙口支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(907,1,'BK_招商银行股份有限公司烟台幸福路支行','招商银行股份有限公司烟台幸福路支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(908,1,'BK_中国光大银行烟台分行营业部','中国光大银行烟台分行营业部','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(909,1,'BK_中国农业银行股份有限公司烟台芝罘支行','中国农业银行股份有限公司烟台芝罘支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(910,1,'BK_中国建设银行股份有限公司烟台分行','中国建设银行股份有限公司烟台分行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(911,1,'BK_中国银行股份有限公司烟台经济技术开发区支行','中国银行股份有限公司烟台经济技术开发区支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(912,1,'BK_交通银行烟台分行经济技术开发区支行','交通银行烟台分行经济技术开发区支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(913,1,'BK_中国农业银行青岛市市南区第三支行','中国农业银行青岛市市南区第三支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(914,1,'BK_中国农业银行股份有限公司深圳东部支行','中国农业银行股份有限公司深圳东部支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(915,1,'BK_国家开发银行深圳市分行','国家开发银行深圳市分行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(916,1,'BK_兴业银行股份有限公司烟台分行','兴业银行股份有限公司烟台分行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(917,1,'BK_烟台农村商业银行芝罘区大疃支行','烟台农村商业银行芝罘区大疃支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(918,1,'BK_中国工商银行股份有限公司烟台幸福支行','中国工商银行股份有限公司烟台幸福支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(919,1,'BK_浙商银行股份有限公司烟台分行','浙商银行股份有限公司烟台分行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(920,1,'BK_宁波银行股份有限公司深圳财富港支行','宁波银行股份有限公司深圳财富港支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(921,1,'BK_平安银行股份有限公司烟台分行','平安银行股份有限公司烟台分行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(922,1,'BK_昆仑银行总行营业部','昆仑银行总行营业部','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(923,1,'BK_中信银行总行营业部','中信银行总行营业部','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(924,1,'BK_交通银行深圳华融支行','交通银行深圳华融支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(925,1,'BK_恒丰银行股份有限公司海阳支行','恒丰银行股份有限公司海阳支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(926,1,'BK_烟台银行股份有限公司海阳支行','烟台银行股份有限公司海阳支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(927,1,'BK_中国农业银行股份有限公司海阳凤城分理处','中国农业银行股份有限公司海阳凤城分理处','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(928,1,'BK_中国银行股份有限公司海阳凤城支行','中国银行股份有限公司海阳凤城支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(929,1,'BK_中国银行股份有限公司海阳营业部','中国银行股份有限公司海阳营业部','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(930,1,'BK_交通银行股份有限公司青岛四方支行','交通银行股份有限公司青岛四方支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(931,1,'BK_交通银行股份有限公司烟台开发区支行','交通银行股份有限公司烟台开发区支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(932,1,'BK_中国建设银行股份有限公司海阳支行','中国建设银行股份有限公司海阳支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(933,1,'BK_示例集团财务有限公司','示例集团财务有限公司','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(934,1,'BK_招商银行烟台分行南大街支行','招商银行烟台分行南大街支行','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(935,1,'BK_中国建设银行烟台分行清莱路分理处','中国建设银行烟台分行清莱路分理处','BANK','NORMAL',NULL,NULL,NULL,NULL,NULL,0),(936,1,'BK_中国银行烟台莱山支行','中国银行烟台莱山支行','BANK','NORMAL',NULL,NULL,NULL,3897,'2026-10-07 23:39:18',0),(937,1,'W13PROBE-A#937','波次十三探针-正向新增','BANK','NORMAL','w13-codeleg-positive',3897,'2026-10-07 23:39:18',3897,'2026-10-07 23:39:18',1),(938,1,'W13PROBE-A','波次十三探针-同码重建','BANK','NORMAL','w13-codeleg-recreate',3897,'2026-10-07 23:39:18',3897,'2026-10-07 23:39:18',0),(939,1,'W16BANKA01#939','波次十六留痕取证行A-改','BANK','NORMAL','w13-codeleg-recreate',3897,'2026-10-08 03:43:14',3897,'2026-10-08 03:43:14',1);
/*!40000 ALTER TABLE `md_bank` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-14 18:29:07
