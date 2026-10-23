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
-- Table structure for table `sys_schema_version`
--

DROP TABLE IF EXISTS `sys_schema_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_schema_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version` varchar(128) NOT NULL COMMENT '迁移版本号（取文件名 V 后到第一个下划线前）',
  `script` varchar(255) NOT NULL COMMENT '脚本相对路径',
  `checksum` varchar(64) DEFAULT NULL COMMENT '脚本 sha256 前 16 位，防同版本号内容被改',
  `applied_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '应用时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schema_version` (`version`)
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据库迁移版本台账（P0-B2）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_schema_version`
--

LOCK TABLES `sys_schema_version` WRITE;
/*!40000 ALTER TABLE `sys_schema_version` DISABLE KEYS */;
INSERT INTO `sys_schema_version` VALUES (1,'1.0.5','db/sql/V1.0.5_p2_gl_entry.sql','f3410815be08eef5','2026-09-10 20:05:24'),(2,'1.0.6','db/sql/V1.0.6_p2_add_missing_subjects.sql','ad621e00162e022a','2026-09-10 20:05:24'),(3,'1.0.7','db/sql/V1.0.7_frontend_gl_pages.sql','28b3bba04f565d4b','2026-09-10 20:05:25'),(4,'1.0.8','db/sql/V1.0.8_frontend_wf_board.sql','c3ee7f0e9dcca902','2026-09-10 20:05:25'),(5,'1.0.9','db/sql/V1.0.9_frontend_p0_p3_boards.sql','9deaf348e2acb9e4','2026-09-10 20:05:25'),(6,'1.1.0','db/sql/V1.1.0_p0p3_board_tables.sql','3048d61ba5f89c63','2026-09-10 20:05:25'),(7,'1.1.1','db/sql/V1.1.1_p7_asset_batch_depr.sql','65bedc3632da04f2','2026-09-10 20:05:26'),(8,'1.1.2','db/sql/V1.1.2_p4_otc_receivable_flow.sql','184f8fa57e5b8853','2026-09-10 20:05:26'),(9,'1.1.3','db/sql/V1.1.3_p2_batch_bpmn.sql','d554fc13ece25e13','2026-09-10 20:05:26'),(10,'1.1.4','db/sql/V1.1.4_p2_batch_bpmn_full.sql','5b1d4beb96c06f99','2026-09-10 20:05:27'),(11,'1.1.5','db/sql/V1.1.5_g1_gl_special.sql','c935da8b50024622','2026-09-10 20:05:27'),(12,'1.1.6','db/sql/V1.1.6_gap_otc05_glmerge.sql','0746a4a14936f3c1','2026-09-10 20:05:27'),(13,'1.1.7','db/sql/V1.1.7_m1_wf_bizid_nullable.sql','c25d2a3d70aa188e','2026-09-10 20:05:27'),(14,'1.1.8','db/sql/V1.1.8_m1_fm_tables.sql','2dcbaa69418e84d7','2026-09-10 20:05:28'),(15,'1.1.11','db/sql/V1.1.11_patch_approval_chain.sql','ed6b4f088a314d55','2026-09-10 20:05:28'),(16,'1.1.13','db/sql/V1.1.13_p2_tr01_split.sql','14d396a18375e3f8','2026-09-10 20:05:28'),(17,'1.1.14','db/sql/V1.1.14_p2_gl67_gl8_role.sql','f43c786c1ef65f73','2026-09-10 20:05:28'),(18,'1.1.15','db/sql/V1.1.15_p2_cleanup_tables.sql','d3a396ec83cbfcea','2026-09-10 20:05:29'),(19,'1.1.16','db/sql/V1.1.16_p2_rc_receipt_line.sql','0d6cab3f11efb111','2026-09-10 20:05:29'),(20,'1.1.17','db/sql/V1.1.17_p0_amount_cn.sql','3e6730b55ca1c26b','2026-09-10 20:05:29'),(21,'1.1.18','db/sql/V1.1.18_p1_opt.sql','5060c784d0af05e9','2026-09-10 20:05:30'),(22,'1.1.19','db/sql/V1.1.19_frontend_p1_pages.sql','d655146682186c44','2026-09-10 20:05:30'),(24,'1.1.20','db/sql/V1.1.20_p1_bank_recon_menu.sql','967c78e666c99eb3','2026-09-10 20:05:30'),(25,'1.1.21','db/sql/V1.1.21_p1_inv_extra.sql','f3d48a1d24493be9','2026-09-10 20:05:31'),(26,'1.1.22','db/sql/V1.1.22_p1_bank_balance.sql','63316305fceeb946','2026-09-10 20:05:31'),(27,'1.1.23','db/sql/V1.1.23_p2_tax_refund.sql','e5b08ad05e4ada97','2026-09-10 20:05:31'),(28,'1.1.24','db/sql/V1.1.24_p2_bank_recon_item.sql','cf7a380b69fff17e','2026-09-10 20:05:32'),(29,'1.2.1','db/sql/V1.2.1_p5_tr_flows.sql','1051558130fd53e0','2026-09-10 20:05:32'),(30,'1.2.2','db/sql/V1.2.2_p6_gl_flows.sql','4f22f5db38086578','2026-09-10 20:05:32'),(31,'1.2.3','db/sql/V1.2.3_p6_gl_tables.sql','2c9a8b8ccf83069b','2026-09-10 20:05:32'),(32,'1.2.4','db/sql/V1.2.4_frontend_glex_special.sql','238dc10aa6e35545','2026-09-10 20:05:33'),(33,'1.2.5','db/sql/V1.2.5_frontend_tax_refund.sql','484faf3eff7af48e','2026-09-10 20:05:33'),(34,'1.2.6','db/sql/V1.2.6_p2_cross_org_approve.sql','5754e394b5155bb9','2026-09-10 20:05:33'),(35,'1.2.7','db/sql/V1.2.7_p3_reconcile_tax_asset.sql','21b0493d83a5c800','2026-09-10 20:05:34'),(36,'1.2.8','db/sql/V1.2.8_archive_attachment_table.sql','96512228bbfd43cc','2026-09-10 20:05:34'),(37,'1.2.9','db/sql/V1.2.9_demo_approver_roles.sql','dec6e9cc8c0395a2','2026-09-10 20:05:34'),(38,'1.2.10','db/sql/V1.2.10_p2_exp_bill_loan_company.sql','c67c40e5b15d8bf9','2026-09-10 20:05:34'),(39,'1.3.0','db/sql/V1.3.0_recon_ent_in.sql','02d815bee662f184','2026-09-10 20:05:35'),(40,'1.3.1','db/sql/V1.3.1_inv_global_index.sql','84cd9aba63083688','2026-09-10 20:05:35'),(41,'1.3.2','db/sql/V1.3.2_feature_wiring.sql','c0169681e820fa43','2026-09-10 20:05:35'),(42,'1.3.3','db/sql/V1.3.3_sod_job_heartbeat.sql','ff336eb8c6a1534a','2026-09-10 20:05:35'),(43,'1.3.4','db/sql/V1.3.4_invoice_code_notnull.sql','b2d6ef95aaf61e31','2026-09-10 20:05:36'),(44,'1.3.5','db/sql/V1.3.5_split_order_detect_rule.sql','f7216ab3e46d29fe','2026-09-10 20:05:36'),(45,'1.3.6','db/sql/V1.3.6_cold_archive.sql','3ce5b097d5797de9','2026-09-10 20:05:36'),(46,'1.3.7','db/sql/V1.3.7_ocr_channel_config.sql','5c608c9929a80746','2026-09-10 20:05:36'),(47,'1.3.8','db/sql/V1.3.8_ocr_config_menu.sql','2b6de8bdabfdd349','2026-09-10 20:05:37'),(48,'1.3.9','db/sql/V1.3.9_exchange_rate_seed.sql','4e72c7bdfe8bd5c2','2026-09-10 20:05:37'),(49,'1.3.10','db/sql/V1.3.10_abroad_standard.sql','3d75b125ee8a78cf','2026-09-10 20:05:37'),(50,'1.3.11','db/sql/V1.3.11_ocr_pdf_ofd.sql','c21188e5baa6cb03','2026-09-10 20:05:38'),(51,'1.3.12','db/sql/V1.3.12_ocr_xml.sql','65746892f3282a99','2026-09-10 20:05:38'),(52,'1.3.13','db/sql/V1.3.13_fx_revaluation.sql','bad4bafb33a94094','2026-09-10 20:05:38'),(53,'1.3.14','db/sql/V1.3.14_overseas_voucher.sql','d5a498d5787f0c3c','2026-09-10 20:05:38'),(54,'1.3.15','db/sql/V1.3.15_fx_reval_subjects.sql','278b36a5353ddba4','2026-09-10 20:05:39'),(55,'1.3.16','db/sql/V1.3.16_role_org_scope.sql','a3a0db645525a61e','2026-09-10 20:05:39'),(56,'1.3.17','db/sql/V1.3.17_multicompany_4domains.sql','1f2f392c4dfa18ef','2026-09-10 20:05:39'),(57,'1.3.18','db/sql/V1.3.18_org_guard_gl.sql','926265ad72b14959','2026-09-10 20:05:40'),(58,'1.3.19','db/sql/V1.3.19_sub_type_routing.sql','6de3ba4b2bde6da6','2026-09-10 20:05:40'),(59,'1.3.20','db/sql/V1.3.20_security_hardening.sql','a1f12d3f9cc9a8f8','2026-09-10 20:05:40'),(60,'1.3.21','db/sql/V1.3.21_ar_recon_record.sql','9f893888a5a15617','2026-09-10 20:05:40'),(61,'1.3.22','db/sql/V1.3.22_gl_std_subjects.sql','41ad39ea14891285','2026-09-10 20:05:41'),(62,'1.3.23','db/sql/V1.3.23_gl_tax_refund_flow.sql','90f7d70565efbec1','2026-09-10 20:05:41'),(63,'1.4.0','db/sql/V1.4.0_p1_fa_depr.sql','b76faa1b279c2897','2026-09-10 20:05:41'),(65,'1.4.1','db/sql/V1.4.1_p1_highprio_rebuild.sql','b65f259ab521873f','2026-09-10 20:05:42'),(66,'1.4.2','db/sql/V1.4.2_p1_b_level_rebuild.sql','b18607f0e0d7cf6f','2026-09-10 20:05:42'),(67,'1.4.3','db/sql/V1.4.3_p1_role_naming_tighten.sql','91191cabcc5c7807','2026-09-10 20:05:42'),(68,'1.4.4','db/sql/V1.4.4_p1_pay_plan.sql','a08395764001b2fc','2026-09-10 20:05:43'),(69,'1.4.5','db/sql/V1.4.5_p1_board_roles.sql','a5ada4012c3c67fe','2026-09-10 20:05:43'),(70,'1.4.6','db/sql/V1.4.6_p1_menu_topdir_featurecode_fix.sql','a7311f9d616431f8','2026-09-10 20:05:43'),(71,'1.4.7','db/sql/V1.4.7_p2_review_rule_index.sql','a143cf5a34897fbb','2026-09-10 20:05:43'),(72,'1.4.8','db/sql/V1.4.8_p1_query_variant.sql','a8fdca6a505147c1','2026-09-10 20:05:44'),(75,'1.0.0','sql/V1.0.0_init_setup.sql','faae3bd8afdae126','2026-09-10 20:05:44'),(77,'1.0.1','sql/V1.0.1_data_migration.sql','cbd1e0c71ed1dec6','2026-09-10 20:05:45'),(78,'1.0.2','sql/V1.0.2_p0_compliance_rule.sql','6cbbe6c1978d4010','2026-09-10 20:05:45'),(79,'1.0.3','sql/V1.0.3_p0_exp_bill_columns.sql','037518d5fe31efa9','2026-09-10 20:05:46'),(80,'1.0.4','sql/V1.0.4_p0_physical_delivery.sql','fe66965748dc36ca','2026-09-10 20:05:46'),(98,'9.9.9','sql/V9.9.9_cleanup_garbage_data.sql','10ff95f3ba0cb819','2026-09-10 20:05:51'),(99,'9.9.10','sql/V9.9.10_cleanup_demo_seed.sql','00ae3f7e634206ad','2026-09-10 20:05:51'),(100,'9.9.11','sql/V9.9.11_cleanup_demo_seed_revised.sql','62f5894bb8d4544e','2026-09-10 20:05:51'),(101,'1.4.11','db/sql/V1.4.11_wf_node_timeout_sla.sql',NULL,'2026-09-12 09:21:45'),(102,'1.4.12','db/sql/V1.4.12_connector_idempotency_trace.sql',NULL,'2026-10-14 06:54:02');
/*!40000 ALTER TABLE `sys_schema_version` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 10:15:46
