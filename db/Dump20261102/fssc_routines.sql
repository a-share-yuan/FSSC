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
-- Temporary view structure for view `v_tax_input_certify`
--

DROP TABLE IF EXISTS `v_tax_input_certify`;
/*!50001 DROP VIEW IF EXISTS `v_tax_input_certify`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_tax_input_certify` AS SELECT 
 1 AS `id`,
 1 AS `tenant_id`,
 1 AS `company_id`,
 1 AS `invoice_id`,
 1 AS `invoice_no`,
 1 AS `invoice_code`,
 1 AS `invoice_type`,
 1 AS `supplier_name`,
 1 AS `supplier_tax_no`,
 1 AS `invoice_date`,
 1 AS `invoice_amount`,
 1 AS `tax_amount`,
 1 AS `total_amount`,
 1 AS `certify_status`,
 1 AS `certify_date`,
 1 AS `certify_period`,
 1 AS `deduct_period`,
 1 AS `deduct_amount`,
 1 AS `reverse_date`*/;
SET character_set_client = @saved_cs_client;

--
-- Final view structure for view `v_tax_input_certify`
--

/*!50001 DROP VIEW IF EXISTS `v_tax_input_certify`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `v_tax_input_certify` AS select `i`.`id` AS `id`,`i`.`tenant_id` AS `tenant_id`,`i`.`company_id` AS `company_id`,`i`.`id` AS `invoice_id`,`i`.`invoice_no` AS `invoice_no`,`i`.`invoice_code` AS `invoice_code`,`i`.`invoice_kind` AS `invoice_type`,`i`.`seller_name` AS `supplier_name`,`i`.`seller_tax_no` AS `supplier_tax_no`,`i`.`invoice_date` AS `invoice_date`,`i`.`net_amount` AS `invoice_amount`,`i`.`tax_amount` AS `tax_amount`,`i`.`amount` AS `total_amount`,(case `i`.`certify_status` when 'CERTIFIED' then 'CERTIFIED' when 'REVERSED' then 'TRANSFERRED' else 'PENDING' end) AS `certify_status`,cast(`i`.`certify_date` as date) AS `certify_date`,`i`.`certify_period` AS `certify_period`,(case when (`i`.`certify_status` = 'CERTIFIED') then `i`.`certify_period` else NULL end) AS `deduct_period`,`i`.`deduct_amount` AS `deduct_amount`,`i`.`reverse_date` AS `reverse_date` from `inv_invoice` `i` where ((`i`.`direction` = 'INPUT') and (`i`.`del_flag` = 0)) */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 23:30:47
