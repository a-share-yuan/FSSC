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
-- Table structure for table `sys_message`
--

DROP TABLE IF EXISTS `sys_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID【多租户隔离字段】',
  `receiver_id` bigint NOT NULL COMMENT '接收人用户ID',
  `msg_type` varchar(20) NOT NULL DEFAULT 'TODO' COMMENT '类型:TODO待办/DONE已办/NOTICE通知/ALERT预警',
  `title` varchar(200) NOT NULL COMMENT '消息标题',
  `content` varchar(1000) DEFAULT NULL COMMENT '消息内容',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '业务类型,如 EXP_CLAIM',
  `biz_id` bigint DEFAULT NULL COMMENT '业务主键',
  `link_url` varchar(500) DEFAULT NULL COMMENT '跳转地址(PC/H5共用相对路径)',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '是否已读:0否 1是',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_receiver` (`tenant_id`,`receiver_id`,`is_read`),
  KEY `idx_tenant_biz` (`tenant_id`,`biz_type`,`biz_id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内消息/待办提醒';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_message`
--

LOCK TABLES `sys_message` WRITE;
/*!40000 ALTER TABLE `sys_message` DISABLE KEYS */;
INSERT INTO `sys_message` VALUES (1,1,1,'ALERT','定时任务健康告警：RcOverdueJob','任务「RcOverdueJob」异常。op_job_heartbeat 无 job_name=RcOverdueJob 行 ⇒ 大概率 @Scheduled 方法从未被触发（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。。请检查「系统监控 → 定时任务健康」。','APPROVAL',NULL,'/system/job-heartbeat',0,NULL,0,'2026-11-01 16:00:00',0,'2026-11-01 16:00:00',0),(2,1,1,'ALERT','定时任务健康告警：ArchiveExpireJob','任务「ArchiveExpireJob」异常。op_job_heartbeat 无 job_name=ArchiveExpireJob 行 ⇒ 大概率 @Scheduled 方法从未被触发（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。。请检查「系统监控 → 定时任务健康」。','APPROVAL',NULL,'/system/job-heartbeat',0,NULL,0,'2026-11-01 16:00:00',0,'2026-11-01 16:00:00',0),(3,1,1,'ALERT','定时任务健康告警：ReconcileService','任务「ReconcileService」异常。op_job_heartbeat 无 job_name=ReconcileService 行 ⇒ 大概率 @Scheduled 方法从未被触发（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。。请检查「系统监控 → 定时任务健康」。','APPROVAL',NULL,'/system/job-heartbeat',0,NULL,0,'2026-11-01 16:00:00',0,'2026-11-01 16:00:00',0),(4,1,1,'ALERT','定时任务健康告警：RptSummaryService','任务「RptSummaryService」异常。op_job_heartbeat 无 job_name=RptSummaryService 行 ⇒ 大概率 @Scheduled 方法从未被触发（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。。请检查「系统监控 → 定时任务健康」。','APPROVAL',NULL,'/system/job-heartbeat',0,NULL,0,'2026-11-01 16:00:00',0,'2026-11-01 16:00:00',0),(5,1,1,'ALERT','定时任务健康告警：ArchiveService','任务「ArchiveService」异常。op_job_heartbeat 无 job_name=ArchiveService 行 ⇒ 大概率 @Scheduled 方法从未被触发（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。。请检查「系统监控 → 定时任务健康」。','APPROVAL',NULL,'/system/job-heartbeat',0,NULL,0,'2026-11-01 16:00:00',0,'2026-11-01 16:00:00',0),(6,1,1,'ALERT','定时任务健康告警：WfHistoryArchiveService','任务「WfHistoryArchiveService」异常。op_job_heartbeat 无 job_name=WfHistoryArchiveService 行 ⇒ 大概率 @Scheduled 方法从未被触发（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。。请检查「系统监控 → 定时任务健康」。','APPROVAL',NULL,'/system/job-heartbeat',0,NULL,0,'2026-11-01 16:00:00',0,'2026-11-01 16:00:00',0),(7,1,1,'ALERT','定时任务健康告警：StabilityLogJob','任务「StabilityLogJob」异常。op_job_heartbeat 无 job_name=StabilityLogJob 行 ⇒ 大概率 @Scheduled 方法从未被触发（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。。请检查「系统监控 → 定时任务健康」。','APPROVAL',NULL,'/system/job-heartbeat',0,NULL,0,'2026-11-01 16:00:00',0,'2026-11-01 16:00:00',0),(8,1,1,'ALERT','定时任务健康告警：WfInstanceArchiveJob','任务「WfInstanceArchiveJob」异常。op_job_heartbeat 无 job_name=WfInstanceArchiveJob 行 ⇒ 大概率 @Scheduled 方法从未被触发（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。。请检查「系统监控 → 定时任务健康」。','APPROVAL',NULL,'/system/job-heartbeat',0,NULL,0,'2026-11-01 16:00:00',0,'2026-11-01 16:00:00',0),(9,1,1,'ALERT','定时任务健康告警：ColdArchiveJob','任务「ColdArchiveJob」异常。op_job_heartbeat 无 job_name=ColdArchiveJob 行 ⇒ 大概率 @Scheduled 方法从未被触发（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。。请检查「系统监控 → 定时任务健康」。','APPROVAL',NULL,'/system/job-heartbeat',0,NULL,0,'2026-11-01 16:00:00',0,'2026-11-01 16:00:00',0),(10,1,1,'ALERT','定时任务健康告警：FxRevalJob','任务「FxRevalJob」异常。op_job_heartbeat 无 job_name=FxRevalJob 行 ⇒ 大概率 @Scheduled 方法从未被触发（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。。请检查「系统监控 → 定时任务健康」。','APPROVAL',NULL,'/system/job-heartbeat',0,NULL,0,'2026-11-01 16:00:00',0,'2026-11-01 16:00:00',0),(11,1,1,'ALERT','定时任务健康告警：AssetDeprAutoJob','任务「AssetDeprAutoJob」异常。op_job_heartbeat 无 job_name=AssetDeprAutoJob 行 ⇒ 大概率 @Scheduled 方法从未被触发（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。。请检查「系统监控 → 定时任务健康」。','APPROVAL',NULL,'/system/job-heartbeat',0,NULL,0,'2026-11-01 16:00:00',0,'2026-11-01 16:00:00',0),(12,1,1,'ALERT','定时任务健康告警：PoOrderFinalStateJob','任务「PoOrderFinalStateJob」异常。op_job_heartbeat 无 job_name=PoOrderFinalStateJob 行 ⇒ 大概率 @Scheduled 方法从未被触发（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。。请检查「系统监控 → 定时任务健康」。','APPROVAL',NULL,'/system/job-heartbeat',0,NULL,0,'2026-11-01 16:00:00',0,'2026-11-01 16:00:00',0);
/*!40000 ALTER TABLE `sys_message` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 18:05:32
