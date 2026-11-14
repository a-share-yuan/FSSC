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
-- Temporary view structure for view `act_ru_identitylink`
--

DROP TABLE IF EXISTS `act_ru_identitylink`;
/*!50001 DROP VIEW IF EXISTS `act_ru_identitylink`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_identitylink` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `GROUP_ID_`,
 1 AS `TYPE_`,
 1 AS `USER_ID_`,
 1 AS `TASK_ID_`,
 1 AS `PROC_INST_ID_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `SCOPE_DEFINITION_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_re_procdef`
--

DROP TABLE IF EXISTS `act_re_procdef`;
/*!50001 DROP VIEW IF EXISTS `act_re_procdef`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_re_procdef` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `CATEGORY_`,
 1 AS `NAME_`,
 1 AS `KEY_`,
 1 AS `VERSION_`,
 1 AS `DEPLOYMENT_ID_`,
 1 AS `RESOURCE_NAME_`,
 1 AS `DGRM_RESOURCE_NAME_`,
 1 AS `DESCRIPTION_`,
 1 AS `HAS_START_FORM_KEY_`,
 1 AS `HAS_GRAPHICAL_NOTATION_`,
 1 AS `SUSPENSION_STATE_`,
 1 AS `TENANT_ID_`,
 1 AS `ENGINE_VERSION_`,
 1 AS `DERIVED_FROM_`,
 1 AS `DERIVED_FROM_ROOT_`,
 1 AS `DERIVED_VERSION_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_id_bytearray`
--

DROP TABLE IF EXISTS `act_id_bytearray`;
/*!50001 DROP VIEW IF EXISTS `act_id_bytearray`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_id_bytearray` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `NAME_`,
 1 AS `BYTES_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_id_group`
--

DROP TABLE IF EXISTS `act_id_group`;
/*!50001 DROP VIEW IF EXISTS `act_id_group`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_id_group` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `NAME_`,
 1 AS `TYPE_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_hi_identitylink`
--

DROP TABLE IF EXISTS `act_hi_identitylink`;
/*!50001 DROP VIEW IF EXISTS `act_hi_identitylink`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_hi_identitylink` AS SELECT 
 1 AS `ID_`,
 1 AS `GROUP_ID_`,
 1 AS `TYPE_`,
 1 AS `USER_ID_`,
 1 AS `TASK_ID_`,
 1 AS `CREATE_TIME_`,
 1 AS `PROC_INST_ID_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `SCOPE_DEFINITION_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ru_timer_job`
--

DROP TABLE IF EXISTS `act_ru_timer_job`;
/*!50001 DROP VIEW IF EXISTS `act_ru_timer_job`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_timer_job` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `CATEGORY_`,
 1 AS `TYPE_`,
 1 AS `LOCK_EXP_TIME_`,
 1 AS `LOCK_OWNER_`,
 1 AS `EXCLUSIVE_`,
 1 AS `EXECUTION_ID_`,
 1 AS `PROCESS_INSTANCE_ID_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `ELEMENT_ID_`,
 1 AS `ELEMENT_NAME_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `SCOPE_DEFINITION_ID_`,
 1 AS `CORRELATION_ID_`,
 1 AS `RETRIES_`,
 1 AS `EXCEPTION_STACK_ID_`,
 1 AS `EXCEPTION_MSG_`,
 1 AS `DUEDATE_`,
 1 AS `REPEAT_`,
 1 AS `HANDLER_TYPE_`,
 1 AS `HANDLER_CFG_`,
 1 AS `CUSTOM_VALUES_ID_`,
 1 AS `CREATE_TIME_`,
 1 AS `TENANT_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_re_deployment`
--

DROP TABLE IF EXISTS `act_re_deployment`;
/*!50001 DROP VIEW IF EXISTS `act_re_deployment`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_re_deployment` AS SELECT 
 1 AS `ID_`,
 1 AS `NAME_`,
 1 AS `CATEGORY_`,
 1 AS `KEY_`,
 1 AS `TENANT_ID_`,
 1 AS `DEPLOY_TIME_`,
 1 AS `DERIVED_FROM_`,
 1 AS `DERIVED_FROM_ROOT_`,
 1 AS `PARENT_DEPLOYMENT_ID_`,
 1 AS `ENGINE_VERSION_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ru_task`
--

DROP TABLE IF EXISTS `act_ru_task`;
/*!50001 DROP VIEW IF EXISTS `act_ru_task`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_task` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `EXECUTION_ID_`,
 1 AS `PROC_INST_ID_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `TASK_DEF_ID_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `SCOPE_DEFINITION_ID_`,
 1 AS `PROPAGATED_STAGE_INST_ID_`,
 1 AS `STATE_`,
 1 AS `NAME_`,
 1 AS `PARENT_TASK_ID_`,
 1 AS `DESCRIPTION_`,
 1 AS `TASK_DEF_KEY_`,
 1 AS `OWNER_`,
 1 AS `ASSIGNEE_`,
 1 AS `DELEGATION_`,
 1 AS `PRIORITY_`,
 1 AS `CREATE_TIME_`,
 1 AS `IN_PROGRESS_TIME_`,
 1 AS `IN_PROGRESS_STARTED_BY_`,
 1 AS `CLAIM_TIME_`,
 1 AS `CLAIMED_BY_`,
 1 AS `SUSPENDED_TIME_`,
 1 AS `SUSPENDED_BY_`,
 1 AS `IN_PROGRESS_DUE_DATE_`,
 1 AS `DUE_DATE_`,
 1 AS `CATEGORY_`,
 1 AS `SUSPENSION_STATE_`,
 1 AS `TENANT_ID_`,
 1 AS `FORM_KEY_`,
 1 AS `IS_COUNT_ENABLED_`,
 1 AS `VAR_COUNT_`,
 1 AS `ID_LINK_COUNT_`,
 1 AS `SUB_TASK_COUNT_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_id_priv_mapping`
--

DROP TABLE IF EXISTS `act_id_priv_mapping`;
/*!50001 DROP VIEW IF EXISTS `act_id_priv_mapping`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_id_priv_mapping` AS SELECT 
 1 AS `ID_`,
 1 AS `PRIV_ID_`,
 1 AS `USER_ID_`,
 1 AS `GROUP_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ru_variable`
--

DROP TABLE IF EXISTS `act_ru_variable`;
/*!50001 DROP VIEW IF EXISTS `act_ru_variable`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_variable` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `TYPE_`,
 1 AS `NAME_`,
 1 AS `EXECUTION_ID_`,
 1 AS `PROC_INST_ID_`,
 1 AS `TASK_ID_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `BYTEARRAY_ID_`,
 1 AS `DOUBLE_`,
 1 AS `LONG_`,
 1 AS `TEXT_`,
 1 AS `TEXT2_`,
 1 AS `META_INFO_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_procdef_info`
--

DROP TABLE IF EXISTS `act_procdef_info`;
/*!50001 DROP VIEW IF EXISTS `act_procdef_info`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_procdef_info` AS SELECT 
 1 AS `ID_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `REV_`,
 1 AS `INFO_JSON_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `flw_channel_definition`
--

DROP TABLE IF EXISTS `flw_channel_definition`;
/*!50001 DROP VIEW IF EXISTS `flw_channel_definition`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `flw_channel_definition` AS SELECT 
 1 AS `ID_`,
 1 AS `NAME_`,
 1 AS `VERSION_`,
 1 AS `KEY_`,
 1 AS `CATEGORY_`,
 1 AS `TYPE_`,
 1 AS `IMPLEMENTATION_`,
 1 AS `DEPLOYMENT_ID_`,
 1 AS `CREATE_TIME_`,
 1 AS `TENANT_ID_`,
 1 AS `RESOURCE_NAME_`,
 1 AS `DESCRIPTION_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `flw_event_resource`
--

DROP TABLE IF EXISTS `flw_event_resource`;
/*!50001 DROP VIEW IF EXISTS `flw_event_resource`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `flw_event_resource` AS SELECT 
 1 AS `ID_`,
 1 AS `NAME_`,
 1 AS `DEPLOYMENT_ID_`,
 1 AS `RESOURCE_BYTES_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ru_execution`
--

DROP TABLE IF EXISTS `act_ru_execution`;
/*!50001 DROP VIEW IF EXISTS `act_ru_execution`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_execution` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `PROC_INST_ID_`,
 1 AS `BUSINESS_KEY_`,
 1 AS `PARENT_ID_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `SUPER_EXEC_`,
 1 AS `ROOT_PROC_INST_ID_`,
 1 AS `ACT_ID_`,
 1 AS `IS_ACTIVE_`,
 1 AS `IS_CONCURRENT_`,
 1 AS `IS_SCOPE_`,
 1 AS `IS_EVENT_SCOPE_`,
 1 AS `IS_MI_ROOT_`,
 1 AS `SUSPENSION_STATE_`,
 1 AS `CACHED_ENT_STATE_`,
 1 AS `TENANT_ID_`,
 1 AS `NAME_`,
 1 AS `START_ACT_ID_`,
 1 AS `START_TIME_`,
 1 AS `START_USER_ID_`,
 1 AS `LOCK_TIME_`,
 1 AS `LOCK_OWNER_`,
 1 AS `IS_COUNT_ENABLED_`,
 1 AS `EVT_SUBSCR_COUNT_`,
 1 AS `TASK_COUNT_`,
 1 AS `JOB_COUNT_`,
 1 AS `TIMER_JOB_COUNT_`,
 1 AS `SUSP_JOB_COUNT_`,
 1 AS `DEADLETTER_JOB_COUNT_`,
 1 AS `EXTERNAL_WORKER_JOB_COUNT_`,
 1 AS `VAR_COUNT_`,
 1 AS `ID_LINK_COUNT_`,
 1 AS `CALLBACK_ID_`,
 1 AS `CALLBACK_TYPE_`,
 1 AS `REFERENCE_ID_`,
 1 AS `REFERENCE_TYPE_`,
 1 AS `PROPAGATED_STAGE_INST_ID_`,
 1 AS `BUSINESS_STATUS_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ge_bytearray`
--

DROP TABLE IF EXISTS `act_ge_bytearray`;
/*!50001 DROP VIEW IF EXISTS `act_ge_bytearray`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ge_bytearray` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `NAME_`,
 1 AS `DEPLOYMENT_ID_`,
 1 AS `BYTES_`,
 1 AS `GENERATED_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_id_info`
--

DROP TABLE IF EXISTS `act_id_info`;
/*!50001 DROP VIEW IF EXISTS `act_id_info`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_id_info` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `USER_ID_`,
 1 AS `TYPE_`,
 1 AS `KEY_`,
 1 AS `VALUE_`,
 1 AS `PASSWORD_`,
 1 AS `PARENT_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ru_job`
--

DROP TABLE IF EXISTS `act_ru_job`;
/*!50001 DROP VIEW IF EXISTS `act_ru_job`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_job` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `CATEGORY_`,
 1 AS `TYPE_`,
 1 AS `LOCK_EXP_TIME_`,
 1 AS `LOCK_OWNER_`,
 1 AS `EXCLUSIVE_`,
 1 AS `EXECUTION_ID_`,
 1 AS `PROCESS_INSTANCE_ID_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `ELEMENT_ID_`,
 1 AS `ELEMENT_NAME_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `SCOPE_DEFINITION_ID_`,
 1 AS `CORRELATION_ID_`,
 1 AS `RETRIES_`,
 1 AS `EXCEPTION_STACK_ID_`,
 1 AS `EXCEPTION_MSG_`,
 1 AS `DUEDATE_`,
 1 AS `REPEAT_`,
 1 AS `HANDLER_TYPE_`,
 1 AS `HANDLER_CFG_`,
 1 AS `CUSTOM_VALUES_ID_`,
 1 AS `CREATE_TIME_`,
 1 AS `TENANT_ID_`*/;
