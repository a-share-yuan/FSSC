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
-- Table structure for table `md_budget_category`
--

DROP TABLE IF EXISTS `md_budget_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_budget_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `category_code` varchar(64) NOT NULL,
  `category_name` varchar(128) NOT NULL,
  `sort` int DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  `special_type` varchar(32) DEFAULT NULL COMMENT '专项类型 RESEARCH科研/ASSET资产运营/EXPENSE费用专项',
  `eps` varchar(32) DEFAULT NULL COMMENT 'EPS 专项费用/资产运营',
  `is_special` varchar(8) DEFAULT 'N' COMMENT '是否专项费用 Y/N',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `uk_bcat` (`tenant_id`,`category_code`)
) ENGINE=InnoDB AUTO_INCREMENT=237 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预算类别';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `md_budget_category`
--

LOCK TABLES `md_budget_category` WRITE;
/*!40000 ALTER TABLE `md_budget_category` DISABLE KEYS */;
INSERT INTO `md_budget_category` VALUES (152,1,'ZX999','非专项',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','N'),(153,1,'21-KYZX001','科研项目申报专项-YCRO',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(154,1,'21-KYZX004','极地冰区半潜式钻井平台关键技术研发及产业化',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(155,1,'21-KYZX005','第七代超深水钻井平台专项',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(156,1,'21-KYZX012','海工装备虚拟现实培训研发中心',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(157,1,'21-KYZX013','平台振动与噪声控制技术研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(158,1,'21-KYZX014','模块化建造和总装建造技术研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(159,1,'21-KYZX015','科技部海洋工程装备重点领域技术标准研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(160,1,'21-KYZX017','合金钢主弦管激光复合制造工艺研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(161,1,'21-KYZX019','山东省关键核心技术知识产权品牌建设',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(162,1,'21-KYZX020','多功能钻探船总体方案技术研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(163,1,'21-KYZX021','邮轮美学设计技术研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(164,1,'21-KYZX022','工信部超大型海上油田设施一体化拆解装备关键技术研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(165,1,'21-KYZX024','海洋工程装备试验检测平台建设-',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(166,1,'21-KYZX026','南海岛礁综合开发方案及关键技术研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(167,1,'21-KYZX027','水下焊接维修机器人装备研制及关键技术研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(168,1,'21-KYZX028','海工装备及高技术船舶用激光电弧复合焊接关键技术研究及示范应用',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(169,1,'21-KYZX030','海上边际油田移动式采储修一体化平台研制及产业化',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(170,1,'21-KYZX031','国家科学技术进步奖项目',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(171,1,'21-KYZX032','国家新材料生产应用示范平台（第二批）',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(172,1,'21-KYZX033','海洋平台运动和载荷子系统工业用户应用',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(173,1,'21-KYZX034','海洋工程智能化管加工平台研发及应用',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(174,1,'21-KYZX035','网络协同制造和智能工厂专项——集团企业研发设计资源集成管理与共享平台开发及应用',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(175,1,'21-KYZX036','PLNG大型模块设计技术研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(176,1,'21-KYZX037','天然气水合物钻采船工程开发',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(177,1,'21-QTZX001','（停用）H233运营费用专项',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(178,1,'21-QTZX002','（停用）H233销售费用专项',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(179,1,'21-QTZX003','（停用）YINSON MARLIM 1&2 FPSO改造项目前期设计',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(180,1,'21-QTZX004','（停用）SS Amazonia监理服务项目',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(181,1,'21-QTZX006','（停用）BT董事会',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(182,1,'41-QTZX001','（停用）蓝鲸一号代理服务费',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(183,1,'41-QTZX002','（停用）蓝鲸二号代理服务费',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(184,1,'43-KYZX001','科研项目申报专项-OEI',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(185,1,'43-KYZX005','极地冰区半潜式钻井平台项目',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(186,1,'43-KYZX006','3000米超深水半潜平台项目',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(187,1,'43-KYZX012','海工云服务平台建设项目',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(188,1,'43-KYZX013','基于海洋高端装备的大数据与智能制造技术服务平台',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(189,1,'43-KYZX014','水下焊接维修机器人装备研制及关键技术研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(190,1,'43-KYZX015','海工装备及高技术船舶用激光电弧复合焊接关键技术研究及示范应用',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(191,1,'43-KYZX016','水合物试采、环境监测及综合评价应用示范',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(192,1,'43-KYZX018','南海岛礁综合开发方案及关键技术研究(OEI)',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(193,1,'43-KYZX021','海上实验改造',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(194,1,'43-KYZX022','烟台市专利导航产业发展项目',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(195,1,'43-KYZX023','国家国际科技合作基地',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(196,1,'43-KYZX024','潮流能转换流场特性及系统耦合动力学理论与试验研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(197,1,'43-KYZX025','海洋波浪能可再生能源开发装备关键技术',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(198,1,'43-KYZX026','基于虚拟现实技术的智能海工基地建设',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(199,1,'43-KYZX027','深水半潜式支持平台',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(200,1,'43-KYZX028','海洋工程装备等离子-电弧复合焊接技术',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(201,1,'43-KYZX029','海洋可再生能源利用创新团队',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(202,1,'43-KYZX030','高服役安全高强韧海洋工程用钢焊接组织可视化及焊接关键技术研究',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(203,1,'43-KYZX032','铜铝金属复合材料焊接技术合作研发',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(204,1,'43-KYZX033','适于中国海域的漂浮内转子式波浪能发电装备研发',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'RESEARCH','专项费用','Y'),(205,1,'43-KYZX034','海洋平台振动噪声虚拟现实智能快速建模技术',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(206,1,'FYZX001','（停用）安全生产专项--劳动防护',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(207,1,'FYZX002','（停用）安全生产专项--现场安全',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(208,1,'FYZX003','（停用）安全生产专项--安全生产培训',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(209,1,'FYZX004','（停用）安全生产专项--安全生产宣传',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(210,1,'FYZX005','（停用）安全生产专项--评价检测费用',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(211,1,'FYZX006','（停用）安全生产专项--检测仪器维护',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(212,1,'FYZX007','（停用）安全生产专项--ONE精益管理',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(213,1,'FYZX008','（停用）安全生产专项--应急救援+器材维护',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(214,1,'FYZX009','（停用）安全生产专项--安全设施、特种设备检测',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(215,1,'FYZX010','（停用）安全生产专项--其他',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(216,1,'FYZX011','（停用）节能环保专项',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(217,1,'ZX000','（停用）历史专项',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'EXPENSE','专项费用','Y'),(218,1,'资产-43-KYZX031','半潜式平台系统运行数据及海况数据的认知方法和分析模型',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(219,1,'资产-H190','H190 JEBSEN',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(220,1,'资产-H194','H194 守卫者号',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(221,1,'资产-H195','H195 Dragon Oil',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(222,1,'资产-H206','H206 SSCV 1#',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(223,1,'资产-H218','H218 SSCV 2#',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(224,1,'资产-H233','H233 Yacht',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(225,1,'资产-H270','H270 GM4-D 1#',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','项目建造','Y'),(226,1,'资产-H273','H273 JU2000E',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(227,1,'资产-H277','H277 蓝鲸1#',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(228,1,'资产-H278','H278 蓝鲸2号',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(229,1,'资产-H284','H284 JU2000E',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(230,1,'资产-H285','H285 JU2000E',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(231,1,'资产-H293','H293 Super M2-9#',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(232,1,'资产-H297','H297 GM4-D 2#',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(233,1,'资产-H300','H300 Super M2',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','项目建造','Y'),(234,1,'资产-H305','H305 GM4-D 3#',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(235,1,'资产-H307','H307 CR600',0,'NORMAL',NULL,NULL,NULL,NULL,NULL,0,'ASSET','资产运营','Y'),(236,1,'W16BC01#236','波次十六留痕取证预算类别-改',0,'NORMAL',NULL,3837,'2026-10-08 04:03:43',3837,'2026-10-08 04:03:43',1,'RESEARCH','专项费用','N');
/*!40000 ALTER TABLE `md_budget_category` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:30:38
