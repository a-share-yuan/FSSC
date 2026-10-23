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
-- Temporary view structure for view `v_sod3_probe`
--

DROP TABLE IF EXISTS `v_sod3_probe`;
/*!50001 DROP VIEW IF EXISTS `v_sod3_probe`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_sod3_probe` AS SELECT 
 1 AS `uid`,
 1 AS `emp_no`,
 1 AS `nick_name`,
 1 AS `dept_id`,
 1 AS `other_role_cnt`,
 1 AS `audit_role_cnt`,
 1 AS `submit_role_cnt`*/;
SET character_set_client = @saved_cs_client;

--
-- Final view structure for view `v_sod3_probe`
--

/*!50001 DROP VIEW IF EXISTS `v_sod3_probe`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 SQL SECURITY DEFINER */
/*!50001 VIEW `v_sod3_probe` AS select `u`.`id` AS `uid`,`u`.`emp_no` AS `emp_no`,`u`.`nick_name` AS `nick_name`,`u`.`dept_id` AS `dept_id`,(select count(0) from (`sys_user_role` `ur2` join `sys_role` `r2` on((`r2`.`id` = `ur2`.`role_id`))) where ((`ur2`.`user_id` = `u`.`id`) and (`r2`.`role_code` <> 'COMMON_EMP'))) AS `other_role_cnt`,(select count(0) from ((`sys_user_role` `ur2` join `sys_role_menu` `rm2` on((`rm2`.`role_id` = `ur2`.`role_id`))) join `sys_menu` `m2` on((`m2`.`id` = `rm2`.`menu_id`))) where ((`ur2`.`user_id` = `u`.`id`) and (find_in_set('exp:bill:audit',replace(`m2`.`perms`,' ','')) > 0))) AS `audit_role_cnt`,(select count(0) from ((`sys_user_role` `ur2` join `sys_role_menu` `rm2` on((`rm2`.`role_id` = `ur2`.`role_id`))) join `sys_menu` `m2` on((`m2`.`id` = `rm2`.`menu_id`))) where ((`ur2`.`user_id` = `u`.`id`) and (find_in_set('exp:bill:submit',replace(`m2`.`perms`,' ','')) > 0))) AS `submit_role_cnt` from `sys_user` `u` where ((`u`.`del_flag` = 0) and exists(select 1 from (`sys_user_role` `ur3` join `sys_role` `r3` on((`r3`.`id` = `ur3`.`role_id`))) where ((`ur3`.`user_id` = `u`.`id`) and (`r3`.`role_code` = 'COMMON_EMP')))) */;
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

-- Dump completed on 2026-10-23 10:16:02