SET character_set_client = @saved_cs_client;

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
-- Temporary view structure for view `act_hi_tsk_log`
--

DROP TABLE IF EXISTS `act_hi_tsk_log`;
/*!50001 DROP VIEW IF EXISTS `act_hi_tsk_log`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_hi_tsk_log` AS SELECT 
 1 AS `ID_`,
 1 AS `TYPE_`,
 1 AS `TASK_ID_`,
 1 AS `TIME_STAMP_`,
 1 AS `USER_ID_`,
 1 AS `DATA_`,
 1 AS `EXECUTION_ID_`,
 1 AS `PROC_INST_ID_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `SCOPE_ID_`,
 1 AS `SCOPE_DEFINITION_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `TENANT_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_hi_detail`
--

DROP TABLE IF EXISTS `act_hi_detail`;
/*!50001 DROP VIEW IF EXISTS `act_hi_detail`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_hi_detail` AS SELECT 
 1 AS `ID_`,
 1 AS `TYPE_`,
 1 AS `PROC_INST_ID_`,
 1 AS `EXECUTION_ID_`,
 1 AS `TASK_ID_`,
 1 AS `ACT_INST_ID_`,
 1 AS `NAME_`,
 1 AS `VAR_TYPE_`,
 1 AS `REV_`,
 1 AS `TIME_`,
 1 AS `BYTEARRAY_ID_`,
 1 AS `DOUBLE_`,
 1 AS `LONG_`,
 1 AS `TEXT_`,
 1 AS `TEXT2_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_re_model`
--

DROP TABLE IF EXISTS `act_re_model`;
/*!50001 DROP VIEW IF EXISTS `act_re_model`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_re_model` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `NAME_`,
 1 AS `KEY_`,
 1 AS `CATEGORY_`,
 1 AS `CREATE_TIME_`,
 1 AS `LAST_UPDATE_TIME_`,
 1 AS `VERSION_`,
 1 AS `META_INFO_`,
 1 AS `DEPLOYMENT_ID_`,
 1 AS `EDITOR_SOURCE_VALUE_ID_`,
 1 AS `EDITOR_SOURCE_EXTRA_VALUE_ID_`,
 1 AS `TENANT_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_id_priv`
--

DROP TABLE IF EXISTS `act_id_priv`;
/*!50001 DROP VIEW IF EXISTS `act_id_priv`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_id_priv` AS SELECT 
 1 AS `ID_`,
 1 AS `NAME_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_hi_comment`
--

DROP TABLE IF EXISTS `act_hi_comment`;
/*!50001 DROP VIEW IF EXISTS `act_hi_comment`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_hi_comment` AS SELECT 
 1 AS `ID_`,
 1 AS `TYPE_`,
 1 AS `TIME_`,
 1 AS `USER_ID_`,
 1 AS `TASK_ID_`,
 1 AS `PROC_INST_ID_`,
 1 AS `ACTION_`,
 1 AS `MESSAGE_`,
 1 AS `FULL_MSG_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ru_suspended_job`
--

DROP TABLE IF EXISTS `act_ru_suspended_job`;
/*!50001 DROP VIEW IF EXISTS `act_ru_suspended_job`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_suspended_job` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `CATEGORY_`,
 1 AS `TYPE_`,
 1 AS `EXCLUSIVE_`,
 1 AS `EXECUTION_ID_`,
 1 AS `PROCESS_INSTANCE_ID_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `ELEMENT_ID_`,
 1 AS `ELEMENT_NAME_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `SCOPE_DEFINITION_ID_`,
 1 AS `CORRELATION_ID_`,
 1 AS `RETRIES_`,
 1 AS `EXCEPTION_STACK_ID_`,
 1 AS `EXCEPTION_MSG_`,
 1 AS `DUEDATE_`,
 1 AS `REPEAT_`,
 1 AS `HANDLER_TYPE_`,
 1 AS `HANDLER_CFG_`,
 1 AS `CUSTOM_VALUES_ID_`,
 1 AS `CREATE_TIME_`,
 1 AS `TENANT_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_id_property`
--

DROP TABLE IF EXISTS `act_id_property`;
/*!50001 DROP VIEW IF EXISTS `act_id_property`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_id_property` AS SELECT 
 1 AS `NAME_`,
 1 AS `VALUE_`,
 1 AS `REV_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `flw_event_deployment`
--

DROP TABLE IF EXISTS `flw_event_deployment`;
/*!50001 DROP VIEW IF EXISTS `flw_event_deployment`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `flw_event_deployment` AS SELECT 
 1 AS `ID_`,
 1 AS `NAME_`,
 1 AS `CATEGORY_`,
 1 AS `DEPLOY_TIME_`,
 1 AS `TENANT_ID_`,
 1 AS `PARENT_DEPLOYMENT_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ru_deadletter_job`
--

DROP TABLE IF EXISTS `act_ru_deadletter_job`;
/*!50001 DROP VIEW IF EXISTS `act_ru_deadletter_job`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_deadletter_job` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `CATEGORY_`,
 1 AS `TYPE_`,
 1 AS `EXCLUSIVE_`,
 1 AS `EXECUTION_ID_`,
 1 AS `PROCESS_INSTANCE_ID_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `ELEMENT_ID_`,
 1 AS `ELEMENT_NAME_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `SCOPE_DEFINITION_ID_`,
 1 AS `CORRELATION_ID_`,
 1 AS `EXCEPTION_STACK_ID_`,
 1 AS `EXCEPTION_MSG_`,
 1 AS `DUEDATE_`,
 1 AS `REPEAT_`,
 1 AS `HANDLER_TYPE_`,
 1 AS `HANDLER_CFG_`,
 1 AS `CUSTOM_VALUES_ID_`,
 1 AS `CREATE_TIME_`,
 1 AS `TENANT_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ru_external_job`
--

DROP TABLE IF EXISTS `act_ru_external_job`;
/*!50001 DROP VIEW IF EXISTS `act_ru_external_job`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_external_job` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `CATEGORY_`,
 1 AS `TYPE_`,
 1 AS `LOCK_EXP_TIME_`,
 1 AS `LOCK_OWNER_`,
 1 AS `EXCLUSIVE_`,
 1 AS `EXECUTION_ID_`,
 1 AS `PROCESS_INSTANCE_ID_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `ELEMENT_ID_`,
 1 AS `ELEMENT_NAME_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `SCOPE_DEFINITION_ID_`,
 1 AS `CORRELATION_ID_`,
 1 AS `RETRIES_`,
 1 AS `EXCEPTION_STACK_ID_`,
 1 AS `EXCEPTION_MSG_`,
 1 AS `DUEDATE_`,
 1 AS `REPEAT_`,
 1 AS `HANDLER_TYPE_`,
 1 AS `HANDLER_CFG_`,
 1 AS `CUSTOM_VALUES_ID_`,
 1 AS `CREATE_TIME_`,
 1 AS `TENANT_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_id_token`
--

DROP TABLE IF EXISTS `act_id_token`;
/*!50001 DROP VIEW IF EXISTS `act_id_token`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_id_token` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `TOKEN_VALUE_`,
 1 AS `TOKEN_DATE_`,
 1 AS `IP_ADDRESS_`,
 1 AS `USER_AGENT_`,
 1 AS `USER_ID_`,
 1 AS `TOKEN_DATA_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_id_membership`
--

DROP TABLE IF EXISTS `act_id_membership`;
/*!50001 DROP VIEW IF EXISTS `act_id_membership`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_id_membership` AS SELECT 
 1 AS `USER_ID_`,
 1 AS `GROUP_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_hi_taskinst`
--

DROP TABLE IF EXISTS `act_hi_taskinst`;
/*!50001 DROP VIEW IF EXISTS `act_hi_taskinst`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_hi_taskinst` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `TASK_DEF_ID_`,
 1 AS `TASK_DEF_KEY_`,
 1 AS `PROC_INST_ID_`,
 1 AS `EXECUTION_ID_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `SCOPE_DEFINITION_ID_`,
 1 AS `PROPAGATED_STAGE_INST_ID_`,
 1 AS `STATE_`,
 1 AS `NAME_`,
 1 AS `PARENT_TASK_ID_`,
 1 AS `DESCRIPTION_`,
 1 AS `OWNER_`,
 1 AS `ASSIGNEE_`,
 1 AS `START_TIME_`,
 1 AS `IN_PROGRESS_TIME_`,
 1 AS `IN_PROGRESS_STARTED_BY_`,
 1 AS `CLAIM_TIME_`,
 1 AS `CLAIMED_BY_`,
 1 AS `SUSPENDED_TIME_`,
 1 AS `SUSPENDED_BY_`,
 1 AS `END_TIME_`,
 1 AS `COMPLETED_BY_`,
 1 AS `DURATION_`,
 1 AS `DELETE_REASON_`,
 1 AS `PRIORITY_`,
 1 AS `IN_PROGRESS_DUE_DATE_`,
 1 AS `DUE_DATE_`,
 1 AS `FORM_KEY_`,
 1 AS `CATEGORY_`,
 1 AS `TENANT_ID_`,
 1 AS `LAST_UPDATED_TIME_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ru_actinst`
--

DROP TABLE IF EXISTS `act_ru_actinst`;
/*!50001 DROP VIEW IF EXISTS `act_ru_actinst`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_actinst` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `PROC_INST_ID_`,
 1 AS `EXECUTION_ID_`,
 1 AS `ACT_ID_`,
 1 AS `TASK_ID_`,
 1 AS `CALL_PROC_INST_ID_`,
 1 AS `ACT_NAME_`,
 1 AS `ACT_TYPE_`,
 1 AS `ASSIGNEE_`,
 1 AS `START_TIME_`,
 1 AS `END_TIME_`,
 1 AS `DURATION_`,
 1 AS `TRANSACTION_ORDER_`,
 1 AS `DELETE_REASON_`,
 1 AS `TENANT_ID_`,
 1 AS `COMPLETED_BY_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ge_property`
--

DROP TABLE IF EXISTS `act_ge_property`;
/*!50001 DROP VIEW IF EXISTS `act_ge_property`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ge_property` AS SELECT 
 1 AS `NAME_`,
 1 AS `VALUE_`,
 1 AS `REV_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_hi_varinst`
--

DROP TABLE IF EXISTS `act_hi_varinst`;
/*!50001 DROP VIEW IF EXISTS `act_hi_varinst`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_hi_varinst` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `PROC_INST_ID_`,
 1 AS `EXECUTION_ID_`,
 1 AS `TASK_ID_`,
 1 AS `NAME_`,
 1 AS `VAR_TYPE_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `BYTEARRAY_ID_`,
 1 AS `DOUBLE_`,
 1 AS `LONG_`,
 1 AS `TEXT_`,
 1 AS `TEXT2_`,
 1 AS `META_INFO_`,
 1 AS `CREATE_TIME_`,
 1 AS `LAST_UPDATED_TIME_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_evt_log`
--

DROP TABLE IF EXISTS `act_evt_log`;
/*!50001 DROP VIEW IF EXISTS `act_evt_log`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_evt_log` AS SELECT 
 1 AS `LOG_NR_`,
 1 AS `TYPE_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `PROC_INST_ID_`,
 1 AS `EXECUTION_ID_`,
 1 AS `TASK_ID_`,
 1 AS `TIME_STAMP_`,
 1 AS `USER_ID_`,
 1 AS `DATA_`,
 1 AS `LOCK_OWNER_`,
 1 AS `LOCK_TIME_`,
 1 AS `IS_PROCESSED_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_hi_actinst`
--

DROP TABLE IF EXISTS `act_hi_actinst`;
/*!50001 DROP VIEW IF EXISTS `act_hi_actinst`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_hi_actinst` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `PROC_INST_ID_`,
 1 AS `EXECUTION_ID_`,
 1 AS `ACT_ID_`,
 1 AS `TASK_ID_`,
 1 AS `CALL_PROC_INST_ID_`,
 1 AS `ACT_NAME_`,
 1 AS `ACT_TYPE_`,
 1 AS `ASSIGNEE_`,
 1 AS `START_TIME_`,
 1 AS `END_TIME_`,
 1 AS `TRANSACTION_ORDER_`,
 1 AS `DURATION_`,
 1 AS `DELETE_REASON_`,
 1 AS `TENANT_ID_`,
 1 AS `COMPLETED_BY_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `flw_event_definition`
--

DROP TABLE IF EXISTS `flw_event_definition`;
/*!50001 DROP VIEW IF EXISTS `flw_event_definition`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `flw_event_definition` AS SELECT 
 1 AS `ID_`,
 1 AS `NAME_`,
 1 AS `VERSION_`,
 1 AS `KEY_`,
 1 AS `CATEGORY_`,
 1 AS `DEPLOYMENT_ID_`,
 1 AS `TENANT_ID_`,
 1 AS `RESOURCE_NAME_`,
 1 AS `DESCRIPTION_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ru_event_subscr`
--

DROP TABLE IF EXISTS `act_ru_event_subscr`;
/*!50001 DROP VIEW IF EXISTS `act_ru_event_subscr`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_event_subscr` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `EVENT_TYPE_`,
 1 AS `EVENT_NAME_`,
 1 AS `EXECUTION_ID_`,
 1 AS `PROC_INST_ID_`,
 1 AS `ACTIVITY_ID_`,
 1 AS `CONFIGURATION_`,
 1 AS `CREATED_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_ID_`,
 1 AS `SCOPE_DEFINITION_ID_`,
 1 AS `SCOPE_DEFINITION_KEY_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `LOCK_TIME_`,
 1 AS `LOCK_OWNER_`,
 1 AS `TENANT_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_hi_procinst`
--

DROP TABLE IF EXISTS `act_hi_procinst`;
/*!50001 DROP VIEW IF EXISTS `act_hi_procinst`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_hi_procinst` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `PROC_INST_ID_`,
 1 AS `BUSINESS_KEY_`,
 1 AS `PROC_DEF_ID_`,
 1 AS `START_TIME_`,
 1 AS `END_TIME_`,
 1 AS `DURATION_`,
 1 AS `START_USER_ID_`,
 1 AS `START_ACT_ID_`,
 1 AS `END_ACT_ID_`,
 1 AS `SUPER_PROCESS_INSTANCE_ID_`,
 1 AS `DELETE_REASON_`,
 1 AS `TENANT_ID_`,
 1 AS `NAME_`,
 1 AS `CALLBACK_ID_`,
 1 AS `CALLBACK_TYPE_`,
 1 AS `REFERENCE_ID_`,
 1 AS `REFERENCE_TYPE_`,
 1 AS `PROPAGATED_STAGE_INST_ID_`,
 1 AS `BUSINESS_STATUS_`,
 1 AS `END_USER_ID_`,
 1 AS `STATE_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ru_history_job`
--

DROP TABLE IF EXISTS `act_ru_history_job`;
/*!50001 DROP VIEW IF EXISTS `act_ru_history_job`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_history_job` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `LOCK_EXP_TIME_`,
 1 AS `LOCK_OWNER_`,
 1 AS `RETRIES_`,
 1 AS `EXCEPTION_STACK_ID_`,
 1 AS `EXCEPTION_MSG_`,
 1 AS `HANDLER_TYPE_`,
 1 AS `HANDLER_CFG_`,
 1 AS `CUSTOM_VALUES_ID_`,
 1 AS `ADV_HANDLER_CFG_ID_`,
 1 AS `CREATE_TIME_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `TENANT_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_id_user`
--

DROP TABLE IF EXISTS `act_id_user`;
/*!50001 DROP VIEW IF EXISTS `act_id_user`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_id_user` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `FIRST_`,
 1 AS `LAST_`,
 1 AS `DISPLAY_NAME_`,
 1 AS `EMAIL_`,
 1 AS `PWD_`,
 1 AS `PICTURE_ID_`,
 1 AS `TENANT_ID_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_hi_entitylink`
--

DROP TABLE IF EXISTS `act_hi_entitylink`;
/*!50001 DROP VIEW IF EXISTS `act_hi_entitylink`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_hi_entitylink` AS SELECT 
 1 AS `ID_`,
 1 AS `LINK_TYPE_`,
 1 AS `CREATE_TIME_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `SCOPE_DEFINITION_ID_`,
 1 AS `PARENT_ELEMENT_ID_`,
 1 AS `REF_SCOPE_ID_`,
 1 AS `REF_SCOPE_TYPE_`,
 1 AS `REF_SCOPE_DEFINITION_ID_`,
 1 AS `ROOT_SCOPE_ID_`,
 1 AS `ROOT_SCOPE_TYPE_`,
 1 AS `HIERARCHY_TYPE_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_hi_attachment`
--

DROP TABLE IF EXISTS `act_hi_attachment`;
/*!50001 DROP VIEW IF EXISTS `act_hi_attachment`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_hi_attachment` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `USER_ID_`,
 1 AS `NAME_`,
 1 AS `DESCRIPTION_`,
 1 AS `TYPE_`,
 1 AS `TASK_ID_`,
 1 AS `PROC_INST_ID_`,
 1 AS `URL_`,
 1 AS `CONTENT_ID_`,
 1 AS `TIME_`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `act_ru_entitylink`
--

DROP TABLE IF EXISTS `act_ru_entitylink`;
/*!50001 DROP VIEW IF EXISTS `act_ru_entitylink`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `act_ru_entitylink` AS SELECT 
 1 AS `ID_`,
 1 AS `REV_`,
 1 AS `CREATE_TIME_`,
 1 AS `LINK_TYPE_`,
 1 AS `SCOPE_ID_`,
 1 AS `SUB_SCOPE_ID_`,
 1 AS `SCOPE_TYPE_`,
 1 AS `SCOPE_DEFINITION_ID_`,
 1 AS `PARENT_ELEMENT_ID_`,
 1 AS `REF_SCOPE_ID_`,
 1 AS `REF_SCOPE_TYPE_`,
 1 AS `REF_SCOPE_DEFINITION_ID_`,
 1 AS `ROOT_SCOPE_ID_`,
 1 AS `ROOT_SCOPE_TYPE_`,
 1 AS `HIERARCHY_TYPE_`*/;
