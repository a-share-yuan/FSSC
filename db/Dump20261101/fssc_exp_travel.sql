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
-- Table structure for table `exp_travel`
--

DROP TABLE IF EXISTS `exp_travel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exp_travel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `bill_id` bigint NOT NULL COMMENT '单据主表ID',
  `line_no` int NOT NULL DEFAULT '1' COMMENT '行程序号',
  `traveler_id` bigint DEFAULT NULL COMMENT '出差人用户ID',
  `traveler_name` varchar(50) DEFAULT NULL COMMENT '出差人姓名',
  `from_city_id` bigint DEFAULT NULL COMMENT '出发城市ID',
  `to_city_id` bigint DEFAULT NULL COMMENT '到达城市ID',
  `dest_country` varchar(64) DEFAULT NULL COMMENT '目的地国家/地区（非空且≠中国=出国）',
  `begin_date` date DEFAULT NULL COMMENT '出发日期',
  `end_date` date DEFAULT NULL COMMENT '返回日期',
  `days` decimal(8,1) DEFAULT NULL COMMENT '出差天数',
  `round_trip` tinyint(1) DEFAULT NULL COMMENT '是否往返：1=往返 0=单程 NULL=历史未记录',
  `traffic_type` varchar(20) DEFAULT NULL COMMENT '交通工具:PLANE飞机/TRAIN火车/CAR汽车/SHIP轮船/OTHER',
  `traffic_class` varchar(20) DEFAULT NULL COMMENT '舱位等级:ECONOMY经济舱/BUSINESS商务舱/FIRST头等舱/SECOND二等座/FIRST_SEAT一等座',
  `hotel_amount` decimal(20,2) DEFAULT '0.00' COMMENT '住宿费金额',
  `traffic_amount` decimal(20,2) DEFAULT '0.00' COMMENT '交通费金额',
  `allowance_amount` decimal(20,2) DEFAULT '0.00' COMMENT '出差补助金额',
  `other_amount` decimal(20,2) DEFAULT '0.00' COMMENT '其他费用',
  `local_traffic_amount` decimal(20,2) DEFAULT '0.00' COMMENT '市内交通费（D-V4R-33：表单早已有这一栏并计入小计，表无列⇒分量合计小于 total_amount）',
  `total_amount` decimal(20,2) DEFAULT '0.00' COMMENT '行程合计',
  `over_std` tinyint NOT NULL DEFAULT '0' COMMENT '是否超标',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `allowance_taxable` tinyint NOT NULL DEFAULT '0' COMMENT '补贴超标并入个税：1=超标部分需并入工资薪金计税',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_bill` (`tenant_id`,`bill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='差旅行程明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exp_travel`
--

LOCK TABLES `exp_travel` WRITE;
/*!40000 ALTER TABLE `exp_travel` DISABLE KEYS */;
/*!40000 ALTER TABLE `exp_travel` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:06:15
