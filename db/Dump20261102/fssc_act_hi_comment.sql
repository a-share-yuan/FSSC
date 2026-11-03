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
-- Table structure for table `act_hi_comment`
--

DROP TABLE IF EXISTS `act_hi_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `act_hi_comment` (
  `ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `TYPE_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TIME_` datetime(3) NOT NULL,
  `USER_ID_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TASK_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROC_INST_ID_` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ACTION_` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `MESSAGE_` varchar(4000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `FULL_MSG_` longblob,
  PRIMARY KEY (`ID_`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `act_hi_comment`
--

LOCK TABLES `act_hi_comment` WRITE;
/*!40000 ALTER TABLE `act_hi_comment` DISABLE KEYS */;
INSERT INTO `act_hi_comment` VALUES ('1ac2fe38-d72e-11f1-9881-d8bbc1e55a1a','event','2026-11-03 10:27:24.265',NULL,'1aad5354-d72e-11f1-9881-d8bbc1e55a1a',NULL,'AddUserLink','3851_|_assignee',NULL),('1aec5841-d72e-11f1-9881-d8bbc1e55a1a','event','2026-11-03 10:27:24.536',NULL,'1adb684d-d72e-11f1-9881-d8bbc1e55a1a',NULL,'AddUserLink','3951_|_assignee',NULL),('1af8db7a-d72e-11f1-9881-d8bbc1e55a1a','event','2026-11-03 10:27:24.618',NULL,'1af272d6-d72e-11f1-9881-d8bbc1e55a1a',NULL,'AddUserLink','3851_|_assignee',NULL),('1b432b04-d72e-11f1-9881-d8bbc1e55a1a','event','2026-11-03 10:27:25.105',NULL,'1b3b62d0-d72e-11f1-9881-d8bbc1e55a1a',NULL,'AddUserLink','3951_|_assignee',NULL),('c5680f23-d72c-11f1-9881-d8bbc1e55a1a','event','2026-11-03 10:17:51.566',NULL,'c539850f-d72c-11f1-9881-d8bbc1e55a1a',NULL,'AddUserLink','3851_|_assignee',NULL),('c5a47bfc-d72c-11f1-9881-d8bbc1e55a1a','event','2026-11-03 10:17:51.962',NULL,'c599cd98-d72c-11f1-9881-d8bbc1e55a1a',NULL,'AddUserLink','3951_|_assignee',NULL),('c5b8a055-d72c-11f1-9881-d8bbc1e55a1a','event','2026-11-03 10:17:52.094',NULL,'c5af2a71-d72c-11f1-9881-d8bbc1e55a1a',NULL,'AddUserLink','3851_|_assignee',NULL),('c5fb9cdf-d72c-11f1-9881-d8bbc1e55a1a','event','2026-11-03 10:17:52.533',NULL,'c5f24e0b-d72c-11f1-9881-d8bbc1e55a1a',NULL,'AddUserLink','3951_|_assignee',NULL),('c96f546d-d72e-11f1-9881-d8bbc1e55a1a','event','2026-11-03 10:32:17.318',NULL,'c960d579-d72e-11f1-9881-d8bbc1e55a1a',NULL,'AddUserLink','3851_|_assignee',NULL),('c9821936-d72e-11f1-9881-d8bbc1e55a1a','event','2026-11-03 10:32:17.441',NULL,'c97c9af2-d72e-11f1-9881-d8bbc1e55a1a',NULL,'AddUserLink','3951_|_assignee',NULL),('c98e273f-d72e-11f1-9881-d8bbc1e55a1a','event','2026-11-03 10:32:17.520',NULL,'c9885adb-d72e-11f1-9881-d8bbc1e55a1a',NULL,'AddUserLink','3851_|_assignee',NULL),('c9c0f729-d72e-11f1-9881-d8bbc1e55a1a','event','2026-11-03 10:32:17.853',NULL,'c9bb51d5-d72e-11f1-9881-d8bbc1e55a1a',NULL,'AddUserLink','3951_|_assignee',NULL);
/*!40000 ALTER TABLE `act_hi_comment` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-03 10:48:36