SET character_set_client = @saved_cs_client;

--
-- Final view structure for view `act_ru_identitylink`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_identitylink`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_identitylink` AS select `ACT_RU_IDENTITYLINK`.`ID_` AS `ID_`,`ACT_RU_IDENTITYLINK`.`REV_` AS `REV_`,`ACT_RU_IDENTITYLINK`.`GROUP_ID_` AS `GROUP_ID_`,`ACT_RU_IDENTITYLINK`.`TYPE_` AS `TYPE_`,`ACT_RU_IDENTITYLINK`.`USER_ID_` AS `USER_ID_`,`ACT_RU_IDENTITYLINK`.`TASK_ID_` AS `TASK_ID_`,`ACT_RU_IDENTITYLINK`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_RU_IDENTITYLINK`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_RU_IDENTITYLINK`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_RU_IDENTITYLINK`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_RU_IDENTITYLINK`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_RU_IDENTITYLINK`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_` from `ACT_RU_IDENTITYLINK` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_re_procdef`
--

/*!50001 DROP VIEW IF EXISTS `act_re_procdef`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_re_procdef` AS select `ACT_RE_PROCDEF`.`ID_` AS `ID_`,`ACT_RE_PROCDEF`.`REV_` AS `REV_`,`ACT_RE_PROCDEF`.`CATEGORY_` AS `CATEGORY_`,`ACT_RE_PROCDEF`.`NAME_` AS `NAME_`,`ACT_RE_PROCDEF`.`KEY_` AS `KEY_`,`ACT_RE_PROCDEF`.`VERSION_` AS `VERSION_`,`ACT_RE_PROCDEF`.`DEPLOYMENT_ID_` AS `DEPLOYMENT_ID_`,`ACT_RE_PROCDEF`.`RESOURCE_NAME_` AS `RESOURCE_NAME_`,`ACT_RE_PROCDEF`.`DGRM_RESOURCE_NAME_` AS `DGRM_RESOURCE_NAME_`,`ACT_RE_PROCDEF`.`DESCRIPTION_` AS `DESCRIPTION_`,`ACT_RE_PROCDEF`.`HAS_START_FORM_KEY_` AS `HAS_START_FORM_KEY_`,`ACT_RE_PROCDEF`.`HAS_GRAPHICAL_NOTATION_` AS `HAS_GRAPHICAL_NOTATION_`,`ACT_RE_PROCDEF`.`SUSPENSION_STATE_` AS `SUSPENSION_STATE_`,`ACT_RE_PROCDEF`.`TENANT_ID_` AS `TENANT_ID_`,`ACT_RE_PROCDEF`.`ENGINE_VERSION_` AS `ENGINE_VERSION_`,`ACT_RE_PROCDEF`.`DERIVED_FROM_` AS `DERIVED_FROM_`,`ACT_RE_PROCDEF`.`DERIVED_FROM_ROOT_` AS `DERIVED_FROM_ROOT_`,`ACT_RE_PROCDEF`.`DERIVED_VERSION_` AS `DERIVED_VERSION_` from `ACT_RE_PROCDEF` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_id_bytearray`
--

/*!50001 DROP VIEW IF EXISTS `act_id_bytearray`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_id_bytearray` AS select `ACT_ID_BYTEARRAY`.`ID_` AS `ID_`,`ACT_ID_BYTEARRAY`.`REV_` AS `REV_`,`ACT_ID_BYTEARRAY`.`NAME_` AS `NAME_`,`ACT_ID_BYTEARRAY`.`BYTES_` AS `BYTES_` from `ACT_ID_BYTEARRAY` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_id_group`
--

/*!50001 DROP VIEW IF EXISTS `act_id_group`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_id_group` AS select `ACT_ID_GROUP`.`ID_` AS `ID_`,`ACT_ID_GROUP`.`REV_` AS `REV_`,`ACT_ID_GROUP`.`NAME_` AS `NAME_`,`ACT_ID_GROUP`.`TYPE_` AS `TYPE_` from `ACT_ID_GROUP` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_hi_identitylink`
--

/*!50001 DROP VIEW IF EXISTS `act_hi_identitylink`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_hi_identitylink` AS select `ACT_HI_IDENTITYLINK`.`ID_` AS `ID_`,`ACT_HI_IDENTITYLINK`.`GROUP_ID_` AS `GROUP_ID_`,`ACT_HI_IDENTITYLINK`.`TYPE_` AS `TYPE_`,`ACT_HI_IDENTITYLINK`.`USER_ID_` AS `USER_ID_`,`ACT_HI_IDENTITYLINK`.`TASK_ID_` AS `TASK_ID_`,`ACT_HI_IDENTITYLINK`.`CREATE_TIME_` AS `CREATE_TIME_`,`ACT_HI_IDENTITYLINK`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_HI_IDENTITYLINK`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_HI_IDENTITYLINK`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_HI_IDENTITYLINK`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_HI_IDENTITYLINK`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_` from `ACT_HI_IDENTITYLINK` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ru_timer_job`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_timer_job`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_timer_job` AS select `ACT_RU_TIMER_JOB`.`ID_` AS `ID_`,`ACT_RU_TIMER_JOB`.`REV_` AS `REV_`,`ACT_RU_TIMER_JOB`.`CATEGORY_` AS `CATEGORY_`,`ACT_RU_TIMER_JOB`.`TYPE_` AS `TYPE_`,`ACT_RU_TIMER_JOB`.`LOCK_EXP_TIME_` AS `LOCK_EXP_TIME_`,`ACT_RU_TIMER_JOB`.`LOCK_OWNER_` AS `LOCK_OWNER_`,`ACT_RU_TIMER_JOB`.`EXCLUSIVE_` AS `EXCLUSIVE_`,`ACT_RU_TIMER_JOB`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_RU_TIMER_JOB`.`PROCESS_INSTANCE_ID_` AS `PROCESS_INSTANCE_ID_`,`ACT_RU_TIMER_JOB`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_RU_TIMER_JOB`.`ELEMENT_ID_` AS `ELEMENT_ID_`,`ACT_RU_TIMER_JOB`.`ELEMENT_NAME_` AS `ELEMENT_NAME_`,`ACT_RU_TIMER_JOB`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_RU_TIMER_JOB`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_RU_TIMER_JOB`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_RU_TIMER_JOB`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_`,`ACT_RU_TIMER_JOB`.`CORRELATION_ID_` AS `CORRELATION_ID_`,`ACT_RU_TIMER_JOB`.`RETRIES_` AS `RETRIES_`,`ACT_RU_TIMER_JOB`.`EXCEPTION_STACK_ID_` AS `EXCEPTION_STACK_ID_`,`ACT_RU_TIMER_JOB`.`EXCEPTION_MSG_` AS `EXCEPTION_MSG_`,`ACT_RU_TIMER_JOB`.`DUEDATE_` AS `DUEDATE_`,`ACT_RU_TIMER_JOB`.`REPEAT_` AS `REPEAT_`,`ACT_RU_TIMER_JOB`.`HANDLER_TYPE_` AS `HANDLER_TYPE_`,`ACT_RU_TIMER_JOB`.`HANDLER_CFG_` AS `HANDLER_CFG_`,`ACT_RU_TIMER_JOB`.`CUSTOM_VALUES_ID_` AS `CUSTOM_VALUES_ID_`,`ACT_RU_TIMER_JOB`.`CREATE_TIME_` AS `CREATE_TIME_`,`ACT_RU_TIMER_JOB`.`TENANT_ID_` AS `TENANT_ID_` from `ACT_RU_TIMER_JOB` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_re_deployment`
--

/*!50001 DROP VIEW IF EXISTS `act_re_deployment`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_re_deployment` AS select `ACT_RE_DEPLOYMENT`.`ID_` AS `ID_`,`ACT_RE_DEPLOYMENT`.`NAME_` AS `NAME_`,`ACT_RE_DEPLOYMENT`.`CATEGORY_` AS `CATEGORY_`,`ACT_RE_DEPLOYMENT`.`KEY_` AS `KEY_`,`ACT_RE_DEPLOYMENT`.`TENANT_ID_` AS `TENANT_ID_`,`ACT_RE_DEPLOYMENT`.`DEPLOY_TIME_` AS `DEPLOY_TIME_`,`ACT_RE_DEPLOYMENT`.`DERIVED_FROM_` AS `DERIVED_FROM_`,`ACT_RE_DEPLOYMENT`.`DERIVED_FROM_ROOT_` AS `DERIVED_FROM_ROOT_`,`ACT_RE_DEPLOYMENT`.`PARENT_DEPLOYMENT_ID_` AS `PARENT_DEPLOYMENT_ID_`,`ACT_RE_DEPLOYMENT`.`ENGINE_VERSION_` AS `ENGINE_VERSION_` from `ACT_RE_DEPLOYMENT` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ru_task`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_task`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_task` AS select `ACT_RU_TASK`.`ID_` AS `ID_`,`ACT_RU_TASK`.`REV_` AS `REV_`,`ACT_RU_TASK`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_RU_TASK`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_RU_TASK`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_RU_TASK`.`TASK_DEF_ID_` AS `TASK_DEF_ID_`,`ACT_RU_TASK`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_RU_TASK`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_RU_TASK`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_RU_TASK`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_`,`ACT_RU_TASK`.`PROPAGATED_STAGE_INST_ID_` AS `PROPAGATED_STAGE_INST_ID_`,`ACT_RU_TASK`.`STATE_` AS `STATE_`,`ACT_RU_TASK`.`NAME_` AS `NAME_`,`ACT_RU_TASK`.`PARENT_TASK_ID_` AS `PARENT_TASK_ID_`,`ACT_RU_TASK`.`DESCRIPTION_` AS `DESCRIPTION_`,`ACT_RU_TASK`.`TASK_DEF_KEY_` AS `TASK_DEF_KEY_`,`ACT_RU_TASK`.`OWNER_` AS `OWNER_`,`ACT_RU_TASK`.`ASSIGNEE_` AS `ASSIGNEE_`,`ACT_RU_TASK`.`DELEGATION_` AS `DELEGATION_`,`ACT_RU_TASK`.`PRIORITY_` AS `PRIORITY_`,`ACT_RU_TASK`.`CREATE_TIME_` AS `CREATE_TIME_`,`ACT_RU_TASK`.`IN_PROGRESS_TIME_` AS `IN_PROGRESS_TIME_`,`ACT_RU_TASK`.`IN_PROGRESS_STARTED_BY_` AS `IN_PROGRESS_STARTED_BY_`,`ACT_RU_TASK`.`CLAIM_TIME_` AS `CLAIM_TIME_`,`ACT_RU_TASK`.`CLAIMED_BY_` AS `CLAIMED_BY_`,`ACT_RU_TASK`.`SUSPENDED_TIME_` AS `SUSPENDED_TIME_`,`ACT_RU_TASK`.`SUSPENDED_BY_` AS `SUSPENDED_BY_`,`ACT_RU_TASK`.`IN_PROGRESS_DUE_DATE_` AS `IN_PROGRESS_DUE_DATE_`,`ACT_RU_TASK`.`DUE_DATE_` AS `DUE_DATE_`,`ACT_RU_TASK`.`CATEGORY_` AS `CATEGORY_`,`ACT_RU_TASK`.`SUSPENSION_STATE_` AS `SUSPENSION_STATE_`,`ACT_RU_TASK`.`TENANT_ID_` AS `TENANT_ID_`,`ACT_RU_TASK`.`FORM_KEY_` AS `FORM_KEY_`,`ACT_RU_TASK`.`IS_COUNT_ENABLED_` AS `IS_COUNT_ENABLED_`,`ACT_RU_TASK`.`VAR_COUNT_` AS `VAR_COUNT_`,`ACT_RU_TASK`.`ID_LINK_COUNT_` AS `ID_LINK_COUNT_`,`ACT_RU_TASK`.`SUB_TASK_COUNT_` AS `SUB_TASK_COUNT_` from `ACT_RU_TASK` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_id_priv_mapping`
--

/*!50001 DROP VIEW IF EXISTS `act_id_priv_mapping`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_id_priv_mapping` AS select `ACT_ID_PRIV_MAPPING`.`ID_` AS `ID_`,`ACT_ID_PRIV_MAPPING`.`PRIV_ID_` AS `PRIV_ID_`,`ACT_ID_PRIV_MAPPING`.`USER_ID_` AS `USER_ID_`,`ACT_ID_PRIV_MAPPING`.`GROUP_ID_` AS `GROUP_ID_` from `ACT_ID_PRIV_MAPPING` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ru_variable`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_variable`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_variable` AS select `ACT_RU_VARIABLE`.`ID_` AS `ID_`,`ACT_RU_VARIABLE`.`REV_` AS `REV_`,`ACT_RU_VARIABLE`.`TYPE_` AS `TYPE_`,`ACT_RU_VARIABLE`.`NAME_` AS `NAME_`,`ACT_RU_VARIABLE`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_RU_VARIABLE`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_RU_VARIABLE`.`TASK_ID_` AS `TASK_ID_`,`ACT_RU_VARIABLE`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_RU_VARIABLE`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_RU_VARIABLE`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_RU_VARIABLE`.`BYTEARRAY_ID_` AS `BYTEARRAY_ID_`,`ACT_RU_VARIABLE`.`DOUBLE_` AS `DOUBLE_`,`ACT_RU_VARIABLE`.`LONG_` AS `LONG_`,`ACT_RU_VARIABLE`.`TEXT_` AS `TEXT_`,`ACT_RU_VARIABLE`.`TEXT2_` AS `TEXT2_`,`ACT_RU_VARIABLE`.`META_INFO_` AS `META_INFO_` from `ACT_RU_VARIABLE` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_procdef_info`
--

/*!50001 DROP VIEW IF EXISTS `act_procdef_info`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_procdef_info` AS select `ACT_PROCDEF_INFO`.`ID_` AS `ID_`,`ACT_PROCDEF_INFO`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_PROCDEF_INFO`.`REV_` AS `REV_`,`ACT_PROCDEF_INFO`.`INFO_JSON_ID_` AS `INFO_JSON_ID_` from `ACT_PROCDEF_INFO` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `flw_channel_definition`
--

/*!50001 DROP VIEW IF EXISTS `flw_channel_definition`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `flw_channel_definition` AS select `FLW_CHANNEL_DEFINITION`.`ID_` AS `ID_`,`FLW_CHANNEL_DEFINITION`.`NAME_` AS `NAME_`,`FLW_CHANNEL_DEFINITION`.`VERSION_` AS `VERSION_`,`FLW_CHANNEL_DEFINITION`.`KEY_` AS `KEY_`,`FLW_CHANNEL_DEFINITION`.`CATEGORY_` AS `CATEGORY_`,`FLW_CHANNEL_DEFINITION`.`TYPE_` AS `TYPE_`,`FLW_CHANNEL_DEFINITION`.`IMPLEMENTATION_` AS `IMPLEMENTATION_`,`FLW_CHANNEL_DEFINITION`.`DEPLOYMENT_ID_` AS `DEPLOYMENT_ID_`,`FLW_CHANNEL_DEFINITION`.`CREATE_TIME_` AS `CREATE_TIME_`,`FLW_CHANNEL_DEFINITION`.`TENANT_ID_` AS `TENANT_ID_`,`FLW_CHANNEL_DEFINITION`.`RESOURCE_NAME_` AS `RESOURCE_NAME_`,`FLW_CHANNEL_DEFINITION`.`DESCRIPTION_` AS `DESCRIPTION_` from `FLW_CHANNEL_DEFINITION` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `flw_event_resource`
--

/*!50001 DROP VIEW IF EXISTS `flw_event_resource`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `flw_event_resource` AS select `FLW_EVENT_RESOURCE`.`ID_` AS `ID_`,`FLW_EVENT_RESOURCE`.`NAME_` AS `NAME_`,`FLW_EVENT_RESOURCE`.`DEPLOYMENT_ID_` AS `DEPLOYMENT_ID_`,`FLW_EVENT_RESOURCE`.`RESOURCE_BYTES_` AS `RESOURCE_BYTES_` from `FLW_EVENT_RESOURCE` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ru_execution`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_execution`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_execution` AS select `ACT_RU_EXECUTION`.`ID_` AS `ID_`,`ACT_RU_EXECUTION`.`REV_` AS `REV_`,`ACT_RU_EXECUTION`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_RU_EXECUTION`.`BUSINESS_KEY_` AS `BUSINESS_KEY_`,`ACT_RU_EXECUTION`.`PARENT_ID_` AS `PARENT_ID_`,`ACT_RU_EXECUTION`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_RU_EXECUTION`.`SUPER_EXEC_` AS `SUPER_EXEC_`,`ACT_RU_EXECUTION`.`ROOT_PROC_INST_ID_` AS `ROOT_PROC_INST_ID_`,`ACT_RU_EXECUTION`.`ACT_ID_` AS `ACT_ID_`,`ACT_RU_EXECUTION`.`IS_ACTIVE_` AS `IS_ACTIVE_`,`ACT_RU_EXECUTION`.`IS_CONCURRENT_` AS `IS_CONCURRENT_`,`ACT_RU_EXECUTION`.`IS_SCOPE_` AS `IS_SCOPE_`,`ACT_RU_EXECUTION`.`IS_EVENT_SCOPE_` AS `IS_EVENT_SCOPE_`,`ACT_RU_EXECUTION`.`IS_MI_ROOT_` AS `IS_MI_ROOT_`,`ACT_RU_EXECUTION`.`SUSPENSION_STATE_` AS `SUSPENSION_STATE_`,`ACT_RU_EXECUTION`.`CACHED_ENT_STATE_` AS `CACHED_ENT_STATE_`,`ACT_RU_EXECUTION`.`TENANT_ID_` AS `TENANT_ID_`,`ACT_RU_EXECUTION`.`NAME_` AS `NAME_`,`ACT_RU_EXECUTION`.`START_ACT_ID_` AS `START_ACT_ID_`,`ACT_RU_EXECUTION`.`START_TIME_` AS `START_TIME_`,`ACT_RU_EXECUTION`.`START_USER_ID_` AS `START_USER_ID_`,`ACT_RU_EXECUTION`.`LOCK_TIME_` AS `LOCK_TIME_`,`ACT_RU_EXECUTION`.`LOCK_OWNER_` AS `LOCK_OWNER_`,`ACT_RU_EXECUTION`.`IS_COUNT_ENABLED_` AS `IS_COUNT_ENABLED_`,`ACT_RU_EXECUTION`.`EVT_SUBSCR_COUNT_` AS `EVT_SUBSCR_COUNT_`,`ACT_RU_EXECUTION`.`TASK_COUNT_` AS `TASK_COUNT_`,`ACT_RU_EXECUTION`.`JOB_COUNT_` AS `JOB_COUNT_`,`ACT_RU_EXECUTION`.`TIMER_JOB_COUNT_` AS `TIMER_JOB_COUNT_`,`ACT_RU_EXECUTION`.`SUSP_JOB_COUNT_` AS `SUSP_JOB_COUNT_`,`ACT_RU_EXECUTION`.`DEADLETTER_JOB_COUNT_` AS `DEADLETTER_JOB_COUNT_`,`ACT_RU_EXECUTION`.`EXTERNAL_WORKER_JOB_COUNT_` AS `EXTERNAL_WORKER_JOB_COUNT_`,`ACT_RU_EXECUTION`.`VAR_COUNT_` AS `VAR_COUNT_`,`ACT_RU_EXECUTION`.`ID_LINK_COUNT_` AS `ID_LINK_COUNT_`,`ACT_RU_EXECUTION`.`CALLBACK_ID_` AS `CALLBACK_ID_`,`ACT_RU_EXECUTION`.`CALLBACK_TYPE_` AS `CALLBACK_TYPE_`,`ACT_RU_EXECUTION`.`REFERENCE_ID_` AS `REFERENCE_ID_`,`ACT_RU_EXECUTION`.`REFERENCE_TYPE_` AS `REFERENCE_TYPE_`,`ACT_RU_EXECUTION`.`PROPAGATED_STAGE_INST_ID_` AS `PROPAGATED_STAGE_INST_ID_`,`ACT_RU_EXECUTION`.`BUSINESS_STATUS_` AS `BUSINESS_STATUS_` from `ACT_RU_EXECUTION` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ge_bytearray`
--

/*!50001 DROP VIEW IF EXISTS `act_ge_bytearray`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ge_bytearray` AS select `ACT_GE_BYTEARRAY`.`ID_` AS `ID_`,`ACT_GE_BYTEARRAY`.`REV_` AS `REV_`,`ACT_GE_BYTEARRAY`.`NAME_` AS `NAME_`,`ACT_GE_BYTEARRAY`.`DEPLOYMENT_ID_` AS `DEPLOYMENT_ID_`,`ACT_GE_BYTEARRAY`.`BYTES_` AS `BYTES_`,`ACT_GE_BYTEARRAY`.`GENERATED_` AS `GENERATED_` from `ACT_GE_BYTEARRAY` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_id_info`
--

/*!50001 DROP VIEW IF EXISTS `act_id_info`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_id_info` AS select `ACT_ID_INFO`.`ID_` AS `ID_`,`ACT_ID_INFO`.`REV_` AS `REV_`,`ACT_ID_INFO`.`USER_ID_` AS `USER_ID_`,`ACT_ID_INFO`.`TYPE_` AS `TYPE_`,`ACT_ID_INFO`.`KEY_` AS `KEY_`,`ACT_ID_INFO`.`VALUE_` AS `VALUE_`,`ACT_ID_INFO`.`PASSWORD_` AS `PASSWORD_`,`ACT_ID_INFO`.`PARENT_ID_` AS `PARENT_ID_` from `ACT_ID_INFO` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ru_job`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_job`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_job` AS select `ACT_RU_JOB`.`ID_` AS `ID_`,`ACT_RU_JOB`.`REV_` AS `REV_`,`ACT_RU_JOB`.`CATEGORY_` AS `CATEGORY_`,`ACT_RU_JOB`.`TYPE_` AS `TYPE_`,`ACT_RU_JOB`.`LOCK_EXP_TIME_` AS `LOCK_EXP_TIME_`,`ACT_RU_JOB`.`LOCK_OWNER_` AS `LOCK_OWNER_`,`ACT_RU_JOB`.`EXCLUSIVE_` AS `EXCLUSIVE_`,`ACT_RU_JOB`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_RU_JOB`.`PROCESS_INSTANCE_ID_` AS `PROCESS_INSTANCE_ID_`,`ACT_RU_JOB`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_RU_JOB`.`ELEMENT_ID_` AS `ELEMENT_ID_`,`ACT_RU_JOB`.`ELEMENT_NAME_` AS `ELEMENT_NAME_`,`ACT_RU_JOB`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_RU_JOB`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_RU_JOB`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_RU_JOB`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_`,`ACT_RU_JOB`.`CORRELATION_ID_` AS `CORRELATION_ID_`,`ACT_RU_JOB`.`RETRIES_` AS `RETRIES_`,`ACT_RU_JOB`.`EXCEPTION_STACK_ID_` AS `EXCEPTION_STACK_ID_`,`ACT_RU_JOB`.`EXCEPTION_MSG_` AS `EXCEPTION_MSG_`,`ACT_RU_JOB`.`DUEDATE_` AS `DUEDATE_`,`ACT_RU_JOB`.`REPEAT_` AS `REPEAT_`,`ACT_RU_JOB`.`HANDLER_TYPE_` AS `HANDLER_TYPE_`,`ACT_RU_JOB`.`HANDLER_CFG_` AS `HANDLER_CFG_`,`ACT_RU_JOB`.`CUSTOM_VALUES_ID_` AS `CUSTOM_VALUES_ID_`,`ACT_RU_JOB`.`CREATE_TIME_` AS `CREATE_TIME_`,`ACT_RU_JOB`.`TENANT_ID_` AS `TENANT_ID_` from `ACT_RU_JOB` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

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

--
-- Final view structure for view `act_hi_tsk_log`
--

/*!50001 DROP VIEW IF EXISTS `act_hi_tsk_log`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_hi_tsk_log` AS select `ACT_HI_TSK_LOG`.`ID_` AS `ID_`,`ACT_HI_TSK_LOG`.`TYPE_` AS `TYPE_`,`ACT_HI_TSK_LOG`.`TASK_ID_` AS `TASK_ID_`,`ACT_HI_TSK_LOG`.`TIME_STAMP_` AS `TIME_STAMP_`,`ACT_HI_TSK_LOG`.`USER_ID_` AS `USER_ID_`,`ACT_HI_TSK_LOG`.`DATA_` AS `DATA_`,`ACT_HI_TSK_LOG`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_HI_TSK_LOG`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_HI_TSK_LOG`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_HI_TSK_LOG`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_HI_TSK_LOG`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_`,`ACT_HI_TSK_LOG`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_HI_TSK_LOG`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_HI_TSK_LOG`.`TENANT_ID_` AS `TENANT_ID_` from `ACT_HI_TSK_LOG` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_hi_detail`
--

