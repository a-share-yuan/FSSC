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
-- Table structure for table `md_invoice_rule`
--

DROP TABLE IF EXISTS `md_invoice_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_invoice_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `keyword` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品/服务名称关键词或词串（多个用 / 分隔，如：打车/出租/滴滴）',
  `match_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CONTAINS' COMMENT '匹配方式: CONTAINS包含/EXACT精确',
  `expense_item_id` bigint DEFAULT NULL COMMENT '命中后预填的费用类型 id (md_expense_item.id)',
  `item_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '费用类型名称（冗余展示）',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序：越小越优先',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL启用/DISABLED停用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_inv_rule_sort` (`tenant_id`,`status`,`sort`)
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发票智能匹配规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_invoice_rule`
--

LOCK TABLES `md_invoice_rule` WRITE;
/*!40000 ALTER TABLE `md_invoice_rule` DISABLE KEYS */;
INSERT INTO `md_invoice_rule` VALUES (1,1,'住宿/酒店/房费','CONTAINS',403,'国内出差-住宿费',1,'NORMAL','绑定到 差旅费-国内出差-住宿费',NULL,'2026-09-11 17:13:14',NULL,'2026-09-11 17:58:12',0),(2,1,'打车/出租/滴滴','CONTAINS',405,'国内出差-市内交通费',2,'NORMAL','绑定到 差旅费-国内出差-市内交通费',NULL,'2026-09-11 17:13:14',1,'2026-09-11 17:58:12',0),(3,1,'高铁/火车/动车','CONTAINS',401,'国内出差-长途交通费',3,'NORMAL','绑定到 差旅费-国内出差-长途交通费',NULL,'2026-09-11 17:13:14',NULL,'2026-09-11 17:58:12',0),(4,1,'顺丰快递/快递','CONTAINS',392,'快递费',4,'NORMAL','绑定到 行政办公费-快递费',NULL,'2026-09-11 17:13:14',NULL,'2026-09-11 17:58:12',0),(5,1,'加班餐补/工作餐','CONTAINS',330,'职工福利',9,'NORMAL','页面功能验证-临时规则',1,'2026-09-11 17:41:29',1,'2026-09-11 17:41:48',1),(7,1,'测试专用商品','EXACT',401,'国内出差-长途交通费',99,'NORMAL','验证解除绑定+停用',1,'2026-09-11 18:13:21',1,'2026-09-11 18:14:01',1),(8,1,'机票/航空/电子客票/行程单','CONTAINS',401,'国内出差-长途交通费',10,'NORMAL','差旅费-国内长途交通',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(9,1,'退票/退改签','CONTAINS',402,'国内出差-退改签费',11,'NORMAL','差旅费-退改签',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(10,1,'地铁/公交/轨道交通','CONTAINS',405,'国内出差-市内交通费',12,'NORMAL','差旅费-市内交通',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(11,1,'停车/泊车','CONTAINS',405,'国内出差-市内交通费',13,'NORMAL','差旅费-市内交通(停车)',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(12,1,'过路费/过桥费/高速通行/ETC','CONTAINS',405,'国内出差-市内交通费',14,'NORMAL','差旅费-市内交通(过路过桥)',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(13,1,'加油/汽油/燃油','CONTAINS',405,'国内出差-市内交通费',15,'NORMAL','差旅费-市内交通(油费)',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(14,1,'火车票/铁路','CONTAINS',401,'国内出差-长途交通费',16,'NORMAL','差旅费-国内长途交通(铁路)',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(15,1,'宴请/招待/餐饮服务','CONTAINS',418,'部门招待费',20,'NORMAL','招待费-部门招待',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(16,1,'团建/聚餐','CONTAINS',369,'团队建设',21,'NORMAL','职工福利-团队建设',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(17,1,'食堂/员工餐','CONTAINS',364,'员工餐费',22,'NORMAL','职工福利-员工餐费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(18,1,'外卖/美团/饿了么/餐饮/餐费','CONTAINS',364,'员工餐费',23,'NORMAL','职工福利-员工餐费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(19,1,'话费/充值/电话','CONTAINS',398,'手机话费',30,'NORMAL','电话费-手机话费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(20,1,'宽带/网络/专线','CONTAINS',400,'网络专线',31,'NORMAL','电话费-网络专线',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(21,1,'固话/座机','CONTAINS',399,'固定话费',32,'NORMAL','电话费-固定话费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(22,1,'办公用品/文具/打印纸/硒鼓/墨盒','CONTAINS',397,'办公文具费',40,'NORMAL','行政办公费-办公文具',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(23,1,'打印/复印/印刷','CONTAINS',384,'部门打印费',41,'NORMAL','行政办公费-部门打印',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(24,1,'图书/书报/杂志/资料','CONTAINS',397,'办公文具费',42,'NORMAL','行政办公费-书报资料',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(25,1,'会议/会务/会场','CONTAINS',393,'会议费',43,'NORMAL','行政办公费-会议费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(26,1,'会员/入会','CONTAINS',394,'会员费',44,'NORMAL','行政办公费-会员费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(27,1,'宣传/广告/推广','CONTAINS',395,'业务宣传费',45,'NORMAL','行政办公费-业务宣传',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(28,1,'体检','CONTAINS',380,'职工体检',50,'NORMAL','劳动保护费-职工体检',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(29,1,'劳保/工作服/手套/安全帽','CONTAINS',376,'劳保用品',51,'NORMAL','劳动保护费-劳保用品',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(30,1,'防暑/降温/清凉','CONTAINS',377,'防暑防寒用品',52,'NORMAL','劳动保护费-防暑防寒',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(31,1,'培训/教育/课程/继续教育','CONTAINS',372,'培训费',53,'NORMAL','职工福利-培训费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(32,1,'生日/蛋糕','CONTAINS',366,'生日餐',54,'NORMAL','职工福利-生日餐',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(33,1,'探亲','CONTAINS',367,'探亲路费',55,'NORMAL','职工福利-探亲路费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(34,1,'药费/医药/挂号/门诊','CONTAINS',379,'体检及医药费-其他类型',56,'NORMAL','劳动保护费-体检医药',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(35,1,'设备租赁/机械租赁','CONTAINS',422,'生产设备租赁费',60,'NORMAL','租赁费-生产设备',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(36,1,'车辆租赁/汽车租赁/运输设备','CONTAINS',421,'运输设备租赁费',61,'NORMAL','租赁费-运输设备',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(37,1,'场地/厂房/办公室租赁/房租','CONTAINS',423,'场地租赁费',62,'NORMAL','租赁费-场地租赁',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(38,1,'住房补贴','CONTAINS',368,'住房补贴',63,'NORMAL','职工福利-住房补贴',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(39,1,'租赁费','CONTAINS',424,'其他租赁费',64,'NORMAL','租赁费-其他（兜底）',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(40,1,'维修/修理/保养/检修','CONTAINS',342,'修理费',70,'NORMAL','修理费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(41,1,'咨询/顾问/中介','CONTAINS',443,'咨询服务费-二级',71,'NORMAL','咨询服务费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(42,1,'物业/物业管理','CONTAINS',444,'物业管理费',72,'NORMAL','物业卫生费-物业管理',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(43,1,'保洁/清洁/垃圾','CONTAINS',445,'卫生费',73,'NORMAL','物业卫生费-卫生费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(44,1,'法律/律师/法务','CONTAINS',474,'法律顾问费',74,'NORMAL','法律事务费-法律顾问',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(45,1,'诉讼','CONTAINS',473,'法律诉讼费',75,'NORMAL','法律事务费-诉讼',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(46,1,'手续费','CONTAINS',478,'手续费',76,'NORMAL','财务费用-手续费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(47,1,'利息','CONTAINS',476,'利息支出',77,'NORMAL','财务费用-利息支出',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(48,1,'保险/保费','CONTAINS',339,'保险费',78,'NORMAL','保险费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(49,1,'软件/系统/云服务/订阅','CONTAINS',493,'软件维护费',79,'NORMAL','其它费用-软件维护',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(50,1,'招聘/猎头/人才','CONTAINS',495,'招聘费',80,'NORMAL','其它费用-招聘费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(51,1,'运输/货运/物流','CONTAINS',496,'运输费',81,'NORMAL','其它费用-运输费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(52,1,'水电','CONTAINS',538,'水电费',82,'NORMAL','其他业务支出-水电费',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(53,1,'罚款/罚金','CONTAINS',481,'罚款',83,'NORMAL','营业外支出-罚款',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(54,1,'捐赠/捐款','CONTAINS',482,'捐赠',84,'NORMAL','营业外支出-捐赠',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(55,1,'代理费','CONTAINS',441,'代理费',85,'NORMAL','咨询服务费-代理',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(56,1,'评审费','CONTAINS',442,'评审费',86,'NORMAL','咨询服务费-评审',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(57,1,'设计费','CONTAINS',446,'设计费',87,'NORMAL','研发费用化支出-设计',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(58,1,'检测/检验/测试','CONTAINS',501,'检验费',88,'NORMAL','检验计量费-检验',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0),(59,1,'计量/检定','CONTAINS',500,'计量仪器检定费',89,'NORMAL','检验计量费-计量检定',NULL,'2026-09-11 18:59:40',NULL,'2026-09-11 18:59:40',0);
/*!40000 ALTER TABLE `md_invoice_rule` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-02  8:14:06