/*!50001 DROP VIEW IF EXISTS `act_hi_detail`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_hi_detail` AS select `ACT_HI_DETAIL`.`ID_` AS `ID_`,`ACT_HI_DETAIL`.`TYPE_` AS `TYPE_`,`ACT_HI_DETAIL`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_HI_DETAIL`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_HI_DETAIL`.`TASK_ID_` AS `TASK_ID_`,`ACT_HI_DETAIL`.`ACT_INST_ID_` AS `ACT_INST_ID_`,`ACT_HI_DETAIL`.`NAME_` AS `NAME_`,`ACT_HI_DETAIL`.`VAR_TYPE_` AS `VAR_TYPE_`,`ACT_HI_DETAIL`.`REV_` AS `REV_`,`ACT_HI_DETAIL`.`TIME_` AS `TIME_`,`ACT_HI_DETAIL`.`BYTEARRAY_ID_` AS `BYTEARRAY_ID_`,`ACT_HI_DETAIL`.`DOUBLE_` AS `DOUBLE_`,`ACT_HI_DETAIL`.`LONG_` AS `LONG_`,`ACT_HI_DETAIL`.`TEXT_` AS `TEXT_`,`ACT_HI_DETAIL`.`TEXT2_` AS `TEXT2_` from `ACT_HI_DETAIL` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_re_model`
--

/*!50001 DROP VIEW IF EXISTS `act_re_model`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_re_model` AS select `ACT_RE_MODEL`.`ID_` AS `ID_`,`ACT_RE_MODEL`.`REV_` AS `REV_`,`ACT_RE_MODEL`.`NAME_` AS `NAME_`,`ACT_RE_MODEL`.`KEY_` AS `KEY_`,`ACT_RE_MODEL`.`CATEGORY_` AS `CATEGORY_`,`ACT_RE_MODEL`.`CREATE_TIME_` AS `CREATE_TIME_`,`ACT_RE_MODEL`.`LAST_UPDATE_TIME_` AS `LAST_UPDATE_TIME_`,`ACT_RE_MODEL`.`VERSION_` AS `VERSION_`,`ACT_RE_MODEL`.`META_INFO_` AS `META_INFO_`,`ACT_RE_MODEL`.`DEPLOYMENT_ID_` AS `DEPLOYMENT_ID_`,`ACT_RE_MODEL`.`EDITOR_SOURCE_VALUE_ID_` AS `EDITOR_SOURCE_VALUE_ID_`,`ACT_RE_MODEL`.`EDITOR_SOURCE_EXTRA_VALUE_ID_` AS `EDITOR_SOURCE_EXTRA_VALUE_ID_`,`ACT_RE_MODEL`.`TENANT_ID_` AS `TENANT_ID_` from `ACT_RE_MODEL` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_id_priv`
--

/*!50001 DROP VIEW IF EXISTS `act_id_priv`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_id_priv` AS select `ACT_ID_PRIV`.`ID_` AS `ID_`,`ACT_ID_PRIV`.`NAME_` AS `NAME_` from `ACT_ID_PRIV` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_hi_comment`
--

/*!50001 DROP VIEW IF EXISTS `act_hi_comment`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_hi_comment` AS select `ACT_HI_COMMENT`.`ID_` AS `ID_`,`ACT_HI_COMMENT`.`TYPE_` AS `TYPE_`,`ACT_HI_COMMENT`.`TIME_` AS `TIME_`,`ACT_HI_COMMENT`.`USER_ID_` AS `USER_ID_`,`ACT_HI_COMMENT`.`TASK_ID_` AS `TASK_ID_`,`ACT_HI_COMMENT`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_HI_COMMENT`.`ACTION_` AS `ACTION_`,`ACT_HI_COMMENT`.`MESSAGE_` AS `MESSAGE_`,`ACT_HI_COMMENT`.`FULL_MSG_` AS `FULL_MSG_` from `ACT_HI_COMMENT` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ru_suspended_job`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_suspended_job`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_suspended_job` AS select `ACT_RU_SUSPENDED_JOB`.`ID_` AS `ID_`,`ACT_RU_SUSPENDED_JOB`.`REV_` AS `REV_`,`ACT_RU_SUSPENDED_JOB`.`CATEGORY_` AS `CATEGORY_`,`ACT_RU_SUSPENDED_JOB`.`TYPE_` AS `TYPE_`,`ACT_RU_SUSPENDED_JOB`.`EXCLUSIVE_` AS `EXCLUSIVE_`,`ACT_RU_SUSPENDED_JOB`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_RU_SUSPENDED_JOB`.`PROCESS_INSTANCE_ID_` AS `PROCESS_INSTANCE_ID_`,`ACT_RU_SUSPENDED_JOB`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_RU_SUSPENDED_JOB`.`ELEMENT_ID_` AS `ELEMENT_ID_`,`ACT_RU_SUSPENDED_JOB`.`ELEMENT_NAME_` AS `ELEMENT_NAME_`,`ACT_RU_SUSPENDED_JOB`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_RU_SUSPENDED_JOB`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_RU_SUSPENDED_JOB`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_RU_SUSPENDED_JOB`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_`,`ACT_RU_SUSPENDED_JOB`.`CORRELATION_ID_` AS `CORRELATION_ID_`,`ACT_RU_SUSPENDED_JOB`.`RETRIES_` AS `RETRIES_`,`ACT_RU_SUSPENDED_JOB`.`EXCEPTION_STACK_ID_` AS `EXCEPTION_STACK_ID_`,`ACT_RU_SUSPENDED_JOB`.`EXCEPTION_MSG_` AS `EXCEPTION_MSG_`,`ACT_RU_SUSPENDED_JOB`.`DUEDATE_` AS `DUEDATE_`,`ACT_RU_SUSPENDED_JOB`.`REPEAT_` AS `REPEAT_`,`ACT_RU_SUSPENDED_JOB`.`HANDLER_TYPE_` AS `HANDLER_TYPE_`,`ACT_RU_SUSPENDED_JOB`.`HANDLER_CFG_` AS `HANDLER_CFG_`,`ACT_RU_SUSPENDED_JOB`.`CUSTOM_VALUES_ID_` AS `CUSTOM_VALUES_ID_`,`ACT_RU_SUSPENDED_JOB`.`CREATE_TIME_` AS `CREATE_TIME_`,`ACT_RU_SUSPENDED_JOB`.`TENANT_ID_` AS `TENANT_ID_` from `ACT_RU_SUSPENDED_JOB` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_id_property`
--

/*!50001 DROP VIEW IF EXISTS `act_id_property`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_id_property` AS select `ACT_ID_PROPERTY`.`NAME_` AS `NAME_`,`ACT_ID_PROPERTY`.`VALUE_` AS `VALUE_`,`ACT_ID_PROPERTY`.`REV_` AS `REV_` from `ACT_ID_PROPERTY` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `flw_event_deployment`
--

/*!50001 DROP VIEW IF EXISTS `flw_event_deployment`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `flw_event_deployment` AS select `FLW_EVENT_DEPLOYMENT`.`ID_` AS `ID_`,`FLW_EVENT_DEPLOYMENT`.`NAME_` AS `NAME_`,`FLW_EVENT_DEPLOYMENT`.`CATEGORY_` AS `CATEGORY_`,`FLW_EVENT_DEPLOYMENT`.`DEPLOY_TIME_` AS `DEPLOY_TIME_`,`FLW_EVENT_DEPLOYMENT`.`TENANT_ID_` AS `TENANT_ID_`,`FLW_EVENT_DEPLOYMENT`.`PARENT_DEPLOYMENT_ID_` AS `PARENT_DEPLOYMENT_ID_` from `FLW_EVENT_DEPLOYMENT` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ru_deadletter_job`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_deadletter_job`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_deadletter_job` AS select `ACT_RU_DEADLETTER_JOB`.`ID_` AS `ID_`,`ACT_RU_DEADLETTER_JOB`.`REV_` AS `REV_`,`ACT_RU_DEADLETTER_JOB`.`CATEGORY_` AS `CATEGORY_`,`ACT_RU_DEADLETTER_JOB`.`TYPE_` AS `TYPE_`,`ACT_RU_DEADLETTER_JOB`.`EXCLUSIVE_` AS `EXCLUSIVE_`,`ACT_RU_DEADLETTER_JOB`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_RU_DEADLETTER_JOB`.`PROCESS_INSTANCE_ID_` AS `PROCESS_INSTANCE_ID_`,`ACT_RU_DEADLETTER_JOB`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_RU_DEADLETTER_JOB`.`ELEMENT_ID_` AS `ELEMENT_ID_`,`ACT_RU_DEADLETTER_JOB`.`ELEMENT_NAME_` AS `ELEMENT_NAME_`,`ACT_RU_DEADLETTER_JOB`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_RU_DEADLETTER_JOB`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_RU_DEADLETTER_JOB`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_RU_DEADLETTER_JOB`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_`,`ACT_RU_DEADLETTER_JOB`.`CORRELATION_ID_` AS `CORRELATION_ID_`,`ACT_RU_DEADLETTER_JOB`.`EXCEPTION_STACK_ID_` AS `EXCEPTION_STACK_ID_`,`ACT_RU_DEADLETTER_JOB`.`EXCEPTION_MSG_` AS `EXCEPTION_MSG_`,`ACT_RU_DEADLETTER_JOB`.`DUEDATE_` AS `DUEDATE_`,`ACT_RU_DEADLETTER_JOB`.`REPEAT_` AS `REPEAT_`,`ACT_RU_DEADLETTER_JOB`.`HANDLER_TYPE_` AS `HANDLER_TYPE_`,`ACT_RU_DEADLETTER_JOB`.`HANDLER_CFG_` AS `HANDLER_CFG_`,`ACT_RU_DEADLETTER_JOB`.`CUSTOM_VALUES_ID_` AS `CUSTOM_VALUES_ID_`,`ACT_RU_DEADLETTER_JOB`.`CREATE_TIME_` AS `CREATE_TIME_`,`ACT_RU_DEADLETTER_JOB`.`TENANT_ID_` AS `TENANT_ID_` from `ACT_RU_DEADLETTER_JOB` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ru_external_job`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_external_job`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_external_job` AS select `ACT_RU_EXTERNAL_JOB`.`ID_` AS `ID_`,`ACT_RU_EXTERNAL_JOB`.`REV_` AS `REV_`,`ACT_RU_EXTERNAL_JOB`.`CATEGORY_` AS `CATEGORY_`,`ACT_RU_EXTERNAL_JOB`.`TYPE_` AS `TYPE_`,`ACT_RU_EXTERNAL_JOB`.`LOCK_EXP_TIME_` AS `LOCK_EXP_TIME_`,`ACT_RU_EXTERNAL_JOB`.`LOCK_OWNER_` AS `LOCK_OWNER_`,`ACT_RU_EXTERNAL_JOB`.`EXCLUSIVE_` AS `EXCLUSIVE_`,`ACT_RU_EXTERNAL_JOB`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_RU_EXTERNAL_JOB`.`PROCESS_INSTANCE_ID_` AS `PROCESS_INSTANCE_ID_`,`ACT_RU_EXTERNAL_JOB`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_RU_EXTERNAL_JOB`.`ELEMENT_ID_` AS `ELEMENT_ID_`,`ACT_RU_EXTERNAL_JOB`.`ELEMENT_NAME_` AS `ELEMENT_NAME_`,`ACT_RU_EXTERNAL_JOB`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_RU_EXTERNAL_JOB`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_RU_EXTERNAL_JOB`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_RU_EXTERNAL_JOB`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_`,`ACT_RU_EXTERNAL_JOB`.`CORRELATION_ID_` AS `CORRELATION_ID_`,`ACT_RU_EXTERNAL_JOB`.`RETRIES_` AS `RETRIES_`,`ACT_RU_EXTERNAL_JOB`.`EXCEPTION_STACK_ID_` AS `EXCEPTION_STACK_ID_`,`ACT_RU_EXTERNAL_JOB`.`EXCEPTION_MSG_` AS `EXCEPTION_MSG_`,`ACT_RU_EXTERNAL_JOB`.`DUEDATE_` AS `DUEDATE_`,`ACT_RU_EXTERNAL_JOB`.`REPEAT_` AS `REPEAT_`,`ACT_RU_EXTERNAL_JOB`.`HANDLER_TYPE_` AS `HANDLER_TYPE_`,`ACT_RU_EXTERNAL_JOB`.`HANDLER_CFG_` AS `HANDLER_CFG_`,`ACT_RU_EXTERNAL_JOB`.`CUSTOM_VALUES_ID_` AS `CUSTOM_VALUES_ID_`,`ACT_RU_EXTERNAL_JOB`.`CREATE_TIME_` AS `CREATE_TIME_`,`ACT_RU_EXTERNAL_JOB`.`TENANT_ID_` AS `TENANT_ID_` from `ACT_RU_EXTERNAL_JOB` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_id_token`
--

/*!50001 DROP VIEW IF EXISTS `act_id_token`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_id_token` AS select `ACT_ID_TOKEN`.`ID_` AS `ID_`,`ACT_ID_TOKEN`.`REV_` AS `REV_`,`ACT_ID_TOKEN`.`TOKEN_VALUE_` AS `TOKEN_VALUE_`,`ACT_ID_TOKEN`.`TOKEN_DATE_` AS `TOKEN_DATE_`,`ACT_ID_TOKEN`.`IP_ADDRESS_` AS `IP_ADDRESS_`,`ACT_ID_TOKEN`.`USER_AGENT_` AS `USER_AGENT_`,`ACT_ID_TOKEN`.`USER_ID_` AS `USER_ID_`,`ACT_ID_TOKEN`.`TOKEN_DATA_` AS `TOKEN_DATA_` from `ACT_ID_TOKEN` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_id_membership`
--

/*!50001 DROP VIEW IF EXISTS `act_id_membership`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_id_membership` AS select `ACT_ID_MEMBERSHIP`.`USER_ID_` AS `USER_ID_`,`ACT_ID_MEMBERSHIP`.`GROUP_ID_` AS `GROUP_ID_` from `ACT_ID_MEMBERSHIP` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_hi_taskinst`
--

/*!50001 DROP VIEW IF EXISTS `act_hi_taskinst`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_hi_taskinst` AS select `ACT_HI_TASKINST`.`ID_` AS `ID_`,`ACT_HI_TASKINST`.`REV_` AS `REV_`,`ACT_HI_TASKINST`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_HI_TASKINST`.`TASK_DEF_ID_` AS `TASK_DEF_ID_`,`ACT_HI_TASKINST`.`TASK_DEF_KEY_` AS `TASK_DEF_KEY_`,`ACT_HI_TASKINST`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_HI_TASKINST`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_HI_TASKINST`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_HI_TASKINST`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_HI_TASKINST`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_HI_TASKINST`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_`,`ACT_HI_TASKINST`.`PROPAGATED_STAGE_INST_ID_` AS `PROPAGATED_STAGE_INST_ID_`,`ACT_HI_TASKINST`.`STATE_` AS `STATE_`,`ACT_HI_TASKINST`.`NAME_` AS `NAME_`,`ACT_HI_TASKINST`.`PARENT_TASK_ID_` AS `PARENT_TASK_ID_`,`ACT_HI_TASKINST`.`DESCRIPTION_` AS `DESCRIPTION_`,`ACT_HI_TASKINST`.`OWNER_` AS `OWNER_`,`ACT_HI_TASKINST`.`ASSIGNEE_` AS `ASSIGNEE_`,`ACT_HI_TASKINST`.`START_TIME_` AS `START_TIME_`,`ACT_HI_TASKINST`.`IN_PROGRESS_TIME_` AS `IN_PROGRESS_TIME_`,`ACT_HI_TASKINST`.`IN_PROGRESS_STARTED_BY_` AS `IN_PROGRESS_STARTED_BY_`,`ACT_HI_TASKINST`.`CLAIM_TIME_` AS `CLAIM_TIME_`,`ACT_HI_TASKINST`.`CLAIMED_BY_` AS `CLAIMED_BY_`,`ACT_HI_TASKINST`.`SUSPENDED_TIME_` AS `SUSPENDED_TIME_`,`ACT_HI_TASKINST`.`SUSPENDED_BY_` AS `SUSPENDED_BY_`,`ACT_HI_TASKINST`.`END_TIME_` AS `END_TIME_`,`ACT_HI_TASKINST`.`COMPLETED_BY_` AS `COMPLETED_BY_`,`ACT_HI_TASKINST`.`DURATION_` AS `DURATION_`,`ACT_HI_TASKINST`.`DELETE_REASON_` AS `DELETE_REASON_`,`ACT_HI_TASKINST`.`PRIORITY_` AS `PRIORITY_`,`ACT_HI_TASKINST`.`IN_PROGRESS_DUE_DATE_` AS `IN_PROGRESS_DUE_DATE_`,`ACT_HI_TASKINST`.`DUE_DATE_` AS `DUE_DATE_`,`ACT_HI_TASKINST`.`FORM_KEY_` AS `FORM_KEY_`,`ACT_HI_TASKINST`.`CATEGORY_` AS `CATEGORY_`,`ACT_HI_TASKINST`.`TENANT_ID_` AS `TENANT_ID_`,`ACT_HI_TASKINST`.`LAST_UPDATED_TIME_` AS `LAST_UPDATED_TIME_` from `ACT_HI_TASKINST` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ru_actinst`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_actinst`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_actinst` AS select `ACT_RU_ACTINST`.`ID_` AS `ID_`,`ACT_RU_ACTINST`.`REV_` AS `REV_`,`ACT_RU_ACTINST`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_RU_ACTINST`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_RU_ACTINST`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_RU_ACTINST`.`ACT_ID_` AS `ACT_ID_`,`ACT_RU_ACTINST`.`TASK_ID_` AS `TASK_ID_`,`ACT_RU_ACTINST`.`CALL_PROC_INST_ID_` AS `CALL_PROC_INST_ID_`,`ACT_RU_ACTINST`.`ACT_NAME_` AS `ACT_NAME_`,`ACT_RU_ACTINST`.`ACT_TYPE_` AS `ACT_TYPE_`,`ACT_RU_ACTINST`.`ASSIGNEE_` AS `ASSIGNEE_`,`ACT_RU_ACTINST`.`START_TIME_` AS `START_TIME_`,`ACT_RU_ACTINST`.`END_TIME_` AS `END_TIME_`,`ACT_RU_ACTINST`.`DURATION_` AS `DURATION_`,`ACT_RU_ACTINST`.`TRANSACTION_ORDER_` AS `TRANSACTION_ORDER_`,`ACT_RU_ACTINST`.`DELETE_REASON_` AS `DELETE_REASON_`,`ACT_RU_ACTINST`.`TENANT_ID_` AS `TENANT_ID_`,`ACT_RU_ACTINST`.`COMPLETED_BY_` AS `COMPLETED_BY_` from `ACT_RU_ACTINST` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ge_property`
--

/*!50001 DROP VIEW IF EXISTS `act_ge_property`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ge_property` AS select `ACT_GE_PROPERTY`.`NAME_` AS `NAME_`,`ACT_GE_PROPERTY`.`VALUE_` AS `VALUE_`,`ACT_GE_PROPERTY`.`REV_` AS `REV_` from `ACT_GE_PROPERTY` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_hi_varinst`
--

/*!50001 DROP VIEW IF EXISTS `act_hi_varinst`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_hi_varinst` AS select `ACT_HI_VARINST`.`ID_` AS `ID_`,`ACT_HI_VARINST`.`REV_` AS `REV_`,`ACT_HI_VARINST`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_HI_VARINST`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_HI_VARINST`.`TASK_ID_` AS `TASK_ID_`,`ACT_HI_VARINST`.`NAME_` AS `NAME_`,`ACT_HI_VARINST`.`VAR_TYPE_` AS `VAR_TYPE_`,`ACT_HI_VARINST`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_HI_VARINST`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_HI_VARINST`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_HI_VARINST`.`BYTEARRAY_ID_` AS `BYTEARRAY_ID_`,`ACT_HI_VARINST`.`DOUBLE_` AS `DOUBLE_`,`ACT_HI_VARINST`.`LONG_` AS `LONG_`,`ACT_HI_VARINST`.`TEXT_` AS `TEXT_`,`ACT_HI_VARINST`.`TEXT2_` AS `TEXT2_`,`ACT_HI_VARINST`.`META_INFO_` AS `META_INFO_`,`ACT_HI_VARINST`.`CREATE_TIME_` AS `CREATE_TIME_`,`ACT_HI_VARINST`.`LAST_UPDATED_TIME_` AS `LAST_UPDATED_TIME_` from `ACT_HI_VARINST` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_evt_log`
--

/*!50001 DROP VIEW IF EXISTS `act_evt_log`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_evt_log` AS select `ACT_EVT_LOG`.`LOG_NR_` AS `LOG_NR_`,`ACT_EVT_LOG`.`TYPE_` AS `TYPE_`,`ACT_EVT_LOG`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_EVT_LOG`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_EVT_LOG`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_EVT_LOG`.`TASK_ID_` AS `TASK_ID_`,`ACT_EVT_LOG`.`TIME_STAMP_` AS `TIME_STAMP_`,`ACT_EVT_LOG`.`USER_ID_` AS `USER_ID_`,`ACT_EVT_LOG`.`DATA_` AS `DATA_`,`ACT_EVT_LOG`.`LOCK_OWNER_` AS `LOCK_OWNER_`,`ACT_EVT_LOG`.`LOCK_TIME_` AS `LOCK_TIME_`,`ACT_EVT_LOG`.`IS_PROCESSED_` AS `IS_PROCESSED_` from `ACT_EVT_LOG` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_hi_actinst`
--

/*!50001 DROP VIEW IF EXISTS `act_hi_actinst`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_hi_actinst` AS select `ACT_HI_ACTINST`.`ID_` AS `ID_`,`ACT_HI_ACTINST`.`REV_` AS `REV_`,`ACT_HI_ACTINST`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_HI_ACTINST`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_HI_ACTINST`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_HI_ACTINST`.`ACT_ID_` AS `ACT_ID_`,`ACT_HI_ACTINST`.`TASK_ID_` AS `TASK_ID_`,`ACT_HI_ACTINST`.`CALL_PROC_INST_ID_` AS `CALL_PROC_INST_ID_`,`ACT_HI_ACTINST`.`ACT_NAME_` AS `ACT_NAME_`,`ACT_HI_ACTINST`.`ACT_TYPE_` AS `ACT_TYPE_`,`ACT_HI_ACTINST`.`ASSIGNEE_` AS `ASSIGNEE_`,`ACT_HI_ACTINST`.`START_TIME_` AS `START_TIME_`,`ACT_HI_ACTINST`.`END_TIME_` AS `END_TIME_`,`ACT_HI_ACTINST`.`TRANSACTION_ORDER_` AS `TRANSACTION_ORDER_`,`ACT_HI_ACTINST`.`DURATION_` AS `DURATION_`,`ACT_HI_ACTINST`.`DELETE_REASON_` AS `DELETE_REASON_`,`ACT_HI_ACTINST`.`TENANT_ID_` AS `TENANT_ID_`,`ACT_HI_ACTINST`.`COMPLETED_BY_` AS `COMPLETED_BY_` from `ACT_HI_ACTINST` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `flw_event_definition`
--

/*!50001 DROP VIEW IF EXISTS `flw_event_definition`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `flw_event_definition` AS select `FLW_EVENT_DEFINITION`.`ID_` AS `ID_`,`FLW_EVENT_DEFINITION`.`NAME_` AS `NAME_`,`FLW_EVENT_DEFINITION`.`VERSION_` AS `VERSION_`,`FLW_EVENT_DEFINITION`.`KEY_` AS `KEY_`,`FLW_EVENT_DEFINITION`.`CATEGORY_` AS `CATEGORY_`,`FLW_EVENT_DEFINITION`.`DEPLOYMENT_ID_` AS `DEPLOYMENT_ID_`,`FLW_EVENT_DEFINITION`.`TENANT_ID_` AS `TENANT_ID_`,`FLW_EVENT_DEFINITION`.`RESOURCE_NAME_` AS `RESOURCE_NAME_`,`FLW_EVENT_DEFINITION`.`DESCRIPTION_` AS `DESCRIPTION_` from `FLW_EVENT_DEFINITION` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ru_event_subscr`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_event_subscr`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_event_subscr` AS select `ACT_RU_EVENT_SUBSCR`.`ID_` AS `ID_`,`ACT_RU_EVENT_SUBSCR`.`REV_` AS `REV_`,`ACT_RU_EVENT_SUBSCR`.`EVENT_TYPE_` AS `EVENT_TYPE_`,`ACT_RU_EVENT_SUBSCR`.`EVENT_NAME_` AS `EVENT_NAME_`,`ACT_RU_EVENT_SUBSCR`.`EXECUTION_ID_` AS `EXECUTION_ID_`,`ACT_RU_EVENT_SUBSCR`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_RU_EVENT_SUBSCR`.`ACTIVITY_ID_` AS `ACTIVITY_ID_`,`ACT_RU_EVENT_SUBSCR`.`CONFIGURATION_` AS `CONFIGURATION_`,`ACT_RU_EVENT_SUBSCR`.`CREATED_` AS `CREATED_`,`ACT_RU_EVENT_SUBSCR`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_RU_EVENT_SUBSCR`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_RU_EVENT_SUBSCR`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_RU_EVENT_SUBSCR`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_`,`ACT_RU_EVENT_SUBSCR`.`SCOPE_DEFINITION_KEY_` AS `SCOPE_DEFINITION_KEY_`,`ACT_RU_EVENT_SUBSCR`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_RU_EVENT_SUBSCR`.`LOCK_TIME_` AS `LOCK_TIME_`,`ACT_RU_EVENT_SUBSCR`.`LOCK_OWNER_` AS `LOCK_OWNER_`,`ACT_RU_EVENT_SUBSCR`.`TENANT_ID_` AS `TENANT_ID_` from `ACT_RU_EVENT_SUBSCR` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_hi_procinst`
--

/*!50001 DROP VIEW IF EXISTS `act_hi_procinst`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_hi_procinst` AS select `ACT_HI_PROCINST`.`ID_` AS `ID_`,`ACT_HI_PROCINST`.`REV_` AS `REV_`,`ACT_HI_PROCINST`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_HI_PROCINST`.`BUSINESS_KEY_` AS `BUSINESS_KEY_`,`ACT_HI_PROCINST`.`PROC_DEF_ID_` AS `PROC_DEF_ID_`,`ACT_HI_PROCINST`.`START_TIME_` AS `START_TIME_`,`ACT_HI_PROCINST`.`END_TIME_` AS `END_TIME_`,`ACT_HI_PROCINST`.`DURATION_` AS `DURATION_`,`ACT_HI_PROCINST`.`START_USER_ID_` AS `START_USER_ID_`,`ACT_HI_PROCINST`.`START_ACT_ID_` AS `START_ACT_ID_`,`ACT_HI_PROCINST`.`END_ACT_ID_` AS `END_ACT_ID_`,`ACT_HI_PROCINST`.`SUPER_PROCESS_INSTANCE_ID_` AS `SUPER_PROCESS_INSTANCE_ID_`,`ACT_HI_PROCINST`.`DELETE_REASON_` AS `DELETE_REASON_`,`ACT_HI_PROCINST`.`TENANT_ID_` AS `TENANT_ID_`,`ACT_HI_PROCINST`.`NAME_` AS `NAME_`,`ACT_HI_PROCINST`.`CALLBACK_ID_` AS `CALLBACK_ID_`,`ACT_HI_PROCINST`.`CALLBACK_TYPE_` AS `CALLBACK_TYPE_`,`ACT_HI_PROCINST`.`REFERENCE_ID_` AS `REFERENCE_ID_`,`ACT_HI_PROCINST`.`REFERENCE_TYPE_` AS `REFERENCE_TYPE_`,`ACT_HI_PROCINST`.`PROPAGATED_STAGE_INST_ID_` AS `PROPAGATED_STAGE_INST_ID_`,`ACT_HI_PROCINST`.`BUSINESS_STATUS_` AS `BUSINESS_STATUS_`,`ACT_HI_PROCINST`.`END_USER_ID_` AS `END_USER_ID_`,`ACT_HI_PROCINST`.`STATE_` AS `STATE_` from `ACT_HI_PROCINST` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ru_history_job`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_history_job`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_history_job` AS select `ACT_RU_HISTORY_JOB`.`ID_` AS `ID_`,`ACT_RU_HISTORY_JOB`.`REV_` AS `REV_`,`ACT_RU_HISTORY_JOB`.`LOCK_EXP_TIME_` AS `LOCK_EXP_TIME_`,`ACT_RU_HISTORY_JOB`.`LOCK_OWNER_` AS `LOCK_OWNER_`,`ACT_RU_HISTORY_JOB`.`RETRIES_` AS `RETRIES_`,`ACT_RU_HISTORY_JOB`.`EXCEPTION_STACK_ID_` AS `EXCEPTION_STACK_ID_`,`ACT_RU_HISTORY_JOB`.`EXCEPTION_MSG_` AS `EXCEPTION_MSG_`,`ACT_RU_HISTORY_JOB`.`HANDLER_TYPE_` AS `HANDLER_TYPE_`,`ACT_RU_HISTORY_JOB`.`HANDLER_CFG_` AS `HANDLER_CFG_`,`ACT_RU_HISTORY_JOB`.`CUSTOM_VALUES_ID_` AS `CUSTOM_VALUES_ID_`,`ACT_RU_HISTORY_JOB`.`ADV_HANDLER_CFG_ID_` AS `ADV_HANDLER_CFG_ID_`,`ACT_RU_HISTORY_JOB`.`CREATE_TIME_` AS `CREATE_TIME_`,`ACT_RU_HISTORY_JOB`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_RU_HISTORY_JOB`.`TENANT_ID_` AS `TENANT_ID_` from `ACT_RU_HISTORY_JOB` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_id_user`
--

/*!50001 DROP VIEW IF EXISTS `act_id_user`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_id_user` AS select `ACT_ID_USER`.`ID_` AS `ID_`,`ACT_ID_USER`.`REV_` AS `REV_`,`ACT_ID_USER`.`FIRST_` AS `FIRST_`,`ACT_ID_USER`.`LAST_` AS `LAST_`,`ACT_ID_USER`.`DISPLAY_NAME_` AS `DISPLAY_NAME_`,`ACT_ID_USER`.`EMAIL_` AS `EMAIL_`,`ACT_ID_USER`.`PWD_` AS `PWD_`,`ACT_ID_USER`.`PICTURE_ID_` AS `PICTURE_ID_`,`ACT_ID_USER`.`TENANT_ID_` AS `TENANT_ID_` from `ACT_ID_USER` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_hi_entitylink`
--

/*!50001 DROP VIEW IF EXISTS `act_hi_entitylink`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_hi_entitylink` AS select `ACT_HI_ENTITYLINK`.`ID_` AS `ID_`,`ACT_HI_ENTITYLINK`.`LINK_TYPE_` AS `LINK_TYPE_`,`ACT_HI_ENTITYLINK`.`CREATE_TIME_` AS `CREATE_TIME_`,`ACT_HI_ENTITYLINK`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_HI_ENTITYLINK`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_HI_ENTITYLINK`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_HI_ENTITYLINK`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_`,`ACT_HI_ENTITYLINK`.`PARENT_ELEMENT_ID_` AS `PARENT_ELEMENT_ID_`,`ACT_HI_ENTITYLINK`.`REF_SCOPE_ID_` AS `REF_SCOPE_ID_`,`ACT_HI_ENTITYLINK`.`REF_SCOPE_TYPE_` AS `REF_SCOPE_TYPE_`,`ACT_HI_ENTITYLINK`.`REF_SCOPE_DEFINITION_ID_` AS `REF_SCOPE_DEFINITION_ID_`,`ACT_HI_ENTITYLINK`.`ROOT_SCOPE_ID_` AS `ROOT_SCOPE_ID_`,`ACT_HI_ENTITYLINK`.`ROOT_SCOPE_TYPE_` AS `ROOT_SCOPE_TYPE_`,`ACT_HI_ENTITYLINK`.`HIERARCHY_TYPE_` AS `HIERARCHY_TYPE_` from `ACT_HI_ENTITYLINK` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_hi_attachment`
--

/*!50001 DROP VIEW IF EXISTS `act_hi_attachment`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_hi_attachment` AS select `ACT_HI_ATTACHMENT`.`ID_` AS `ID_`,`ACT_HI_ATTACHMENT`.`REV_` AS `REV_`,`ACT_HI_ATTACHMENT`.`USER_ID_` AS `USER_ID_`,`ACT_HI_ATTACHMENT`.`NAME_` AS `NAME_`,`ACT_HI_ATTACHMENT`.`DESCRIPTION_` AS `DESCRIPTION_`,`ACT_HI_ATTACHMENT`.`TYPE_` AS `TYPE_`,`ACT_HI_ATTACHMENT`.`TASK_ID_` AS `TASK_ID_`,`ACT_HI_ATTACHMENT`.`PROC_INST_ID_` AS `PROC_INST_ID_`,`ACT_HI_ATTACHMENT`.`URL_` AS `URL_`,`ACT_HI_ATTACHMENT`.`CONTENT_ID_` AS `CONTENT_ID_`,`ACT_HI_ATTACHMENT`.`TIME_` AS `TIME_` from `ACT_HI_ATTACHMENT` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `act_ru_entitylink`
--

/*!50001 DROP VIEW IF EXISTS `act_ru_entitylink`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `act_ru_entitylink` AS select `ACT_RU_ENTITYLINK`.`ID_` AS `ID_`,`ACT_RU_ENTITYLINK`.`REV_` AS `REV_`,`ACT_RU_ENTITYLINK`.`CREATE_TIME_` AS `CREATE_TIME_`,`ACT_RU_ENTITYLINK`.`LINK_TYPE_` AS `LINK_TYPE_`,`ACT_RU_ENTITYLINK`.`SCOPE_ID_` AS `SCOPE_ID_`,`ACT_RU_ENTITYLINK`.`SUB_SCOPE_ID_` AS `SUB_SCOPE_ID_`,`ACT_RU_ENTITYLINK`.`SCOPE_TYPE_` AS `SCOPE_TYPE_`,`ACT_RU_ENTITYLINK`.`SCOPE_DEFINITION_ID_` AS `SCOPE_DEFINITION_ID_`,`ACT_RU_ENTITYLINK`.`PARENT_ELEMENT_ID_` AS `PARENT_ELEMENT_ID_`,`ACT_RU_ENTITYLINK`.`REF_SCOPE_ID_` AS `REF_SCOPE_ID_`,`ACT_RU_ENTITYLINK`.`REF_SCOPE_TYPE_` AS `REF_SCOPE_TYPE_`,`ACT_RU_ENTITYLINK`.`REF_SCOPE_DEFINITION_ID_` AS `REF_SCOPE_DEFINITION_ID_`,`ACT_RU_ENTITYLINK`.`ROOT_SCOPE_ID_` AS `ROOT_SCOPE_ID_`,`ACT_RU_ENTITYLINK`.`ROOT_SCOPE_TYPE_` AS `ROOT_SCOPE_TYPE_`,`ACT_RU_ENTITYLINK`.`HIERARCHY_TYPE_` AS `HIERARCHY_TYPE_` from `ACT_RU_ENTITYLINK` */;
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

-- Dump completed on 2026-11-14 18:29:20
