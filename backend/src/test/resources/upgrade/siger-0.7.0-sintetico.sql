-- SIGER 0.7.0 (commit 3938e4e = v0.7.0, esquema V16): volcado SINTETICO para MySql84Upgrade070Test (E01 de 0.8.0).
-- Solo semillas demo de 0.7.0, los vectores NNA SRCeI de prueba de E00 y registros sinteticos creados con sus comandos;
-- sin datos de personas reales. Las claves de las cuentas demo se reemplazaron por un valor que no permite ingresar.
-- Procedimiento de generacion: docs/BITACORA_0_8_0.md, registro de E01. No editar a mano.


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `app_meta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_meta` (
  `id` int NOT NULL,
  `namespace` varchar(40) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `app_meta` WRITE;
/*!40000 ALTER TABLE `app_meta` DISABLE KEYS */;
INSERT INTO `app_meta` VALUES (1,'SIGER_A_2026');
/*!40000 ALTER TABLE `app_meta` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `application_marker`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `application_marker` (
  `code` varchar(80) NOT NULL,
  `applied_ms` bigint NOT NULL,
  `amount` bigint DEFAULT NULL,
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `application_marker` WRITE;
/*!40000 ALTER TABLE `application_marker` DISABLE KEYS */;
INSERT INTO `application_marker` VALUES ('COORDINACION_REGIONAL_050',1795532157221,NULL);
INSERT INTO `application_marker` VALUES ('DEMO_ORGANIZATION_030',1795532157201,NULL);
INSERT INTO `application_marker` VALUES ('MILESTONES_V14',1795532157234,0);
INSERT INTO `application_marker` VALUES ('PERSON_INDEX_V12B',1795532157227,NULL);
INSERT INTO `application_marker` VALUES ('REGION_FICHA_V16',1795532157239,NULL);
INSERT INTO `application_marker` VALUES ('ROL_AUTORIDAD_SUPRIMIDO_050',1795532157218,NULL);
/*!40000 ALTER TABLE `application_marker` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `attention_handoff`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attention_handoff` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `requirement_id` bigint NOT NULL,
  `from_user` bigint NOT NULL,
  `from_scope` bigint NOT NULL,
  `sent_by` bigint NOT NULL,
  `sent_ms` bigint NOT NULL,
  `target_scope` bigint NOT NULL,
  `target_user` bigint DEFAULT NULL,
  `recipient_kind` varchar(16) NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDIENTE',
  `reason` varchar(2000) NOT NULL,
  `decided_by` bigint DEFAULT NULL,
  `decided_ms` bigint DEFAULT NULL,
  `decision_reason` varchar(2000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `from_user` (`from_user`),
  KEY `from_scope` (`from_scope`),
  KEY `sent_by` (`sent_by`),
  KEY `target_scope` (`target_scope`),
  KEY `decided_by` (`decided_by`),
  KEY `ix_attention_pending` (`requirement_id`,`state`),
  KEY `ix_attention_recipient` (`target_user`,`state`,`target_scope`),
  CONSTRAINT `attention_handoff_ibfk_1` FOREIGN KEY (`requirement_id`) REFERENCES `attention_record` (`requirement_id`),
  CONSTRAINT `attention_handoff_ibfk_2` FOREIGN KEY (`from_user`) REFERENCES `staff` (`id`),
  CONSTRAINT `attention_handoff_ibfk_3` FOREIGN KEY (`from_scope`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `attention_handoff_ibfk_4` FOREIGN KEY (`sent_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `attention_handoff_ibfk_5` FOREIGN KEY (`target_scope`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `attention_handoff_ibfk_6` FOREIGN KEY (`target_user`) REFERENCES `staff` (`id`),
  CONSTRAINT `attention_handoff_ibfk_7` FOREIGN KEY (`decided_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `attention_handoff_chk_1` CHECK ((`recipient_kind` in (_utf8mb4'PROFESIONAL',_utf8mb4'JEFATURA'))),
  CONSTRAINT `attention_handoff_chk_2` CHECK ((`state` in (_utf8mb4'PENDIENTE',_utf8mb4'RECIBIDA',_utf8mb4'DEVUELTA',_utf8mb4'RETIRADA'))),
  CONSTRAINT `attention_handoff_chk_3` CHECK ((((`recipient_kind` = _utf8mb4'PROFESIONAL') and (`target_user` is not null)) or ((`recipient_kind` = _utf8mb4'JEFATURA') and (`target_user` is null))))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `attention_handoff` WRITE;
/*!40000 ALTER TABLE `attention_handoff` DISABLE KEYS */;
INSERT INTO `attention_handoff` VALUES (1,4,2,4,2,1795532157609,4,3,'PROFESIONAL','RECIBIDA','Continuar la atencion',3,1795532157616,'Recibo la atencion');
/*!40000 ALTER TABLE `attention_handoff` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `attention_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attention_record` (
  `requirement_id` bigint NOT NULL,
  `modality` varchar(16) NOT NULL,
  `data_json` mediumtext NOT NULL,
  `followup_required` tinyint(1) NOT NULL DEFAULT '0',
  `updated_by` bigint NOT NULL,
  `updated_ms` bigint NOT NULL,
  PRIMARY KEY (`requirement_id`),
  KEY `updated_by` (`updated_by`),
  CONSTRAINT `attention_record_ibfk_1` FOREIGN KEY (`requirement_id`) REFERENCES `requirement` (`id`),
  CONSTRAINT `attention_record_ibfk_2` FOREIGN KEY (`updated_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `attention_record_chk_1` CHECK ((`modality` in (_utf8mb4'GENERAL',_utf8mb4'NNA')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `attention_record` WRITE;
/*!40000 ALTER TABLE `attention_record` DISABLE KEYS */;
INSERT INTO `attention_record` VALUES (4,'NNA','{\"presenter_name\":\"\",\"identity_document\":\"\",\"identity_type\":\"\",\"relationship\":\"\",\"contact_email\":\"\",\"contact_phone\":\"\",\"presenter_domicile\":\"\",\"region\":\"\",\"commune\":\"\",\"preferred_name\":\"\",\"pronouns\":\"\",\"supports\":\"\",\"court\":\"\",\"rit\":\"\",\"ruc\":\"\",\"guardian_name\":\"\",\"nationality\":\"\",\"group_description\":\"\",\"approximate_count\":\"\",\"motive\":\"Contacto sintetico NNA\",\"story\":\"\",\"request_text\":\"\",\"observations\":\"\",\"orientation\":\"\",\"actions\":\"\",\"commitments\":\"\",\"institutions\":\"\",\"safety_note\":\"\",\"participation_note\":\"\",\"child_message\":\"\",\"identity_note\":\"\",\"priority_note\":\"\",\"safe_contact\":\"\",\"judicial_status\":\"\",\"guardian_status\":\"\",\"participation\":\"NO\",\"participation_mode\":\"\",\"role_explained\":\"\",\"priority\":\"\",\"children_scope\":\"\",\"modality\":\"NNA\",\"schema_version\":\"1.0\",\"course\":\"EN_ATENCION\",\"people\":[{\"role\":\"NNA\",\"preferred_name\":\"\",\"nationality\":\"\",\"relationship\":\"\",\"name\":\"Nombre NNA sintetico\",\"birth_date\":\"\",\"schooling\":\"\",\"location\":\"\",\"identity_document\":\"\",\"age\":\"\"}]}',0,2,1795532157594);
INSERT INTO `attention_record` VALUES (5,'GENERAL','{\"presenter_first_name\":\"Persona\",\"presenter_middle_name\":\"\",\"presenter_last_name\":\"Sintetica\",\"presenter_second_last_name\":\"\",\"presenter_run_number\":\"11111111\",\"presenter_run_dv\":\"1\",\"presenter_birth_date\":\"\",\"presenter_document_kind\":\"\",\"presenter_document_number\":\"\",\"presenter_name\":\"Persona Sintetica\",\"identity_document\":\"11.111.111-1\",\"identity_type\":\"RUN\",\"relationship\":\"\",\"contact_email\":\"persona@ejemplo.cl\",\"contact_phone\":\"+56900000000\",\"presenter_domicile\":\"\",\"region\":\"07\",\"commune\":\"Talca\",\"preferred_name\":\"\",\"pronouns\":\"\",\"supports\":\"\",\"court\":\"\",\"rit\":\"\",\"ruc\":\"\",\"guardian_name\":\"\",\"nationality\":\"Chile\",\"group_description\":\"\",\"approximate_count\":\"\",\"motive\":\"Consulta sintetica\",\"story\":\"\",\"request_text\":\"\",\"observations\":\"\",\"orientation\":\"\",\"actions\":\"\",\"commitments\":\"\",\"institutions\":\"\",\"safety_note\":\"\",\"participation_note\":\"\",\"child_message\":\"\",\"identity_note\":\"\",\"priority_note\":\"\",\"safe_contact\":\"\",\"judicial_status\":\"\",\"guardian_status\":\"\",\"participation\":\"\",\"participation_mode\":\"\",\"role_explained\":\"\",\"priority\":\"\",\"children_scope\":\"\",\"modality\":\"GENERAL\",\"schema_version\":\"1.1\",\"course\":\"EN_ATENCION\",\"people\":[{\"role\":\"NNA\",\"preferred_name\":\"\",\"nationality\":\"\",\"relationship\":\"\",\"name\":\"Nino sintetico\",\"birth_date\":\"\",\"schooling\":\"\",\"location\":\"\",\"identity_document\":\"\",\"age\":\"9\"}]}',0,2,1795532157706);
INSERT INTO `attention_record` VALUES (6,'NNA','{\"presenter_first_name\":\"\",\"presenter_middle_name\":\"\",\"presenter_last_name\":\"\",\"presenter_second_last_name\":\"\",\"presenter_run_number\":\"\",\"presenter_run_dv\":\"\",\"presenter_birth_date\":\"\",\"presenter_document_kind\":\"\",\"presenter_document_number\":\"\",\"presenter_name\":\"\",\"identity_document\":\"\",\"identity_type\":\"\",\"relationship\":\"\",\"contact_email\":\"\",\"contact_phone\":\"\",\"presenter_domicile\":\"\",\"region\":\"\",\"commune\":\"\",\"preferred_name\":\"\",\"pronouns\":\"\",\"supports\":\"\",\"court\":\"\",\"rit\":\"\",\"ruc\":\"\",\"guardian_name\":\"\",\"nationality\":\"\",\"group_description\":\"\",\"approximate_count\":\"\",\"motive\":\"Contacto sintetico NNA 1.1\",\"story\":\"\",\"request_text\":\"\",\"observations\":\"\",\"orientation\":\"\",\"actions\":\"\",\"commitments\":\"\",\"institutions\":\"\",\"safety_note\":\"\",\"participation_note\":\"\",\"child_message\":\"\",\"identity_note\":\"\",\"priority_note\":\"\",\"safe_contact\":\"\",\"judicial_status\":\"\",\"guardian_status\":\"\",\"participation\":\"NO\",\"participation_mode\":\"\",\"role_explained\":\"\",\"priority\":\"\",\"children_scope\":\"\",\"modality\":\"NNA\",\"schema_version\":\"1.1\",\"course\":\"EN_ATENCION\",\"people\":[{\"role\":\"NNA\",\"preferred_name\":\"\",\"nationality\":\"\",\"relationship\":\"\",\"name\":\"Nina sintetica\",\"birth_date\":\"\",\"schooling\":\"\",\"location\":\"\",\"identity_document\":\"\",\"age\":\"doce\"}]}',0,2,1795532157716);
/*!40000 ALTER TABLE `attention_record` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `attention_revision`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attention_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `requirement_id` bigint NOT NULL,
  `requirement_revision` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  `registered_ms` bigint NOT NULL,
  `reason` varchar(2000) NOT NULL,
  `data_json` mediumtext NOT NULL,
  `region_code` varchar(4) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `requirement_id` (`requirement_id`,`requirement_revision`),
  KEY `author_id` (`author_id`),
  KEY `ix_attention_revisions` (`requirement_id`,`id`),
  CONSTRAINT `attention_revision_ibfk_1` FOREIGN KEY (`requirement_id`) REFERENCES `attention_record` (`requirement_id`),
  CONSTRAINT `attention_revision_ibfk_2` FOREIGN KEY (`author_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `attention_revision` WRITE;
/*!40000 ALTER TABLE `attention_revision` DISABLE KEYS */;
INSERT INTO `attention_revision` VALUES (1,4,0,2,1795532157601,'Primer registro con antecedentes disponibles','{\"presenter_name\":\"\",\"identity_document\":\"\",\"identity_type\":\"\",\"relationship\":\"\",\"contact_email\":\"\",\"contact_phone\":\"\",\"presenter_domicile\":\"\",\"region\":\"\",\"commune\":\"\",\"preferred_name\":\"\",\"pronouns\":\"\",\"supports\":\"\",\"court\":\"\",\"rit\":\"\",\"ruc\":\"\",\"guardian_name\":\"\",\"nationality\":\"\",\"group_description\":\"\",\"approximate_count\":\"\",\"motive\":\"Contacto sintetico NNA\",\"story\":\"\",\"request_text\":\"\",\"observations\":\"\",\"orientation\":\"\",\"actions\":\"\",\"commitments\":\"\",\"institutions\":\"\",\"safety_note\":\"\",\"participation_note\":\"\",\"child_message\":\"\",\"identity_note\":\"\",\"priority_note\":\"\",\"safe_contact\":\"\",\"judicial_status\":\"\",\"guardian_status\":\"\",\"participation\":\"NO\",\"participation_mode\":\"\",\"role_explained\":\"\",\"priority\":\"\",\"children_scope\":\"\",\"modality\":\"NNA\",\"schema_version\":\"1.0\",\"course\":\"EN_ATENCION\",\"people\":[{\"role\":\"NNA\",\"preferred_name\":\"\",\"nationality\":\"\",\"relationship\":\"\",\"name\":\"Nombre NNA sintetico\",\"birth_date\":\"\",\"schooling\":\"\",\"location\":\"\",\"identity_document\":\"\",\"age\":\"\"}]}',NULL);
INSERT INTO `attention_revision` VALUES (2,5,0,2,1795532157699,'Primer registro con antecedentes disponibles','{\"presenter_first_name\":\"Persona\",\"presenter_middle_name\":\"\",\"presenter_last_name\":\"Sintetica\",\"presenter_second_last_name\":\"\",\"presenter_run_number\":\"11111111\",\"presenter_run_dv\":\"1\",\"presenter_birth_date\":\"\",\"presenter_document_kind\":\"\",\"presenter_document_number\":\"\",\"presenter_name\":\"Persona Sintetica\",\"identity_document\":\"11.111.111-1\",\"identity_type\":\"RUN\",\"relationship\":\"\",\"contact_email\":\"persona@ejemplo.cl\",\"contact_phone\":\"+56900000000\",\"presenter_domicile\":\"\",\"region\":\"07\",\"commune\":\"Talca\",\"preferred_name\":\"\",\"pronouns\":\"\",\"supports\":\"\",\"court\":\"\",\"rit\":\"\",\"ruc\":\"\",\"guardian_name\":\"\",\"nationality\":\"Chile\",\"group_description\":\"\",\"approximate_count\":\"\",\"motive\":\"Consulta sintetica\",\"story\":\"\",\"request_text\":\"\",\"observations\":\"\",\"orientation\":\"\",\"actions\":\"\",\"commitments\":\"\",\"institutions\":\"\",\"safety_note\":\"\",\"participation_note\":\"\",\"child_message\":\"\",\"identity_note\":\"\",\"priority_note\":\"\",\"safe_contact\":\"\",\"judicial_status\":\"\",\"guardian_status\":\"\",\"participation\":\"\",\"participation_mode\":\"\",\"role_explained\":\"\",\"priority\":\"\",\"children_scope\":\"\",\"modality\":\"GENERAL\",\"schema_version\":\"1.1\",\"course\":\"EN_ATENCION\",\"people\":[]}','07');
INSERT INTO `attention_revision` VALUES (3,5,1,2,1795532157708,'Complementacion sintetica','{\"presenter_first_name\":\"Persona\",\"presenter_middle_name\":\"\",\"presenter_last_name\":\"Sintetica\",\"presenter_second_last_name\":\"\",\"presenter_run_number\":\"11111111\",\"presenter_run_dv\":\"1\",\"presenter_birth_date\":\"\",\"presenter_document_kind\":\"\",\"presenter_document_number\":\"\",\"presenter_name\":\"Persona Sintetica\",\"identity_document\":\"11.111.111-1\",\"identity_type\":\"RUN\",\"relationship\":\"\",\"contact_email\":\"persona@ejemplo.cl\",\"contact_phone\":\"+56900000000\",\"presenter_domicile\":\"\",\"region\":\"07\",\"commune\":\"Talca\",\"preferred_name\":\"\",\"pronouns\":\"\",\"supports\":\"\",\"court\":\"\",\"rit\":\"\",\"ruc\":\"\",\"guardian_name\":\"\",\"nationality\":\"Chile\",\"group_description\":\"\",\"approximate_count\":\"\",\"motive\":\"Consulta sintetica\",\"story\":\"\",\"request_text\":\"\",\"observations\":\"\",\"orientation\":\"\",\"actions\":\"\",\"commitments\":\"\",\"institutions\":\"\",\"safety_note\":\"\",\"participation_note\":\"\",\"child_message\":\"\",\"identity_note\":\"\",\"priority_note\":\"\",\"safe_contact\":\"\",\"judicial_status\":\"\",\"guardian_status\":\"\",\"participation\":\"\",\"participation_mode\":\"\",\"role_explained\":\"\",\"priority\":\"\",\"children_scope\":\"\",\"modality\":\"GENERAL\",\"schema_version\":\"1.1\",\"course\":\"EN_ATENCION\",\"people\":[{\"role\":\"NNA\",\"preferred_name\":\"\",\"nationality\":\"\",\"relationship\":\"\",\"name\":\"Nino sintetico\",\"birth_date\":\"\",\"schooling\":\"\",\"location\":\"\",\"identity_document\":\"\",\"age\":\"9\"}]}','07');
INSERT INTO `attention_revision` VALUES (4,6,0,2,1795532157718,'Primer registro con antecedentes disponibles','{\"presenter_first_name\":\"\",\"presenter_middle_name\":\"\",\"presenter_last_name\":\"\",\"presenter_second_last_name\":\"\",\"presenter_run_number\":\"\",\"presenter_run_dv\":\"\",\"presenter_birth_date\":\"\",\"presenter_document_kind\":\"\",\"presenter_document_number\":\"\",\"presenter_name\":\"\",\"identity_document\":\"\",\"identity_type\":\"\",\"relationship\":\"\",\"contact_email\":\"\",\"contact_phone\":\"\",\"presenter_domicile\":\"\",\"region\":\"\",\"commune\":\"\",\"preferred_name\":\"\",\"pronouns\":\"\",\"supports\":\"\",\"court\":\"\",\"rit\":\"\",\"ruc\":\"\",\"guardian_name\":\"\",\"nationality\":\"\",\"group_description\":\"\",\"approximate_count\":\"\",\"motive\":\"Contacto sintetico NNA 1.1\",\"story\":\"\",\"request_text\":\"\",\"observations\":\"\",\"orientation\":\"\",\"actions\":\"\",\"commitments\":\"\",\"institutions\":\"\",\"safety_note\":\"\",\"participation_note\":\"\",\"child_message\":\"\",\"identity_note\":\"\",\"priority_note\":\"\",\"safe_contact\":\"\",\"judicial_status\":\"\",\"guardian_status\":\"\",\"participation\":\"NO\",\"participation_mode\":\"\",\"role_explained\":\"\",\"priority\":\"\",\"children_scope\":\"\",\"modality\":\"NNA\",\"schema_version\":\"1.1\",\"course\":\"EN_ATENCION\",\"people\":[{\"role\":\"NNA\",\"preferred_name\":\"\",\"nationality\":\"\",\"relationship\":\"\",\"name\":\"Nina sintetica\",\"birth_date\":\"\",\"schooling\":\"\",\"location\":\"\",\"identity_document\":\"\",\"age\":\"doce\"}]}',NULL);
/*!40000 ALTER TABLE `attention_revision` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `audit_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_id` bigint NOT NULL,
  `at_ms` bigint NOT NULL,
  `action` varchar(48) NOT NULL,
  `resource_type` varchar(24) NOT NULL,
  `resource_id` bigint NOT NULL,
  `scope_id` bigint DEFAULT NULL,
  `note` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `actor_id` (`actor_id`),
  KEY `scope_id` (`scope_id`),
  KEY `ix_audit_resource` (`resource_type`,`resource_id`,`id`),
  CONSTRAINT `audit_event_ibfk_1` FOREIGN KEY (`actor_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `audit_event_ibfk_2` FOREIGN KEY (`scope_id`) REFERENCES `org_scope` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=92 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `audit_event` WRITE;
/*!40000 ALTER TABLE `audit_event` DISABLE KEYS */;
INSERT INTO `audit_event` VALUES (1,16,1795532157209,'CONFIGURACION_SINTETICA','SISTEMA',0,NULL,'Cuentas y sede ficticias. No son designaciones institucionales.');
INSERT INTO `audit_event` VALUES (2,2,1795532157272,'COM_REGISTRADA','COM',1,4,'Canal TELEFONO');
INSERT INTO `audit_event` VALUES (3,1,1795532157287,'SOL_DISTRIBUIDA','SOL',1,4,'Clasificacion sintetica');
INSERT INTO `audit_event` VALUES (4,4,1795532157299,'SOL_REDIRIGIDA','SOL',1,2,'Destino anterior 4. Corresponde a UPD');
INSERT INTO `audit_event` VALUES (5,7,1795532157308,'SOL_DEVUELTA_DISTRIBUCION','SOL',1,2,'Vuelve a clasificacion');
INSERT INTO `audit_event` VALUES (6,1,1795532157317,'SOL_REDIRIGIDA','SOL',1,4,'Destino anterior 2. Redistribucion');
INSERT INTO `audit_event` VALUES (7,4,1795532157329,'REQ_CREADO','REQ',1,4,'COM 1');
INSERT INTO `audit_event` VALUES (8,4,1795532157330,'SOL_ASUMIDA','SOL',1,4,'Asumida');
INSERT INTO `audit_event` VALUES (9,4,1795532157338,'REQ_COMPLEJIDAD','REQ',1,4,' -> BAJA. Clasificacion sintetica');
INSERT INTO `audit_event` VALUES (10,4,1795532157349,'CASO_CREADO','CAS',1,4,'Seguimiento sintetico');
INSERT INTO `audit_event` VALUES (11,3,1795532157355,'ASIGNACION_ACUSADA','CAS',1,4,'Acuse manual.');
INSERT INTO `audit_event` VALUES (12,4,1795532157362,'CASE_WAIT','CAS',1,4,'Espera de antecedentes');
INSERT INTO `audit_event` VALUES (13,4,1795532157368,'CASE_RESUME','CAS',1,4,'Llegaron los antecedentes');
INSERT INTO `audit_event` VALUES (14,3,1795532157376,'TAREA_CREADA','CAS',1,4,'Agenda independiente de actuaciones.');
INSERT INTO `audit_event` VALUES (15,3,1795532157385,'TAREA_CREADA','CAS',1,4,'Agenda independiente de actuaciones.');
INSERT INTO `audit_event` VALUES (16,3,1795532157391,'TASK_COMPLETE','CAS',1,4,'Tarea 1. Llamada realizada');
INSERT INTO `audit_event` VALUES (17,3,1795532157397,'TASK_CANCEL','CAS',1,4,'Tarea 2. Ya no corresponde');
INSERT INTO `audit_event` VALUES (18,3,1795532157408,'CASE_REQUEST_CLOSE','CAS',1,4,'Gestion concluida');
INSERT INTO `audit_event` VALUES (19,3,1795532157416,'CASE_WITHDRAW_CLOSE','CAS',1,4,'Falta un antecedente');
INSERT INTO `audit_event` VALUES (20,3,1795532157424,'CASE_REQUEST_CLOSE','CAS',1,4,'Gestion concluida');
INSERT INTO `audit_event` VALUES (21,4,1795532157432,'CASE_RETURN','CAS',1,4,'Falta informar al requirente');
INSERT INTO `audit_event` VALUES (22,4,1795532157443,'CASE_ASSIGN','CAS',1,4,'Continua la coordinacion');
INSERT INTO `audit_event` VALUES (23,4,1795532157449,'COLABORACION_SOLICITADA','FLUJO',1,4,'Colaboracion sintetica');
INSERT INTO `audit_event` VALUES (24,7,1795532157466,'CASO_CREADO','CAS',2,8,'Apoyo sintetico');
INSERT INTO `audit_event` VALUES (25,7,1795532157467,'COLABORACION_ACEPTADA','FLUJO',1,2,'Aceptada');
INSERT INTO `audit_event` VALUES (26,8,1795532157478,'CASO_CERRADO_PROFESIONAL_BAJA','CAS',2,8,'Apoyo entregado');
INSERT INTO `audit_event` VALUES (27,4,1795532157486,'CASO_CERRADO_JEFATURA_PROPIA','CAS',1,4,'Cierre confirmado');
INSERT INTO `audit_event` VALUES (28,4,1795532157501,'REQ_CIERRE_EXPLICITO','REQ',1,4,'Motivo de cierre sintetico');
INSERT INTO `audit_event` VALUES (29,4,1795532157507,'REAPERTURA_SOLICITADA','REAPERTURA',1,4,'Nuevos antecedentes');
INSERT INTO `audit_event` VALUES (30,4,1795532157514,'REQ_REACTIVADO','REQ',1,4,'Procede');
INSERT INTO `audit_event` VALUES (31,4,1795532157515,'REAPERTURA_ACEPTADA','REAPERTURA',1,4,'Procede');
INSERT INTO `audit_event` VALUES (32,4,1795532157520,'REAPERTURA_SOLICITADA','REAPERTURA',2,4,'Reabrir el Caso');
INSERT INTO `audit_event` VALUES (33,4,1795532157528,'CASO_REABIERTO','CAS',1,4,'Procede');
INSERT INTO `audit_event` VALUES (34,4,1795532157529,'REAPERTURA_ACEPTADA','REAPERTURA',2,4,'Procede');
INSERT INTO `audit_event` VALUES (35,4,1795532157536,'COM_REGISTRADA','COM',2,4,'Canal TELEFONO');
INSERT INTO `audit_event` VALUES (36,4,1795532157546,'REQ_CREADO','REQ',2,4,'COM 2');
INSERT INTO `audit_event` VALUES (37,4,1795532157547,'ASUNCION_INGRESO_PROPIO','SOL',2,4,'Ingreso propio');
INSERT INTO `audit_event` VALUES (38,2,1795532157558,'COM_REGISTRADA','COM',3,4,'Canal TELEFONO');
INSERT INTO `audit_event` VALUES (39,1,1795532157566,'SOL_DISTRIBUIDA','SOL',3,4,'Clasificacion sintetica');
INSERT INTO `audit_event` VALUES (40,4,1795532157574,'SOL_DUPLICADA','SOL',3,4,'Ya ingresada');
INSERT INTO `audit_event` VALUES (41,2,1795532157580,'COM_REGISTRADA','COM',4,4,'Canal TELEFONO');
INSERT INTO `audit_event` VALUES (42,2,1795532157581,'REQ_CREADO','REQ',3,4,'COM 4');
INSERT INTO `audit_event` VALUES (43,2,1795532157584,'PRIMERA_ATENCION_RESUELTA','REQ',3,4,'Orientacion entregada');
INSERT INTO `audit_event` VALUES (44,2,1795532157602,'COM_REGISTRADA_ATENCION','COM',5,4,'Atencion directa. REQ 4');
INSERT INTO `audit_event` VALUES (45,2,1795532157602,'ATENCION_INICIADA','REQ',4,4,'COM 5. Sin SOL ni Caso automatico.');
INSERT INTO `audit_event` VALUES (46,2,1795532157610,'ATENCION_ENTREGADA','REQ',4,4,'Destino 4. Continuar la atencion');
INSERT INTO `audit_event` VALUES (47,3,1795532157617,'ATENCION_RECIBIDA','REQ',4,4,'Entrega 1. Recibo la atencion');
INSERT INTO `audit_event` VALUES (48,3,1795532157626,'ATENCION_RESUELTA','REQ',4,4,'ORIENTACION. Resultado registrado por quien atendio.');
INSERT INTO `audit_event` VALUES (49,2,1795532157660,'COM_ORIGINAL_CONSERVADO','COM',6,4,'Origen FORMULARIO_PUBLICO_DDN d76662e1-7441-4b6b-a731-e2bfb1d3c0bf');
INSERT INTO `audit_event` VALUES (50,2,1795532157674,'COM_ORIGINAL_CONSERVADO','COM',7,4,'Origen FORMULARIO_PUBLICO_DDN 66666666-6666-4666-8666-666666666670');
INSERT INTO `audit_event` VALUES (51,2,1795532157689,'COM_ORIGINAL_CONSERVADO','COM',8,4,'Origen FORMULARIO_PUBLICO_DDN 66666666-6666-4666-8666-666666666671');
INSERT INTO `audit_event` VALUES (52,2,1795532157699,'COM_REGISTRADA_ATENCION','COM',9,4,'Atencion directa. REQ 5');
INSERT INTO `audit_event` VALUES (53,2,1795532157699,'ATENCION_INICIADA','REQ',5,4,'COM 9. Sin SOL ni Caso automatico.');
INSERT INTO `audit_event` VALUES (54,2,1795532157709,'FICHA_COMPLEMENTADA','REQ',5,4,'Version 1. Complementacion sintetica');
INSERT INTO `audit_event` VALUES (55,2,1795532157718,'COM_REGISTRADA_ATENCION','COM',10,4,'Atencion directa. REQ 6');
INSERT INTO `audit_event` VALUES (56,2,1795532157718,'ATENCION_INICIADA','REQ',6,4,'COM 10. Sin SOL ni Caso automatico.');
INSERT INTO `audit_event` VALUES (57,1,1795532157725,'COM_REGISTRADA','COM',11,4,'Canal TELEFONO');
INSERT INTO `audit_event` VALUES (58,1,1795532157732,'SOL_DISTRIBUIDA','SOL',4,1,'Competencia UPR');
INSERT INTO `audit_event` VALUES (59,5,1795532157741,'REQ_CREADO','REQ',7,1,'COM 11');
INSERT INTO `audit_event` VALUES (60,5,1795532157741,'SOL_ASUMIDA','SOL',4,1,'Asuncion sintetica');
INSERT INTO `audit_event` VALUES (61,5,1795532157747,'TRANSFERENCIA_SOLICITADA','FLUJO',2,1,'Corresponde a UPD');
INSERT INTO `audit_event` VALUES (62,7,1795532157755,'TRANSFERENCIA_ACEPTADA','FLUJO',2,2,'Aceptada');
INSERT INTO `audit_event` VALUES (63,7,1795532157763,'REQ_CIERRE_EXPLICITO','REQ',7,2,'Cierre sintetico tras transferencia');
INSERT INTO `audit_event` VALUES (64,1,1795532157770,'COM_REGISTRADA','COM',12,4,'Canal TELEFONO');
INSERT INTO `audit_event` VALUES (65,1,1795532157777,'SOL_DISTRIBUIDA','SOL',5,1,'Competencia UPR');
INSERT INTO `audit_event` VALUES (66,5,1795532157793,'REQ_CREADO','REQ',8,1,'COM 12');
INSERT INTO `audit_event` VALUES (67,5,1795532157793,'SOL_ASUMIDA','SOL',5,1,'Asuncion sintetica');
INSERT INTO `audit_event` VALUES (68,5,1795532157799,'REQ_COMPLEJIDAD','REQ',8,1,' -> MEDIANA. Clasificacion sintetica');
INSERT INTO `audit_event` VALUES (69,5,1795532157808,'CASO_CREADO','CAS',3,7,'Seguimiento sintetico');
INSERT INTO `audit_event` VALUES (70,5,1795532157817,'DERIVACION_SOLICITADA','FLUJO',3,7,'Competencia de otra unidad');
INSERT INTO `audit_event` VALUES (71,7,1795532157829,'CASO_CREADO','CAS',4,8,'Destino sintetico');
INSERT INTO `audit_event` VALUES (72,7,1795532157831,'CASO_CERRADO_DERIVACION','CAS',3,7,'Aceptada; sucesor 4');
INSERT INTO `audit_event` VALUES (73,7,1795532157835,'DERIVACION_ACEPTADA','FLUJO',3,2,'Aceptada');
INSERT INTO `audit_event` VALUES (74,1,1795532157845,'COM_REGISTRADA','COM',13,4,'Canal TELEFONO');
INSERT INTO `audit_event` VALUES (75,1,1795532157855,'COM_HIJA_PROPUESTA','COM',13,4,'REQ 1. Posible relacion');
INSERT INTO `audit_event` VALUES (76,4,1795532157862,'ANTECEDENTE_ADMITIDA','REQ',1,4,'COM 13. Mismo asunto');
INSERT INTO `audit_event` VALUES (77,1,1795532157871,'COM_REGISTRADA','COM',14,4,'Canal TELEFONO');
INSERT INTO `audit_event` VALUES (78,1,1795532157879,'COM_HIJA_PROPUESTA','COM',14,4,'REQ 1. Posible relacion');
INSERT INTO `audit_event` VALUES (79,4,1795532157886,'ANTECEDENTE_RECHAZADA','REQ',1,4,'COM 14. Asunto distinto');
INSERT INTO `audit_event` VALUES (80,4,1795532157894,'ANTECEDENTE_DEVUELTO','REQ',1,4,'COM 14. Reclasificar');
INSERT INTO `audit_event` VALUES (81,4,1795532157894,'COM_DEVUELTA_CLASIFICACION','COM',14,4,'Vinculo 2 rechazado. Reclasificar');
INSERT INTO `audit_event` VALUES (82,1,1795532157906,'COM_REGISTRADA','COM',15,4,'Canal TELEFONO');
INSERT INTO `audit_event` VALUES (83,1,1795532157919,'SOL_DISTRIBUIDA','SOL',6,4,'Clasificacion sintetica');
INSERT INTO `audit_event` VALUES (84,4,1795532157928,'SOL_INVALIDA','SOL',6,4,'No corresponde');
INSERT INTO `audit_event` VALUES (85,1,1795532157935,'COM_REGISTRADA','COM',16,4,'Canal TELEFONO');
INSERT INTO `audit_event` VALUES (86,1,1795532157943,'SOL_DISTRIBUIDA','SOL',7,2,'Competencia UPD');
INSERT INTO `audit_event` VALUES (87,2,1795532157953,'COM_REGISTRADA','COM',17,4,'Canal TELEFONO');
INSERT INTO `audit_event` VALUES (88,16,1795532157962,'AMBITO_ACTUALIZADO','AMBITO',14,14,'Region: 05 -> 06. Efecto territorial: 05 sin atencion. Acto sintetico: la region 05 queda sin atencion');
INSERT INTO `audit_event` VALUES (89,16,1795532157968,'COBERTURA_ACTUALIZADA','AMBITO',14,14,'Cobertura adicional: ninguna -> 07. Efecto territorial: 06 sigue atendida por REG-DEMO. Acto sintetico');
INSERT INTO `audit_event` VALUES (90,16,1795532157976,'COBERTURA_NIVEL_CENTRAL_ACTUALIZADA','AMBITO',0,NULL,'Cobertura del nivel central: ninguna -> 13. Acto sintetico');
INSERT INTO `audit_event` VALUES (91,16,1795532157982,'AMBITO_CREADO','AMBITO',15,15,'Sede PROYECTADA, region 05. Acto sintetico');
/*!40000 ALTER TABLE `audit_event` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `business_holiday`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `business_holiday` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `holiday_day` char(10) NOT NULL,
  `region_code` varchar(4) DEFAULT NULL,
  `region_key` varchar(4) NOT NULL,
  `name` varchar(120) NOT NULL,
  `created_by` bigint NOT NULL,
  `created_ms` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_business_holiday_day_region` (`holiday_day`,`region_key`),
  KEY `created_by` (`created_by`),
  CONSTRAINT `business_holiday_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `business_holiday` WRITE;
/*!40000 ALTER TABLE `business_holiday` DISABLE KEYS */;
/*!40000 ALTER TABLE `business_holiday` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `case_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `case_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `assigned_by` bigint NOT NULL,
  `assigned_ms` bigint NOT NULL,
  `acknowledged_ms` bigint DEFAULT NULL,
  `current_flag` tinyint(1) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `assigned_by` (`assigned_by`),
  KEY `ix_assignment_current` (`case_id`,`current_flag`,`acknowledged_ms`),
  CONSTRAINT `case_assignment_ibfk_1` FOREIGN KEY (`case_id`) REFERENCES `case_record` (`id`),
  CONSTRAINT `case_assignment_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `case_assignment_ibfk_3` FOREIGN KEY (`assigned_by`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `case_assignment` WRITE;
/*!40000 ALTER TABLE `case_assignment` DISABLE KEYS */;
INSERT INTO `case_assignment` VALUES (1,1,3,4,1795532157348,1795532157354,0);
INSERT INTO `case_assignment` VALUES (2,1,4,4,1795532157440,NULL,1);
INSERT INTO `case_assignment` VALUES (3,2,8,7,1795532157465,NULL,1);
INSERT INTO `case_assignment` VALUES (4,3,6,5,1795532157807,NULL,1);
INSERT INTO `case_assignment` VALUES (5,4,8,7,1795532157828,NULL,1);
/*!40000 ALTER TABLE `case_assignment` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `case_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_member` (
  `case_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `added_by` bigint NOT NULL,
  `added_ms` bigint NOT NULL,
  PRIMARY KEY (`case_id`,`user_id`),
  KEY `user_id` (`user_id`),
  KEY `added_by` (`added_by`),
  CONSTRAINT `case_member_ibfk_1` FOREIGN KEY (`case_id`) REFERENCES `case_record` (`id`),
  CONSTRAINT `case_member_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `case_member_ibfk_3` FOREIGN KEY (`added_by`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `case_member` WRITE;
/*!40000 ALTER TABLE `case_member` DISABLE KEYS */;
/*!40000 ALTER TABLE `case_member` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `case_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `folio` varchar(48) DEFAULT NULL,
  `requirement_id` bigint NOT NULL,
  `scope_id` bigint NOT NULL,
  `responsible_id` bigint NOT NULL,
  `objective` text NOT NULL,
  `matter` varchar(24) NOT NULL,
  `state` varchar(24) NOT NULL DEFAULT 'EN_GESTION',
  `created_ms` bigint NOT NULL,
  `created_by` bigint NOT NULL,
  `closed_ms` bigint DEFAULT NULL,
  `closed_by` bigint DEFAULT NULL,
  `closure_mode` varchar(32) DEFAULT NULL,
  `close_reason` text,
  `origin_case_id` bigint DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `folio` (`folio`),
  KEY `created_by` (`created_by`),
  KEY `closed_by` (`closed_by`),
  KEY `origin_case_id` (`origin_case_id`),
  KEY `ix_case_req` (`requirement_id`,`state`),
  KEY `ix_case_scope_state` (`scope_id`,`state`,`id`),
  KEY `ix_case_owner_state` (`responsible_id`,`state`,`id`),
  CONSTRAINT `case_record_ibfk_1` FOREIGN KEY (`requirement_id`) REFERENCES `requirement` (`id`),
  CONSTRAINT `case_record_ibfk_2` FOREIGN KEY (`scope_id`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `case_record_ibfk_3` FOREIGN KEY (`responsible_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `case_record_ibfk_4` FOREIGN KEY (`created_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `case_record_ibfk_5` FOREIGN KEY (`closed_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `case_record_ibfk_6` FOREIGN KEY (`origin_case_id`) REFERENCES `case_record` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `case_record` WRITE;
/*!40000 ALTER TABLE `case_record` DISABLE KEYS */;
INSERT INTO `case_record` VALUES (1,'CAS-A-000001',1,4,4,'Seguimiento sintetico','UPR','EN_GESTION',1795532157346,4,NULL,NULL,NULL,NULL,NULL,14);
INSERT INTO `case_record` VALUES (2,'CAS-A-000002',1,8,8,'Apoyo sintetico','UPD','CERRADO',1795532157463,7,1795532157476,8,'PROFESIONAL_BAJA','Apoyo entregado',NULL,1);
INSERT INTO `case_record` VALUES (3,'CAS-A-000003',8,7,6,'Seguimiento sintetico','UPR','CERRADO',1795532157806,5,1795532157830,7,'DERIVACION','Aceptada; sucesor 4',NULL,1);
INSERT INTO `case_record` VALUES (4,'CAS-A-000004',8,8,8,'Destino sintetico','UPR','EN_GESTION',1795532157827,7,NULL,NULL,NULL,NULL,3,0);
/*!40000 ALTER TABLE `case_record` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `closure_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `closure_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `requirement_id` bigint NOT NULL,
  `case_id` bigint DEFAULT NULL,
  `actor_id` bigint NOT NULL,
  `closed_ms` bigint NOT NULL,
  `mode` varchar(32) NOT NULL,
  `reason` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `requirement_id` (`requirement_id`),
  KEY `case_id` (`case_id`),
  KEY `actor_id` (`actor_id`),
  CONSTRAINT `closure_record_ibfk_1` FOREIGN KEY (`requirement_id`) REFERENCES `requirement` (`id`),
  CONSTRAINT `closure_record_ibfk_2` FOREIGN KEY (`case_id`) REFERENCES `case_record` (`id`),
  CONSTRAINT `closure_record_ibfk_3` FOREIGN KEY (`actor_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `closure_record` WRITE;
/*!40000 ALTER TABLE `closure_record` DISABLE KEYS */;
INSERT INTO `closure_record` VALUES (1,1,2,8,1795532157476,'PROFESIONAL_BAJA','Apoyo entregado');
INSERT INTO `closure_record` VALUES (2,1,1,4,1795532157485,'JEFATURA_PROPIA','Cierre confirmado');
INSERT INTO `closure_record` VALUES (3,1,NULL,4,1795532157499,'JEFATURA_PRINCIPAL','Motivo de cierre sintetico');
INSERT INTO `closure_record` VALUES (4,3,NULL,2,1795531857577,'PRIMERA_ATENCION','Orientacion entregada');
INSERT INTO `closure_record` VALUES (5,4,NULL,3,1795532157625,'PRIMERA_ATENCION','Orientacion concluida');
INSERT INTO `closure_record` VALUES (6,7,NULL,7,1795532157762,'JEFATURA_PRINCIPAL','Cierre sintetico tras transferencia');
INSERT INTO `closure_record` VALUES (7,8,3,7,1795532157830,'DERIVACION','Aceptada; sucesor 4');
/*!40000 ALTER TABLE `closure_record` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `command_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `command_log` (
  `command_key` varchar(36) NOT NULL,
  `actor_id` bigint NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `result_kind` varchar(24) NOT NULL,
  `result_id` bigint NOT NULL,
  `result_message` varchar(300) NOT NULL,
  `created_ms` bigint NOT NULL,
  PRIMARY KEY (`command_key`),
  KEY `actor_id` (`actor_id`),
  CONSTRAINT `command_log_ibfk_1` FOREIGN KEY (`actor_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `command_log` WRITE;
/*!40000 ALTER TABLE `command_log` DISABLE KEYS */;
INSERT INTO `command_log` VALUES ('07dea893-6de7-4c23-8dff-c234fbfee4d7',4,'7883c5d5a30b8aa55f6062ddfd23d77ec584f3fa5abaf6f6c7096f10bb3b0406','REQ',1,'Operacion registrada.',1795532157501);
INSERT INTO `command_log` VALUES ('0fc93f82-0c5e-4273-a947-a5b7bfeefa97',4,'b807d54c5380fae51a588a16d343a21f98455079db4a7f4b96edaaf73ff9fb23','CAS',1,'Operacion registrada.',1795532157363);
INSERT INTO `command_log` VALUES ('10011a09-fa63-40c6-a044-67387020da41',3,'fe5663d04e71c85cbe1e4837c5c026cfa234f2cd3e8174fc7d2025a12e1969fb','CAS',1,'Operacion registrada.',1795532157409);
INSERT INTO `command_log` VALUES ('10464cdd-c782-47d6-9999-cc0c3f5de336',5,'c5ce5a816a2602bd1f0872269fae840f9eb21cab88dd53c703682e88460d3dde','CAS',3,'Operacion registrada.',1795532157808);
INSERT INTO `command_log` VALUES ('12a60334-14bd-460b-b54d-0c9ba525a8fe',1,'1387b67dc8e8cd0b6a5009f64359bbb8e623adc37088a4ccca46c2591028731b','SOL',4,'Operacion registrada.',1795532157734);
INSERT INTO `command_log` VALUES ('1ad56a5a-1ac1-4b2b-9a5f-0f16bd785d5a',8,'d6161f78a017170fad0f049148b9c912b5715c342f66be79643c3a40a41b0d42','CAS',2,'Operacion registrada.',1795532157480);
INSERT INTO `command_log` VALUES ('1d0e3410-beef-4e28-9737-4db1f56eb08a',4,'593cd4c130e4b8923d841975accd9a594f37d966b76056ef137f4d96785d9c09','REQ',2,'Operacion registrada.',1795532157548);
INSERT INTO `command_log` VALUES ('23186cf6-5b02-4035-90b4-c9c912d4339e',4,'3af77a80e243a587f51cf907cb8ec5460cd2d740a5c5e55797495a53cc8c0f0a','CAS',1,'Operacion registrada.',1795532157443);
INSERT INTO `command_log` VALUES ('23d73b10-46a0-4f3f-a5a1-ab74400c5ae6',1,'ffee8d1aa4d2c4d87af7dfac6be49c66c79cdab4211eeefe9af073ca43e8c34b','SOL',6,'Operacion registrada.',1795532157922);
INSERT INTO `command_log` VALUES ('269b9663-282a-45c1-8a80-bcd142f5ef8b',1,'cddeccb201329397c5d5100ac2da738ca16f7c089f65b030ee7bf4e235048177','COM',13,'Operacion registrada.',1795532157857);
INSERT INTO `command_log` VALUES ('2b2a4f1f-97e8-482f-adf6-b4c499293d3f',1,'24281579e1f71e513595ad298adf25e5a64109bd0cd87d21f99c221b3f833091','COM',11,'Operacion registrada.',1795532157726);
INSERT INTO `command_log` VALUES ('2daaa284-4e58-4983-9610-27e9eaf73f7e',2,'3839e68d845e8d530727f9b8de7d5b6f5cae1f85bcb992f5abff2bffcce3dbb4','REQ',5,'Ficha guardada con historial. El ingreso original permanece intacto.',1795532157709);
INSERT INTO `command_log` VALUES ('30e7e106-ac60-4f02-bc16-2ecd9384d93d',1,'c21f8a8ea509be5e411f31856e4a3c20dcedad8da47833331412fca7ed392ba8','SOL',3,'Operacion registrada.',1795532157568);
INSERT INTO `command_log` VALUES ('3261fe0a-cf54-4654-8066-1949a142f921',1,'a3d1c3d2f426f71ef652642e1bdb736be46e41577855581c318513938b62804e','SOL',5,'Operacion registrada.',1795532157779);
INSERT INTO `command_log` VALUES ('32fabe12-7b08-4bbd-a5f1-bc397fc39d3e',4,'3e69d38d911bd0ab74ed271dbd721fbf933b44ae69194c6ac1a67a12e450492b','REQ',1,'Operacion registrada.',1795532157897);
INSERT INTO `command_log` VALUES ('3c1c9ddf-e5bc-454c-be4b-d0a619408048',4,'884ebd3506518422846a28b4beece3544268c86c3426c7dfd2204ea09f344dca','CAS',1,'Operacion registrada.',1795532157489);
INSERT INTO `command_log` VALUES ('3d64972e-af0d-4cba-abb1-eceee106c89a',16,'25ae6f181506da48707dc3211077d416848fe7f8f7d2447930a841d8b38691be','AMBITO',14,'Operacion registrada.',1795532157962);
INSERT INTO `command_log` VALUES ('3daa2f04-1cfd-40d3-8d03-4eecc4acbac2',4,'253a5f3d936cab2abad58995a50e6b6991309886506ee7cbe6c85091e0149db9','CAS',1,'Operacion registrada.',1795532157349);
INSERT INTO `command_log` VALUES ('3f863650-b575-4cb0-b801-12ff10a54f86',1,'322126d697774ced228d1aa9368efc3d08b3beb22d0d567b441ed03076337861','COM',13,'Operacion registrada.',1795532157847);
INSERT INTO `command_log` VALUES ('415a8fac-28a1-4bc9-87d6-5f49fb5d389c',3,'22db2a769df2c16de087d5b088f10b539c0dab9419c52019857654deff6c22fb','CAS',1,'Operacion registrada.',1795532157356);
INSERT INTO `command_log` VALUES ('41b68b4d-9c89-43dc-bd4a-dd2b1bb774ab',4,'6ab1a079df50746a542ada180e23d3ccbf16e5d1c4bbd4b1171fd5a39767ca0a','SOL',1,'Operacion registrada.',1795532157303);
INSERT INTO `command_log` VALUES ('4ae3c6bd-1101-4b53-873a-22e66a44ec26',1,'47d223f17c79f6f665dcb19d47295c12bba846c5259ac03c8487aceebf985ab1','COM',15,'Operacion registrada.',1795532157909);
INSERT INTO `command_log` VALUES ('4d4ab18d-33ff-4ffc-9f66-f15e272c2ca5',2,'0d7cbd7b58d1c0d813b95c9ba2c3a15b5a19fcfc9e3905c854c471ac63998ca4','REQ',4,'Entrega enviada. Conservas el encargo hasta su recepcion; no se cambia la jefatura principal.',1795532157610);
INSERT INTO `command_log` VALUES ('5a2ad789-93fe-44da-944f-d7c4c23c7d1f',2,'f34b74126f3f06c62df2583557f80f76e4860e553cfffa317a08c3f3844adc7d','COM',17,'Operacion registrada.',1795532157955);
INSERT INTO `command_log` VALUES ('60851e4e-bf0e-47d2-95eb-c4f27b7108f3',4,'8ae307778b6a5f1bdf310107b972bdd00da289c65a8137710d0119c318f5cc49','REQ',1,'Operacion registrada.',1795532157522);
INSERT INTO `command_log` VALUES ('66fddc61-5000-4595-91c4-9302a16e2316',5,'4896dd1222d7084ae5c135e7baccc361dcaeac8af5366f50828e693d755c7002','REQ',8,'Operacion registrada.',1795532157819);
INSERT INTO `command_log` VALUES ('68587abc-16d5-4625-9412-69706f7ffeb7',4,'184263e92c5732aed2e42c14283f4a416af1670267484f7f014e763da5c94153','REQ',1,'Operacion registrada.',1795532157887);
INSERT INTO `command_log` VALUES ('68e55408-0a06-4593-ab7f-e747c6747fa7',4,'3bddcd9fe1f76eeee30179114ec0e0f2f227b71963a72a1e77ae5615d1796fdd','REQ',1,'Operacion registrada.',1795532157331);
INSERT INTO `command_log` VALUES ('699942a5-be97-43d1-ac12-6bc313479590',4,'512b7ac7ffda1f8068d917667b89d9ed49eb1ce7146f1eadc053dd7dc4343ff9','REQ',1,'Operacion registrada.',1795532157452);
INSERT INTO `command_log` VALUES ('6b9e42f9-7552-41ab-acbe-2853a2fa1f67',2,'fe9e7ad6c89e74d98f8af28a825fa93d5e071d3eb9bda33a5090d3e5e185b67c','COM',1,'Operacion registrada.',1795532157278);
INSERT INTO `command_log` VALUES ('6c64ecf0-4b85-462b-8946-4d48dface85e',1,'5beba9b7916fe5ba4e9d176f935d56a50952689f20a52df3b6b1722a1ae28d28','COM',16,'Operacion registrada.',1795532157937);
INSERT INTO `command_log` VALUES ('6d19e9aa-adca-46f8-b660-3d5874423132',16,'d8862ee218f11457156687ca2121ef3a6401e04875be844562ef7550be39060d','AMBITO',0,'Operacion registrada.',1795532157977);
INSERT INTO `command_log` VALUES ('6da6af2e-5a50-4ee2-9f38-f47a58ffe185',7,'83d7b9fcf4d75996c120d80a775bdf018d575e2727702dab71244da7fae1b48e','SOL',1,'Operacion registrada.',1795532157311);
INSERT INTO `command_log` VALUES ('71cc9299-7f83-4f44-b809-1ef4d0544f33',3,'10032544eb32bcad8231ee845bc2d51030f980cab4edc8e6e1a0d6894a203037','CAS',1,'Operacion registrada.',1795532157417);
INSERT INTO `command_log` VALUES ('72c5a74d-1e4d-4a74-b567-b9840115d72e',4,'3c85a61372f04f7b93792d216a9cc02fe35f583adf940526cceff9ab49d407be','CAS',1,'Operacion registrada.',1795532157433);
INSERT INTO `command_log` VALUES ('7695957d-6a8c-487e-8e86-d1706d83ea1d',7,'d0a51d461caf4bea115678453a9080a4d248650d9cfb170c8ec6cbd72c90b8dc','FLUJO',2,'Operacion registrada.',1795532157757);
INSERT INTO `command_log` VALUES ('7ad2b424-2b66-4492-b12d-63789fa8fded',3,'c70870dfd3f2c07c061f66b7888fc2e30c1cb973765106e70bdc1b2345da5425','CAS',1,'Operacion registrada.',1795532157392);
INSERT INTO `command_log` VALUES ('7e47abbc-3377-4eb0-ad3a-acc6a2dd2806',16,'fab2620ea008debdfe12520f720728d7cc26d2bc3e330e85590eeaa04e8f51d1','AMBITO',15,'Operacion registrada.',1795532157982);
INSERT INTO `command_log` VALUES ('8257f95f-06c5-4d7d-97d4-8a7a529db07d',1,'670a3f5b4354b1ff7a40e8f06c9f0d72e37bf1112cd744a0f50752bf0364f450','COM',12,'Operacion registrada.',1795532157772);
INSERT INTO `command_log` VALUES ('87cf83be-7a42-48b6-9dc3-2d1a90fe43ce',3,'5ff9385f72777b0227f5ddc44709a99c5a19f0c32c897b586fde5e3e0fecdd1b','CAS',1,'Operacion registrada.',1795532157386);
INSERT INTO `command_log` VALUES ('88fa4e35-3aba-4a3e-a098-851e92f28979',1,'7fef0d2ac9b122105487986550e02ba7c073d78e8baad7531772cd4de907ae20','COM',14,'Operacion registrada.',1795532157873);
INSERT INTO `command_log` VALUES ('8ed102e2-8c10-4fb2-9aab-4d58cd7b085e',4,'1d2f485df8c3ea3c05df2d112d8c960aeb545bca9af105647b6c6746a43435fe','REQ',1,'Operacion registrada.',1795532157509);
INSERT INTO `command_log` VALUES ('91c3e00b-495a-4ae9-b82e-1f647c8c0b6d',7,'8045e29a38ac4633d0ecb8bf3a1c92591bd9e85fc8a2a052d33f882e1ba89e50','FLUJO',1,'Operacion registrada.',1795532157470);
INSERT INTO `command_log` VALUES ('9717355b-de74-43bc-a82a-378067ac0adc',2,'800e34ae2e9ddea07a17f66bf23f392f8c408bef40104171a8e8455de532477a','COM',3,'Operacion registrada.',1795532157560);
INSERT INTO `command_log` VALUES ('982a155a-3c6d-4259-85ff-46cdf1839a6f',5,'e36ff1e9af4263741bfad0ccac39db545b605efb98c92d5068d48ac38f3a078a','REQ',7,'Operacion registrada.',1795532157749);
INSERT INTO `command_log` VALUES ('9b633767-24d7-4828-b924-be6fdd70d7ba',5,'33b3f79d477adbecec9ce4a2f9b6703b1d1102e4ef50848e17bed55c2ee8abfa','REQ',7,'Operacion registrada.',1795532157742);
INSERT INTO `command_log` VALUES ('9c04f2de-3583-40d1-a3f1-0fec26223b4b',2,'5f9773114799497e415b556fda59603ed0f0f17f00c3ca32df219dad845e3305','REQ',6,'Atencion registrada. COM y REQ creados; puedes completar la ficha o entregarla.',1795532157719);
INSERT INTO `command_log` VALUES ('9d2a4f8a-a286-4a2e-99e3-f3152861f7d9',3,'51aacb91e16004d650edb619b9f6757a914d142c05798bd7ea2cf5b347b63a94','REQ',4,'Atencion resuelta y REQ cerrado sin SOL ni Caso. Resultado e historial conservados.',1795532157628);
INSERT INTO `command_log` VALUES ('a02f08fa-e748-428d-96b0-a33062e60c14',1,'ae0dff1e2520d77f3d1d09a373f1743fbef4e09c3114aaaaee365d0e43a9c411','SOL',1,'Operacion registrada.',1795532157320);
INSERT INTO `command_log` VALUES ('a0e76720-5ae4-4bb3-895f-150b32f003a8',4,'268fe9ea8ecab3269dfa40086ee7ab6460cc2b01058d46ed16d2c407e2752183','SOL',3,'Operacion registrada.',1795532157574);
INSERT INTO `command_log` VALUES ('a7c1c7f4-162a-4dfc-9e45-e23159da7c06',5,'c2cbcbfcfbfb0e6c57b4578771c2bd8f7b0b2882cad2442aaedc4640298b6952','REQ',8,'Operacion registrada.',1795532157799);
INSERT INTO `command_log` VALUES ('a8017886-4784-42d8-84fd-48ec5209a2e3',3,'646c05362de2819b174b841a399c603eb1af1f6662b040f8d3cfdda079d73e06','CAS',1,'Operacion registrada.',1795532157425);
INSERT INTO `command_log` VALUES ('ae435a6f-1367-4e92-9e06-9f809170b25b',16,'1cfc6610b05168ecf3e9fbe4cfe412f257681e1a8b6828b64eddb18cdf474612','AMBITO',14,'Operacion registrada.',1795532157968);
INSERT INTO `command_log` VALUES ('b72308d0-c448-4166-b84a-9d32bad2c62d',3,'0a3570f54a8a7fb5690adab89fd603bea9c88e17c9de2cbe2f7d5764b3bc765d','CAS',1,'Operacion registrada.',1795532157377);
INSERT INTO `command_log` VALUES ('b8c8a3c8-b0b8-469c-bd52-de641f87c058',1,'6e575f16ec1c809a35fef636b6fd6d9947ca098e58aacbe79b3d0a04ff10dd7f','SOL',1,'Operacion registrada.',1795532157292);
INSERT INTO `command_log` VALUES ('b9648f4a-660f-4d82-8ac4-13b8b8a5116b',1,'690c404a13e18367880418c632546bcc730faa28e0836ff5bf4bf04d8844f0dd','SOL',7,'Operacion registrada.',1795532157946);
INSERT INTO `command_log` VALUES ('c5d9302d-a660-4bce-b246-7418c49d3734',2,'fd10d11e4c67eb26fbb67e23b972a92917ba5a150b21582a858f0e5da56cf064','REQ',5,'Atencion registrada. COM y REQ creados; puedes completar la ficha o entregarla.',1795532157699);
INSERT INTO `command_log` VALUES ('ca98d9ee-2d77-4e3a-9e60-5620997bab43',3,'6d87cd0aef4d0b4a39fbfdfa133f1e38d39e2dd61a4ede7139565771838057b5','REQ',4,'Entrega registrada. El mismo REQ conserva su folio y antecedentes.',1795532157619);
INSERT INTO `command_log` VALUES ('d2359df6-7354-4c09-a0e5-365407ca4953',5,'818fcd6901d8890e631fd73b05fb155c82dfe1a0543d6360d2fed6923368adc5','REQ',8,'Operacion registrada.',1795532157793);
INSERT INTO `command_log` VALUES ('d23f89ae-7910-474e-a615-fccea24a209b',7,'8278c2d31f5baf7b12e12bf90a41c7d6e1b4ac4175b962f24e5f404802e61299','REQ',7,'Operacion registrada.',1795532157764);
INSERT INTO `command_log` VALUES ('d7774f66-4c5b-47f2-862a-0fa959e1824c',4,'3dc8dbdcab0f2c8c6269fd1b04b0b7d416a1237f881f04ae653b8dfa8586db1e','REQ',1,'Operacion registrada.',1795532157515);
INSERT INTO `command_log` VALUES ('e0407525-04b0-4aa1-8613-132652d3f51c',7,'c7b6463c9f8bc5b74e18691598940b05df18cbb766617aba8f94c84234894d0c','FLUJO',3,'Operacion registrada.',1795532157837);
INSERT INTO `command_log` VALUES ('e2072d3b-1539-4887-87df-bd0ebf952c15',2,'5161d82a96101d360b3842634fca5d70ff9b7a484adfd4420076f6a5fb8d9e83','REQ',4,'Atencion registrada. COM y REQ creados; puedes completar la ficha o entregarla.',1795532157602);
INSERT INTO `command_log` VALUES ('e5dfcae1-d1d2-413d-a2e9-f135608f1732',4,'73e8c7b43d860d89787995bc6f7984e79beb5f44d46b8d6fd65e33f5fc93c24a','CAS',1,'Operacion registrada.',1795532157369);
INSERT INTO `command_log` VALUES ('e70ef061-a347-4390-8aa8-a6f5dba530c3',4,'07dc8e29fde9870db8f2276911a0c1cb364fceb6fe22b3e8c9293d57ed1ee87d','REQ',1,'Operacion registrada.',1795532157863);
INSERT INTO `command_log` VALUES ('e831c13e-f065-4c92-bbc9-03cc069cbf12',2,'afc11784b41e4be5ad9d4ca3d03982ec76e9271eda94cee19e477fc7a02c6e17','REQ',3,'Operacion registrada.',1795532157584);
INSERT INTO `command_log` VALUES ('ea7271a9-fd4a-436b-a1f0-852bae14d5e8',3,'cec8b9fe448444bf1006c26082aee414d085f3a1f897a26919181f10d0a0cd31','CAS',1,'Operacion registrada.',1795532157398);
INSERT INTO `command_log` VALUES ('eb416e02-85c9-4eb4-baf1-de86078f494f',1,'55319de55516789bb775c9122163474be0d952f3593c929b19234440b301e220','COM',14,'Operacion registrada.',1795532157881);
INSERT INTO `command_log` VALUES ('ed85c30a-610b-4c73-bdeb-ee06e9665dc3',4,'a1b68a407ae749542280dc24802f1a71788a837f45920b65950983dfb08a59c0','SOL',6,'Operacion registrada.',1795532157928);
INSERT INTO `command_log` VALUES ('f008e4dd-db0a-4b4e-b02f-0a0fa8925a82',4,'9337d55e1ea3c476a32359d3e04e7e3cbb18f1c7377743d88290c65276c5a16c','REQ',1,'Operacion registrada.',1795532157338);
INSERT INTO `command_log` VALUES ('f65b9c75-1cc2-40f8-bbd7-b2e6636055a3',4,'079721893ddb15bfaaf79978d2ba015624a96e0f3c9844c00069c4568697fa5c','REQ',1,'Operacion registrada.',1795532157529);
INSERT INTO `command_log` VALUES ('fee7619b-8558-489b-b076-046ebe2c8aeb',4,'528affaa4510a2f72331ed4f8b2c34d41bec85968bbd1c82b34e1eb60737502d','COM',2,'Operacion registrada.',1795532157538);
/*!40000 ALTER TABLE `command_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `communication`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `folio` varchar(48) DEFAULT NULL,
  `external_source` varchar(48) DEFAULT NULL,
  `external_id` varchar(100) DEFAULT NULL,
  `channel` varchar(32) NOT NULL,
  `received_ms` bigint NOT NULL,
  `created_ms` bigint NOT NULL,
  `created_by` bigint NOT NULL,
  `origin_scope` bigint NOT NULL,
  `presenter` varchar(200) DEFAULT NULL,
  `contact` varchar(300) DEFAULT NULL,
  `identity_document` varchar(100) DEFAULT NULL,
  `subject` varchar(240) NOT NULL,
  `body` mediumtext NOT NULL,
  `state` varchar(24) NOT NULL DEFAULT 'NUEVA',
  `revision` bigint NOT NULL DEFAULT '0',
  `contact_email` varchar(200) DEFAULT NULL,
  `contact_phone` varchar(80) DEFAULT NULL,
  `contact_restrictions` varchar(1000) DEFAULT NULL,
  `presenter_domicile` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `folio` (`folio`),
  UNIQUE KEY `external_source` (`external_source`,`external_id`),
  KEY `created_by` (`created_by`),
  KEY `origin_scope` (`origin_scope`),
  KEY `ix_com_queue` (`state`,`origin_scope`,`id`),
  CONSTRAINT `communication_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `communication_ibfk_2` FOREIGN KEY (`origin_scope`) REFERENCES `org_scope` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `communication` WRITE;
/*!40000 ALTER TABLE `communication` DISABLE KEYS */;
INSERT INTO `communication` VALUES (1,'COM-A-000001',NULL,NULL,'TELEFONO',1795531857253,1795532157265,2,4,'','','','COM con todo el recorrido','Relato sintetico','ENVIADA',1,'','','','');
INSERT INTO `communication` VALUES (2,'COM-A-000002',NULL,NULL,'TELEFONO',1795531857531,1795532157533,4,4,'','','','COM asumida por la jefatura','Relato sintetico','ENVIADA',1,'','','','');
INSERT INTO `communication` VALUES (3,'COM-A-000003',NULL,NULL,'TELEFONO',1795531857553,1795532157556,2,4,'','','','COM duplicada','Relato sintetico','DUPLICADA',2,'','','','');
INSERT INTO `communication` VALUES (4,'COM-A-000004',NULL,NULL,'TELEFONO',1795531557576,1795532157579,2,4,'','','','Orientacion','Relato sintetico','ATENDIDA',1,'','','','');
INSERT INTO `communication` VALUES (5,'COM-A-000005',NULL,NULL,'PRESENCIAL',1795531557586,1795532157591,2,4,'',NULL,'','Contacto sintetico NNA','Contacto sintetico NNA','ATENDIDA',0,'','','','');
INSERT INTO `communication` VALUES (6,'COM-A-000006','FORMULARIO_PUBLICO_DDN','d76662e1-7441-4b6b-a731-e2bfb1d3c0bf','FORMULARIO_WEB_PUBLICO',1793793600000,1795532157647,2,4,'María José De los Ángeles Del Río González',NULL,'12345678-5','Formulario web adulto · Vulneración de derechos','Relato sintético con tildes: niño, señal, acción.','NUEVA',0,'maria.jose@example.cl','+56912345678',NULL,'Avenida Irarrázaval 1234, depto. 5, Ñuñoa, Metropolitana de Santiago');
INSERT INTO `communication` VALUES (7,'COM-A-000007','FORMULARIO_PUBLICO_DDN','66666666-6666-4666-8666-666666666670','FORMULARIO_WEB_NNA',1794744000000,1795532157669,2,4,'NOMBRE SINTETICO APELLIDO SINTETICO','Contacto principal: No tiene una forma segura de contacto','99999999-9','Formulario web NNA','Relato exclusivamente sintético para el contrato NNA SRCeI.','NUEVA',0,NULL,NULL,'No tiene una forma segura de contacto.','Metropolitana de Santiago');
INSERT INTO `communication` VALUES (8,'COM-A-000008','FORMULARIO_PUBLICO_DDN','66666666-6666-4666-8666-666666666671','FORMULARIO_WEB_NNA',1794744000000,1795532157683,2,4,'NOMBRE SINTETICO APELLIDO SINTETICO','Contacto principal: No tiene una forma segura de contacto','99999999-9','Formulario web NNA','Relato exclusivamente sintético para el contrato NNA SRCeI.','NUEVA',0,NULL,NULL,'No tiene una forma segura de contacto.','Metropolitana de Santiago');
INSERT INTO `communication` VALUES (9,'COM-A-000009',NULL,NULL,'PRESENCIAL',1795531357691,1795532157694,2,4,'Persona Sintetica',NULL,'11.111.111-1','Consulta sintetica','Consulta sintetica','ATENDIDA',0,'persona@ejemplo.cl','+56900000000','','');
INSERT INTO `communication` VALUES (10,'COM-A-000010',NULL,NULL,'PRESENCIAL',1795531457711,1795532157714,2,4,'',NULL,'','Contacto sintetico NNA 1.1','Contacto sintetico NNA 1.1','ATENDIDA',0,'','','','');
INSERT INTO `communication` VALUES (11,'COM-A-000011',NULL,NULL,'TELEFONO',1795531257721,1795532157723,1,4,'','','','Ingreso para transferir','Relato sintetico','ENVIADA',1,'','','','');
INSERT INTO `communication` VALUES (12,'COM-A-000012',NULL,NULL,'TELEFONO',1795531257766,1795532157768,1,4,'','','','Ingreso para derivar','Relato sintetico','ENVIADA',1,'','','','');
INSERT INTO `communication` VALUES (13,'COM-A-000013',NULL,NULL,'TELEFONO',1795531257840,1795532157842,1,4,'','','','Antecedente adicional','Relato sintetico','VINCULADA',1,'','','','');
INSERT INTO `communication` VALUES (14,'COM-A-000014',NULL,NULL,'TELEFONO',1795531257865,1795532157868,1,4,'','','','Antecedente devuelto','Relato sintetico','NUEVA',2,'','','','');
INSERT INTO `communication` VALUES (15,'COM-A-000015',NULL,NULL,'TELEFONO',1795531257901,1795532157903,1,4,'','','','Ingreso invalido','Relato sintetico','INVALIDA',2,'','','','');
INSERT INTO `communication` VALUES (16,'COM-A-000016',NULL,NULL,'TELEFONO',1795531257931,1795532157933,1,4,'','','','Ingreso con SOL pendiente','Relato sintetico','ENVIADA',1,'','','','');
INSERT INTO `communication` VALUES (17,'COM-A-000017',NULL,NULL,'TELEFONO',1795531257949,1795532157951,2,4,'','','','Ingreso sin encaminar','Relato sintetico','NUEVA',0,'','','','');
/*!40000 ALTER TABLE `communication` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `communication_link`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_link` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `communication_id` bigint NOT NULL,
  `requirement_id` bigint NOT NULL,
  `reason` text NOT NULL,
  `proposed_by` bigint NOT NULL,
  `proposed_ms` bigint NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PROPUESTA',
  `target_case_id` bigint DEFAULT NULL,
  `decided_by` bigint DEFAULT NULL,
  `decided_ms` bigint DEFAULT NULL,
  `decision_reason` text,
  `revision` bigint NOT NULL DEFAULT '0',
  `returned_ms` bigint DEFAULT NULL,
  `returned_by` bigint DEFAULT NULL,
  `return_reason` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `communication_id` (`communication_id`),
  KEY `requirement_id` (`requirement_id`),
  KEY `proposed_by` (`proposed_by`),
  KEY `target_case_id` (`target_case_id`),
  KEY `decided_by` (`decided_by`),
  KEY `fk_link_return_actor` (`returned_by`),
  CONSTRAINT `communication_link_ibfk_1` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`),
  CONSTRAINT `communication_link_ibfk_2` FOREIGN KEY (`requirement_id`) REFERENCES `requirement` (`id`),
  CONSTRAINT `communication_link_ibfk_3` FOREIGN KEY (`proposed_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `communication_link_ibfk_4` FOREIGN KEY (`target_case_id`) REFERENCES `case_record` (`id`),
  CONSTRAINT `communication_link_ibfk_5` FOREIGN KEY (`decided_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_link_return_actor` FOREIGN KEY (`returned_by`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `communication_link` WRITE;
/*!40000 ALTER TABLE `communication_link` DISABLE KEYS */;
INSERT INTO `communication_link` VALUES (1,13,1,'Posible relacion',1,1795532157853,'ADMITIDA',NULL,4,1795532157862,'Mismo asunto',1,NULL,NULL,NULL);
INSERT INTO `communication_link` VALUES (2,14,1,'Posible relacion',1,1795532157878,'RECHAZADA',NULL,4,1795532157886,'Asunto distinto',2,1795532157893,4,'Reclasificar');
/*!40000 ALTER TABLE `communication_link` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `communication_original`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_original` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `communication_id` bigint NOT NULL,
  `source` varchar(48) NOT NULL,
  `external_id` varchar(100) NOT NULL,
  `document_version` varchar(8) NOT NULL,
  `document_kind` varchar(24) NOT NULL,
  `received_ms` bigint NOT NULL,
  `content_hash` char(64) NOT NULL,
  `provenance_hash` char(64) NOT NULL,
  `custody_key_id` varchar(64) NOT NULL,
  `document` mediumtext NOT NULL,
  `document_sha256` char(64) NOT NULL,
  `document_bytes` bigint NOT NULL,
  `stored_ms` bigint NOT NULL,
  `stored_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `communication_id` (`communication_id`),
  UNIQUE KEY `uq_original_source` (`source`,`external_id`),
  KEY `stored_by` (`stored_by`),
  CONSTRAINT `communication_original_ibfk_1` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`),
  CONSTRAINT `communication_original_ibfk_2` FOREIGN KEY (`stored_by`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `communication_original` WRITE;
/*!40000 ALTER TABLE `communication_original` DISABLE KEYS */;
INSERT INTO `communication_original` VALUES (1,6,'FORMULARIO_PUBLICO_DDN','d76662e1-7441-4b6b-a731-e2bfb1d3c0bf','1.0','ADULTO_CU',1793793600000,'dcb39241aca24c2969d406991108860c2149e04889fcc60aa39b21dae5537aab','380e9ca0e42b7f953bedb5e5d128b8243e15a67288dd3bf052db7ea5933e1183','PRUEBA_SIGER','{\"comunicacion\":{\"version\":\"1.0\",\"id\":\"d76662e1-7441-4b6b-a731-e2bfb1d3c0bf\",\"recorridoId\":\"ab923a06-2bb4-4c2c-8a80-3459262083f2\",\"tipo\":\"ADULTO_CU\",\"recibidaEn\":\"2026-11-04T12:00:00Z\",\"avisoVersion\":\"LAB_1\",\"aceptaCanalDigital\":true,\"datos\":{\"campos\":{\"motivo\":\"vulneracion\",\"adult-region\":\"13\",\"adult-commune\":\"Ñuñoa\",\"adult-address\":\"Avenida Irarrázaval 1234, depto. 5\",\"email\":\"maria.jose@example.cl\",\"telefono\":\"+56912345678\",\"relato\":\"Relato sintético con tildes: niño, señal, acción.\",\"riesgo\":\"si\",\"relacion\":\"madre\",\"identifica-responsables\":\"true\"},\"personas\":[{\"personaId\":\"afectado-1\",\"detalles\":{\"region\":\"13\",\"comuna\":\"Ñuñoa\"}},{\"personaId\":\"relacionado-1\",\"detalles\":{\"relacion\":\"Familiar\"}},{\"personaId\":\"senalado-1\",\"detalles\":{\"alias\":\"El vecino\"}}],\"instituciones\":[{\"nombre\":\"Escuela Básica Sintética\",\"comuna\":\"Ñuñoa\",\"region\":\"13\",\"tipo\":\"Establecimiento educativo\"}],\"adjuntos\":[]},\"personas\":[{\"id\":\"presentador-adulto\",\"rol\":\"PRESENTADOR\",\"runNumero\":\"12345678\",\"runDv\":\"5\",\"modulo11\":false,\"autenticada\":true,\"estadoConsulta\":\"NO_HABILITADO\",\"consultadaEn\":null,\"consultaRef\":null,\"atributos\":{\"runNumero\":{\"valor\":\"12345678\",\"codigo\":null,\"fuente\":\"CLAVE_UNICA_USERINFO\",\"nivel\":\"IDENTIDAD_AUTENTICADA\",\"observadoEn\":\"2026-11-04T12:00:00Z\",\"evidencia\":\"50000000-0000-4000-8000-000000000001\"},\"runDv\":{\"valor\":\"5\",\"codigo\":null,\"fuente\":\"CLAVE_UNICA_USERINFO\",\"nivel\":\"IDENTIDAD_AUTENTICADA\",\"observadoEn\":\"2026-11-04T12:00:00Z\",\"evidencia\":\"50000000-0000-4000-8000-000000000001\"},\"nombres\":{\"valor\":\"María José De los Ángeles\",\"codigo\":null,\"fuente\":\"CLAVE_UNICA_USERINFO\",\"nivel\":\"IDENTIDAD_AUTENTICADA\",\"observadoEn\":\"2026-11-04T12:00:00Z\",\"evidencia\":\"50000000-0000-4000-8000-000000000001\"},\"apellidos\":{\"valor\":\"Del Río González\",\"codigo\":null,\"fuente\":\"CLAVE_UNICA_USERINFO\",\"nivel\":\"IDENTIDAD_AUTENTICADA\",\"observadoEn\":\"2026-11-04T12:00:00Z\",\"evidencia\":\"50000000-0000-4000-8000-000000000001\"},\"primerNombre\":{\"valor\":\"María José\",\"codigo\":null,\"fuente\":\"CLAVE_UNICA_USERINFO\",\"nivel\":\"IDENTIDAD_AUTENTICADA\",\"observadoEn\":\"2026-11-04T12:00:00Z\",\"evidencia\":\"50000000-0000-4000-8000-000000000001\"},\"segundoNombre\":{\"valor\":\"De los Ángeles\",\"codigo\":null,\"fuente\":\"CLAVE_UNICA_USERINFO\",\"nivel\":\"IDENTIDAD_AUTENTICADA\",\"observadoEn\":\"2026-11-04T12:00:00Z\",\"evidencia\":\"50000000-0000-4000-8000-000000000001\"}},\"discrepancias\":{}},{\"id\":\"afectado-1\",\"rol\":\"AFECTADO_NNA\",\"runNumero\":null,\"runDv\":null,\"modulo11\":false,\"autenticada\":false,\"estadoConsulta\":\"NO_HABILITADO\",\"consultadaEn\":null,\"consultaRef\":null,\"atributos\":{\"primerNombre\":{\"valor\":\"Tomás\",\"codigo\":null,\"fuente\":\"DECLARACION_CIUDADANA\",\"nivel\":\"DECLARADO_NO_CONTRASTADO\",\"observadoEn\":\"2026-11-04T12:00:00Z\",\"evidencia\":\"50000000-0000-4000-8000-000000000001\"},\"edad\":{\"valor\":\"9\",\"codigo\":null,\"fuente\":\"DECLARACION_CIUDADANA\",\"nivel\":\"DECLARADO_NO_CONTRASTADO\",\"observadoEn\":\"2026-11-04T12:00:00Z\",\"evidencia\":\"50000000-0000-4000-8000-000000000001\"}},\"discrepancias\":{}},{\"id\":\"relacionado-1\",\"rol\":\"ADULTO_RELACIONADO\",\"runNumero\":null,\"runDv\":null,\"modulo11\":false,\"autenticada\":false,\"estadoConsulta\":\"NO_HABILITADO\",\"consultadaEn\":null,\"consultaRef\":null,\"atributos\":{\"primerNombre\":{\"valor\":\"Rosa\",\"codigo\":null,\"fuente\":\"DECLARACION_CIUDADANA\",\"nivel\":\"DECLARADO_NO_CONTRASTADO\",\"observadoEn\":\"2026-11-04T12:00:00Z\",\"evidencia\":\"50000000-0000-4000-8000-000000000001\"}},\"discrepancias\":{}},{\"id\":\"senalado-1\",\"rol\":\"PERSONA_SENALADA\",\"runNumero\":null,\"runDv\":null,\"modulo11\":false,\"autenticada\":false,\"estadoConsulta\":\"NO_HABILITADO\",\"consultadaEn\":null,\"consultaRef\":null,\"atributos\":{},\"discrepancias\":{}}],\"adjuntos\":[]},\"sha256\":\"dcb39241aca24c2969d406991108860c2149e04889fcc60aa39b21dae5537aab\",\"huellaProcedencia\":\"380e9ca0e42b7f953bedb5e5d128b8243e15a67288dd3bf052db7ea5933e1183\",\"custodia\":{\"algoritmo\":\"Ed25519\",\"keyId\":\"PRUEBA_SIGER\",\"payload\":\"eyJ2ZXJzaW9uIjoiMS4wIiwiaWQiOiJkNzY2NjJlMS03NDQxLTRiNmItYTczMS1lMmJmYjFkM2MwYmYiLCJyZWNpYmlkYUVuIjoiMjAyNi0xMS0wNFQxMjowMDowMFoiLCJ0aXBvUmVjZXBjaW9uIjoiQ1VTVE9ESUFfUFVCTElDQSIsImh1ZWxsYUNvbnRlbmlkbyI6ImRjYjM5MjQxYWNhMjRjMjk2OWQ0MDY5OTExMDg4NjBjMjE0OWUwNDg4OWZjYzYwYWEzOWIyMWRhZTU1MzdhYWIiLCJodWVsbGFQcm9jZWRlbmNpYSI6IjM4MGU5Y2EwZTQyYjdmOTUzYmVkYjVlNWQxMjhiODI0M2UxNWE2NzI4OGRkM2JmMDUyZGI3ZWE1OTMzZTExODMiLCJlbWlzb3IiOiJGT1JNVUxBUklPX1BVQkxJQ09fREROIiwiYXZpc29WZXJzaW9uIjoiTEFCXzEifQ\",\"firma\":\"usDyHqmt8GYaX0kMZ7Q8kPh1wcEIpZRlYmn6nRlN6BozZHFh6sT5LguReoO-aKgINLZz0UmhzmQp8djBQPQ9CQ\"}}','7b88b80be9befd2423fde677fab79062bf48759bd966ab5a992a1142c6ce5980',4462,1795532157649,2);
INSERT INTO `communication_original` VALUES (2,7,'FORMULARIO_PUBLICO_DDN','66666666-6666-4666-8666-666666666670','1.0','NNA',1794744000000,'277f40bf49f67c6ffecdcd31ee8de29d3c612f5821533020441f72c83840ec56','dfa631787f07fee7d3b6e80d615f4086a214986f8192687ea29a8929cadcf91f','nna-srcei-public-test','{\n  \"comunicacion\" : {\n    \"version\" : \"1.0\",\n    \"id\" : \"66666666-6666-4666-8666-666666666670\",\n    \"recorridoId\" : \"66666666-6666-4666-8666-666666666667\",\n    \"tipo\" : \"NNA\",\n    \"recibidaEn\" : \"2026-11-15T12:00:00Z\",\n    \"avisoVersion\" : \"NNA_SRCEI_SINTETICO_1\",\n    \"aceptaCanalDigital\" : false,\n    \"datos\" : {\n      \"campos\" : {\n        \"nna-primary-method\" : \"unsafe\",\n        \"nna-region\" : \"13\",\n        \"nna-story\" : \"Relato exclusivamente sintético para el contrato NNA SRCeI.\"\n      },\n      \"personas\" : [ ],\n      \"instituciones\" : [ ],\n      \"adjuntos\" : [ ]\n    },\n    \"personas\" : [ {\n      \"id\" : \"presentador-nna\",\n      \"rol\" : \"PRESENTADOR\",\n      \"runNumero\" : \"99999999\",\n      \"runDv\" : \"9\",\n      \"modulo11\" : true,\n      \"autenticada\" : false,\n      \"estadoConsulta\" : \"PARCIAL\",\n      \"consultadaEn\" : \"2026-11-15T12:00:00Z\",\n      \"consultaRef\" : \"66666666-6666-4666-8666-666666666666\",\n      \"atributos\" : {\n        \"primerApellido\" : {\n          \"valor\" : \"APELLIDO SINTETICO\",\n          \"codigo\" : null,\n          \"fuente\" : \"SRCEI\",\n          \"nivel\" : \"CONTRASTADO_EN_FUENTE\",\n          \"observadoEn\" : \"2026-11-15T12:00:00Z\",\n          \"evidencia\" : \"66666666-6666-4666-8666-666666666666\"\n        },\n        \"fechaNacimiento\" : {\n          \"valor\" : \"2012-03-04\",\n          \"codigo\" : null,\n          \"fuente\" : \"SRCEI\",\n          \"nivel\" : \"CONTRASTADO_EN_FUENTE\",\n          \"observadoEn\" : \"2026-11-15T12:00:00Z\",\n          \"evidencia\" : \"66666666-6666-4666-8666-666666666666\"\n        },\n        \"runDv\" : {\n          \"valor\" : \"9\",\n          \"codigo\" : null,\n          \"fuente\" : \"SRCEI\",\n          \"nivel\" : \"CONTRASTADO_EN_FUENTE\",\n          \"observadoEn\" : \"2026-11-15T12:00:00Z\",\n          \"evidencia\" : \"66666666-6666-4666-8666-666666666666\"\n        },\n        \"runNumero\" : {\n          \"valor\" : \"99999999\",\n          \"codigo\" : null,\n          \"fuente\" : \"SRCEI\",\n          \"nivel\" : \"CONTRASTADO_EN_FUENTE\",\n          \"observadoEn\" : \"2026-11-15T12:00:00Z\",\n          \"evidencia\" : \"66666666-6666-4666-8666-666666666666\"\n        },\n        \"nombres\" : {\n          \"valor\" : \"NOMBRE SINTETICO\",\n          \"codigo\" : null,\n          \"fuente\" : \"SRCEI\",\n          \"nivel\" : \"CONTRASTADO_EN_FUENTE\",\n          \"observadoEn\" : \"2026-11-15T12:00:00Z\",\n          \"evidencia\" : \"66666666-6666-4666-8666-666666666666\"\n        }\n      },\n      \"discrepancias\" : { }\n    } ],\n    \"adjuntos\" : [ ]\n  },\n  \"sha256\" : \"277f40bf49f67c6ffecdcd31ee8de29d3c612f5821533020441f72c83840ec56\",\n  \"huellaProcedencia\" : \"dfa631787f07fee7d3b6e80d615f4086a214986f8192687ea29a8929cadcf91f\",\n  \"custodia\" : {\n    \"algoritmo\" : \"Ed25519\",\n    \"keyId\" : \"nna-srcei-public-test\",\n    \"payload\" : \"eyJ2ZXJzaW9uIjoiMS4wIiwiaWQiOiI2NjY2NjY2Ni02NjY2LTQ2NjYtODY2Ni02NjY2NjY2NjY2NzAiLCJyZWNpYmlkYUVuIjoiMjAyNi0xMS0xNVQxMjowMDowMFoiLCJ0aXBvUmVjZXBjaW9uIjoiQ1VTVE9ESUFfUFVCTElDQSIsImh1ZWxsYUNvbnRlbmlkbyI6IjI3N2Y0MGJmNDlmNjdjNmZmZWNkY2QzMWVlOGRlMjlkM2M2MTJmNTgyMTUzMzAyMDQ0MWY3MmM4Mzg0MGVjNTYiLCJodWVsbGFQcm9jZWRlbmNpYSI6ImRmYTYzMTc4N2YwN2ZlZTdkM2I2ZTgwZDYxNWY0MDg2YTIxNDk4NmY4MTkyNjg3ZWEyOWE4OTI5Y2FkY2Y5MWYiLCJlbWlzb3IiOiJGT1JNVUxBUklPX1BVQkxJQ09fREROIiwiYXZpc29WZXJzaW9uIjoiTk5BX1NSQ0VJX1NJTlRFVElDT18xIn0\",\n    \"firma\" : \"HzPqujLt3G17ottAbAPBp4gDMhmU0REu9w_Wp2gMMwCS1KL1WWEz51-9_zZ2EXqMhfZhFb_tn2CqWz8GB239Cw\"\n  }\n}\n','f7cbc491ad0426ab7afcccd898f0854bf8aa28cf17d12466ae28e38d95ee9380',3327,1795532157670,2);
INSERT INTO `communication_original` VALUES (3,8,'FORMULARIO_PUBLICO_DDN','66666666-6666-4666-8666-666666666671','1.1','NNA',1794744000000,'e547458b2fd6c49a8a824c6e4c6545136d564d9c77892397106663a7dea91ecb','dfa631787f07fee7d3b6e80d615f4086a214986f8192687ea29a8929cadcf91f','nna-srcei-public-test','{\n  \"comunicacion\" : {\n    \"version\" : \"1.1\",\n    \"id\" : \"66666666-6666-4666-8666-666666666671\",\n    \"recorridoId\" : \"66666666-6666-4666-8666-666666666667\",\n    \"tipo\" : \"NNA\",\n    \"recibidaEn\" : \"2026-11-15T12:00:00Z\",\n    \"avisoVersion\" : \"NNA_SRCEI_SINTETICO_1\",\n    \"aceptaCanalDigital\" : false,\n    \"datos\" : {\n      \"campos\" : {\n        \"nna-primary-method\" : \"unsafe\",\n        \"nna-region\" : \"13\",\n        \"nna-story\" : \"Relato exclusivamente sintético para el contrato NNA SRCeI.\"\n      },\n      \"personas\" : [ ],\n      \"instituciones\" : [ ],\n      \"adjuntos\" : [ \"66666666-6666-4666-8666-666666666668\" ]\n    },\n    \"personas\" : [ {\n      \"id\" : \"presentador-nna\",\n      \"rol\" : \"PRESENTADOR\",\n      \"runNumero\" : \"99999999\",\n      \"runDv\" : \"9\",\n      \"modulo11\" : true,\n      \"autenticada\" : false,\n      \"estadoConsulta\" : \"PARCIAL\",\n      \"consultadaEn\" : \"2026-11-15T12:00:00Z\",\n      \"consultaRef\" : \"66666666-6666-4666-8666-666666666666\",\n      \"atributos\" : {\n        \"primerApellido\" : {\n          \"valor\" : \"APELLIDO SINTETICO\",\n          \"codigo\" : null,\n          \"fuente\" : \"SRCEI\",\n          \"nivel\" : \"CONTRASTADO_EN_FUENTE\",\n          \"observadoEn\" : \"2026-11-15T12:00:00Z\",\n          \"evidencia\" : \"66666666-6666-4666-8666-666666666666\"\n        },\n        \"fechaNacimiento\" : {\n          \"valor\" : \"2012-03-04\",\n          \"codigo\" : null,\n          \"fuente\" : \"SRCEI\",\n          \"nivel\" : \"CONTRASTADO_EN_FUENTE\",\n          \"observadoEn\" : \"2026-11-15T12:00:00Z\",\n          \"evidencia\" : \"66666666-6666-4666-8666-666666666666\"\n        },\n        \"runDv\" : {\n          \"valor\" : \"9\",\n          \"codigo\" : null,\n          \"fuente\" : \"SRCEI\",\n          \"nivel\" : \"CONTRASTADO_EN_FUENTE\",\n          \"observadoEn\" : \"2026-11-15T12:00:00Z\",\n          \"evidencia\" : \"66666666-6666-4666-8666-666666666666\"\n        },\n        \"runNumero\" : {\n          \"valor\" : \"99999999\",\n          \"codigo\" : null,\n          \"fuente\" : \"SRCEI\",\n          \"nivel\" : \"CONTRASTADO_EN_FUENTE\",\n          \"observadoEn\" : \"2026-11-15T12:00:00Z\",\n          \"evidencia\" : \"66666666-6666-4666-8666-666666666666\"\n        },\n        \"nombres\" : {\n          \"valor\" : \"NOMBRE SINTETICO\",\n          \"codigo\" : null,\n          \"fuente\" : \"SRCEI\",\n          \"nivel\" : \"CONTRASTADO_EN_FUENTE\",\n          \"observadoEn\" : \"2026-11-15T12:00:00Z\",\n          \"evidencia\" : \"66666666-6666-4666-8666-666666666666\"\n        }\n      },\n      \"discrepancias\" : { }\n    } ],\n    \"adjuntos\" : [ {\n      \"id\" : \"66666666-6666-4666-8666-666666666668\",\n      \"nombre\" : \"sintetico.txt\",\n      \"tamanio\" : 45,\n      \"sha256\" : \"b5a546c94e508a25afa40d7b4f4e2a7e43c51eedfb477bc6816f14a5d10ab8bb\",\n      \"tipo\" : \"text/plain\",\n      \"estado\" : \"VALIDADO\"\n    } ]\n  },\n  \"sha256\" : \"e547458b2fd6c49a8a824c6e4c6545136d564d9c77892397106663a7dea91ecb\",\n  \"huellaProcedencia\" : \"dfa631787f07fee7d3b6e80d615f4086a214986f8192687ea29a8929cadcf91f\",\n  \"custodia\" : {\n    \"algoritmo\" : \"Ed25519\",\n    \"keyId\" : \"nna-srcei-public-test\",\n    \"payload\" : \"eyJ2ZXJzaW9uIjoiMS4wIiwiaWQiOiI2NjY2NjY2Ni02NjY2LTQ2NjYtODY2Ni02NjY2NjY2NjY2NzEiLCJyZWNpYmlkYUVuIjoiMjAyNi0xMS0xNVQxMjowMDowMFoiLCJ0aXBvUmVjZXBjaW9uIjoiQ1VTVE9ESUFfUFVCTElDQSIsImh1ZWxsYUNvbnRlbmlkbyI6ImU1NDc0NThiMmZkNmM0OWE4YTgyNGM2ZTRjNjU0NTEzNmQ1NjRkOWM3Nzg5MjM5NzEwNjY2M2E3ZGVhOTFlY2IiLCJodWVsbGFQcm9jZWRlbmNpYSI6ImRmYTYzMTc4N2YwN2ZlZTdkM2I2ZTgwZDYxNWY0MDg2YTIxNDk4NmY4MTkyNjg3ZWEyOWE4OTI5Y2FkY2Y5MWYiLCJlbWlzb3IiOiJGT1JNVUxBUklPX1BVQkxJQ09fREROIiwiYXZpc29WZXJzaW9uIjoiTk5BX1NSQ0VJX1NJTlRFVElDT18xIn0\",\n    \"firma\" : \"EiQlGccijh27-_gRkpuQvy8zSJqMl3SOWg-ecfs2Rjt8ij1XWLNMF4gpaO5FtPmD_DTdS8izbcug04qxwryEDg\"\n  }\n}\n','3ae1f376e5b980260ff02e5ddabf571cdfbbb52edeb2b9fb7c1537ee68d4ff87',3625,1795532157685,2);
/*!40000 ALTER TABLE `communication_original` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `communication_person`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_person` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `communication_id` bigint NOT NULL,
  `person_role` varchar(24) NOT NULL,
  `name` varchar(200) DEFAULT NULL,
  `identity_document` varchar(100) DEFAULT NULL,
  `relationship` varchar(200) DEFAULT NULL,
  `location_text` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `communication_id` (`communication_id`),
  CONSTRAINT `communication_person_ibfk_1` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `communication_person` WRITE;
/*!40000 ALTER TABLE `communication_person` DISABLE KEYS */;
/*!40000 ALTER TABLE `communication_person` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `document_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `communication_id` bigint DEFAULT NULL,
  `requirement_id` bigint DEFAULT NULL,
  `case_id` bigint DEFAULT NULL,
  `name` varchar(180) NOT NULL,
  `storage_key` varchar(80) NOT NULL,
  `sha256` char(64) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `content_type` varchar(120) NOT NULL,
  `uploaded_by` bigint NOT NULL,
  `created_ms` bigint NOT NULL,
  `reserved` tinyint(1) NOT NULL DEFAULT '0',
  `quarantined` tinyint(1) NOT NULL DEFAULT '1',
  `note_id` bigint DEFAULT NULL,
  `document_type` varchar(32) NOT NULL DEFAULT 'OTRO',
  `description` varchar(500) DEFAULT NULL,
  `previous_document_id` bigint DEFAULT NULL,
  `upload_key` varchar(36) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `storage_key` (`storage_key`),
  UNIQUE KEY `ux_document_upload` (`upload_key`),
  UNIQUE KEY `ux_document_previous` (`previous_document_id`),
  KEY `communication_id` (`communication_id`),
  KEY `requirement_id` (`requirement_id`),
  KEY `case_id` (`case_id`),
  KEY `uploaded_by` (`uploaded_by`),
  KEY `fk_document_note` (`note_id`),
  CONSTRAINT `document_item_ibfk_1` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`),
  CONSTRAINT `document_item_ibfk_2` FOREIGN KEY (`requirement_id`) REFERENCES `requirement` (`id`),
  CONSTRAINT `document_item_ibfk_3` FOREIGN KEY (`case_id`) REFERENCES `case_record` (`id`),
  CONSTRAINT `document_item_ibfk_4` FOREIGN KEY (`uploaded_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_document_note` FOREIGN KEY (`note_id`) REFERENCES `management_note` (`id`),
  CONSTRAINT `fk_document_previous` FOREIGN KEY (`previous_document_id`) REFERENCES `document_item` (`id`),
  CONSTRAINT `document_item_chk_1` CHECK ((((`communication_id` is not null) and (`requirement_id` is null) and (`case_id` is null)) or ((`communication_id` is null) and (`requirement_id` is not null) and (`case_id` is null)) or ((`communication_id` is null) and (`requirement_id` is null) and (`case_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `document_item` WRITE;
/*!40000 ALTER TABLE `document_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_item` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `document_scan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_scan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_id` bigint NOT NULL,
  `sha256` char(64) NOT NULL,
  `engine` varchar(16) NOT NULL,
  `engine_version` varchar(120) DEFAULT NULL,
  `verdict` varchar(12) NOT NULL,
  `exit_code` int DEFAULT NULL,
  `detail` varchar(64) DEFAULT NULL,
  `requested_by` bigint NOT NULL,
  `started_ms` bigint NOT NULL,
  `finished_ms` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `requested_by` (`requested_by`),
  KEY `ix_document_scan_document` (`document_id`,`id`),
  CONSTRAINT `document_scan_ibfk_1` FOREIGN KEY (`document_id`) REFERENCES `document_item` (`id`),
  CONSTRAINT `document_scan_ibfk_2` FOREIGN KEY (`requested_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `document_scan_chk_1` CHECK ((`verdict` in (_utf8mb4'LIMPIO',_utf8mb4'INFECTADO',_utf8mb4'ERROR')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `document_scan` WRITE;
/*!40000 ALTER TABLE `document_scan` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_scan` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `external_attribute`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `external_attribute` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `person_id` bigint NOT NULL,
  `attr_key` varchar(255) NOT NULL,
  `discrepancy` tinyint(1) NOT NULL,
  `value_text` mediumtext,
  `code_text` varchar(255) DEFAULT NULL,
  `meaning` varchar(120) DEFAULT NULL,
  `source` varchar(32) NOT NULL,
  `level_code` varchar(64) NOT NULL,
  `observed_ms` bigint NOT NULL,
  `evidence` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_external_attribute_person` (`person_id`,`id`),
  CONSTRAINT `external_attribute_ibfk_1` FOREIGN KEY (`person_id`) REFERENCES `external_person` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `external_attribute` WRITE;
/*!40000 ALTER TABLE `external_attribute` DISABLE KEYS */;
INSERT INTO `external_attribute` VALUES (1,1,'runNumero',0,'12345678',NULL,NULL,'CLAVE_UNICA_USERINFO','IDENTIDAD_AUTENTICADA',1793793600000,'50000000-0000-4000-8000-000000000001');
INSERT INTO `external_attribute` VALUES (2,1,'runDv',0,'5',NULL,NULL,'CLAVE_UNICA_USERINFO','IDENTIDAD_AUTENTICADA',1793793600000,'50000000-0000-4000-8000-000000000001');
INSERT INTO `external_attribute` VALUES (3,1,'nombres',0,'María José De los Ángeles',NULL,NULL,'CLAVE_UNICA_USERINFO','IDENTIDAD_AUTENTICADA',1793793600000,'50000000-0000-4000-8000-000000000001');
INSERT INTO `external_attribute` VALUES (4,1,'apellidos',0,'Del Río González',NULL,NULL,'CLAVE_UNICA_USERINFO','IDENTIDAD_AUTENTICADA',1793793600000,'50000000-0000-4000-8000-000000000001');
INSERT INTO `external_attribute` VALUES (5,1,'primerNombre',0,'María José',NULL,NULL,'CLAVE_UNICA_USERINFO','IDENTIDAD_AUTENTICADA',1793793600000,'50000000-0000-4000-8000-000000000001');
INSERT INTO `external_attribute` VALUES (6,1,'segundoNombre',0,'De los Ángeles',NULL,NULL,'CLAVE_UNICA_USERINFO','IDENTIDAD_AUTENTICADA',1793793600000,'50000000-0000-4000-8000-000000000001');
INSERT INTO `external_attribute` VALUES (7,2,'primerNombre',0,'Tomás',NULL,NULL,'DECLARACION_CIUDADANA','DECLARADO_NO_CONTRASTADO',1793793600000,'50000000-0000-4000-8000-000000000001');
INSERT INTO `external_attribute` VALUES (8,2,'edad',0,'9',NULL,NULL,'DECLARACION_CIUDADANA','DECLARADO_NO_CONTRASTADO',1793793600000,'50000000-0000-4000-8000-000000000001');
INSERT INTO `external_attribute` VALUES (9,3,'primerNombre',0,'Rosa',NULL,NULL,'DECLARACION_CIUDADANA','DECLARADO_NO_CONTRASTADO',1793793600000,'50000000-0000-4000-8000-000000000001');
INSERT INTO `external_attribute` VALUES (10,5,'primerApellido',0,'APELLIDO SINTETICO',NULL,NULL,'SRCEI','CONTRASTADO_EN_FUENTE',1794744000000,'66666666-6666-4666-8666-666666666666');
INSERT INTO `external_attribute` VALUES (11,5,'fechaNacimiento',0,'2012-03-04',NULL,NULL,'SRCEI','CONTRASTADO_EN_FUENTE',1794744000000,'66666666-6666-4666-8666-666666666666');
INSERT INTO `external_attribute` VALUES (12,5,'runDv',0,'9',NULL,NULL,'SRCEI','CONTRASTADO_EN_FUENTE',1794744000000,'66666666-6666-4666-8666-666666666666');
INSERT INTO `external_attribute` VALUES (13,5,'runNumero',0,'99999999',NULL,NULL,'SRCEI','CONTRASTADO_EN_FUENTE',1794744000000,'66666666-6666-4666-8666-666666666666');
INSERT INTO `external_attribute` VALUES (14,5,'nombres',0,'NOMBRE SINTETICO',NULL,NULL,'SRCEI','CONTRASTADO_EN_FUENTE',1794744000000,'66666666-6666-4666-8666-666666666666');
INSERT INTO `external_attribute` VALUES (15,6,'primerApellido',0,'APELLIDO SINTETICO',NULL,NULL,'SRCEI','CONTRASTADO_EN_FUENTE',1794744000000,'66666666-6666-4666-8666-666666666666');
INSERT INTO `external_attribute` VALUES (16,6,'fechaNacimiento',0,'2012-03-04',NULL,NULL,'SRCEI','CONTRASTADO_EN_FUENTE',1794744000000,'66666666-6666-4666-8666-666666666666');
INSERT INTO `external_attribute` VALUES (17,6,'runDv',0,'9',NULL,NULL,'SRCEI','CONTRASTADO_EN_FUENTE',1794744000000,'66666666-6666-4666-8666-666666666666');
INSERT INTO `external_attribute` VALUES (18,6,'runNumero',0,'99999999',NULL,NULL,'SRCEI','CONTRASTADO_EN_FUENTE',1794744000000,'66666666-6666-4666-8666-666666666666');
INSERT INTO `external_attribute` VALUES (19,6,'nombres',0,'NOMBRE SINTETICO',NULL,NULL,'SRCEI','CONTRASTADO_EN_FUENTE',1794744000000,'66666666-6666-4666-8666-666666666666');
/*!40000 ALTER TABLE `external_attribute` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `external_document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `external_document` (
  `document_id` bigint NOT NULL,
  `communication_id` bigint NOT NULL,
  `operation_id` varchar(36) NOT NULL,
  `attachment_id` varchar(36) NOT NULL,
  `media_type` varchar(255) NOT NULL,
  PRIMARY KEY (`document_id`),
  UNIQUE KEY `external_document_attachment` (`communication_id`,`attachment_id`),
  CONSTRAINT `external_document_ibfk_1` FOREIGN KEY (`document_id`) REFERENCES `document_item` (`id`),
  CONSTRAINT `external_document_ibfk_2` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `external_document` WRITE;
/*!40000 ALTER TABLE `external_document` DISABLE KEYS */;
/*!40000 ALTER TABLE `external_document` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `external_field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `external_field` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `communication_id` bigint NOT NULL,
  `field_group` varchar(12) NOT NULL,
  `person_key` varchar(64) DEFAULT NULL,
  `item` int NOT NULL,
  `field_key` varchar(255) NOT NULL,
  `value_text` mediumtext,
  PRIMARY KEY (`id`),
  KEY `ix_external_field_com` (`communication_id`,`id`),
  CONSTRAINT `external_field_ibfk_1` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`),
  CONSTRAINT `external_field_chk_1` CHECK ((`field_group` in (_utf8mb4'CAMPO',_utf8mb4'PERSONA',_utf8mb4'INSTITUCION')))
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `external_field` WRITE;
/*!40000 ALTER TABLE `external_field` DISABLE KEYS */;
INSERT INTO `external_field` VALUES (1,6,'CAMPO',NULL,0,'motivo','vulneracion');
INSERT INTO `external_field` VALUES (2,6,'CAMPO',NULL,0,'adult-region','13');
INSERT INTO `external_field` VALUES (3,6,'CAMPO',NULL,0,'adult-commune','Ñuñoa');
INSERT INTO `external_field` VALUES (4,6,'CAMPO',NULL,0,'adult-address','Avenida Irarrázaval 1234, depto. 5');
INSERT INTO `external_field` VALUES (5,6,'CAMPO',NULL,0,'email','maria.jose@example.cl');
INSERT INTO `external_field` VALUES (6,6,'CAMPO',NULL,0,'telefono','+56912345678');
INSERT INTO `external_field` VALUES (7,6,'CAMPO',NULL,0,'relato','Relato sintético con tildes: niño, señal, acción.');
INSERT INTO `external_field` VALUES (8,6,'CAMPO',NULL,0,'riesgo','si');
INSERT INTO `external_field` VALUES (9,6,'CAMPO',NULL,0,'relacion','madre');
INSERT INTO `external_field` VALUES (10,6,'CAMPO',NULL,0,'identifica-responsables','true');
INSERT INTO `external_field` VALUES (11,6,'PERSONA','afectado-1',0,'region','13');
INSERT INTO `external_field` VALUES (12,6,'PERSONA','afectado-1',0,'comuna','Ñuñoa');
INSERT INTO `external_field` VALUES (13,6,'PERSONA','relacionado-1',1,'relacion','Familiar');
INSERT INTO `external_field` VALUES (14,6,'PERSONA','senalado-1',2,'alias','El vecino');
INSERT INTO `external_field` VALUES (15,6,'INSTITUCION',NULL,0,'nombre','Escuela Básica Sintética');
INSERT INTO `external_field` VALUES (16,6,'INSTITUCION',NULL,0,'comuna','Ñuñoa');
INSERT INTO `external_field` VALUES (17,6,'INSTITUCION',NULL,0,'region','13');
INSERT INTO `external_field` VALUES (18,6,'INSTITUCION',NULL,0,'tipo','Establecimiento educativo');
INSERT INTO `external_field` VALUES (19,7,'CAMPO',NULL,0,'nna-primary-method','unsafe');
INSERT INTO `external_field` VALUES (20,7,'CAMPO',NULL,0,'nna-region','13');
INSERT INTO `external_field` VALUES (21,7,'CAMPO',NULL,0,'nna-story','Relato exclusivamente sintético para el contrato NNA SRCeI.');
INSERT INTO `external_field` VALUES (22,8,'CAMPO',NULL,0,'nna-primary-method','unsafe');
INSERT INTO `external_field` VALUES (23,8,'CAMPO',NULL,0,'nna-region','13');
INSERT INTO `external_field` VALUES (24,8,'CAMPO',NULL,0,'nna-story','Relato exclusivamente sintético para el contrato NNA SRCeI.');
/*!40000 ALTER TABLE `external_field` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `external_person`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `external_person` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `communication_id` bigint NOT NULL,
  `person_key` varchar(64) NOT NULL,
  `person_index` int NOT NULL,
  `person_role` varchar(24) NOT NULL,
  `authenticated` tinyint(1) NOT NULL,
  `given_names` mediumtext,
  `given_names_source` varchar(32) DEFAULT NULL,
  `surnames` mediumtext,
  `surnames_source` varchar(32) DEFAULT NULL,
  `run_number` varchar(9) DEFAULT NULL,
  `run_dv` char(1) DEFAULT NULL,
  `run_source` varchar(32) DEFAULT NULL,
  `run_valid` tinyint(1) DEFAULT NULL,
  `identification_type` varchar(40) DEFAULT NULL,
  `foreign_document_type` varchar(255) DEFAULT NULL,
  `foreign_document_number` varchar(255) DEFAULT NULL,
  `birth_date` char(10) DEFAULT NULL,
  `birth_date_source` varchar(32) DEFAULT NULL,
  `age_at_intake` int DEFAULT NULL,
  `declared_age` int DEFAULT NULL,
  `registry_state` varchar(24) DEFAULT NULL,
  `registry_checked_ms` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_external_person` (`communication_id`,`person_key`),
  CONSTRAINT `external_person_ibfk_1` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `external_person` WRITE;
/*!40000 ALTER TABLE `external_person` DISABLE KEYS */;
INSERT INTO `external_person` VALUES (1,6,'presentador-adulto',0,'PRESENTADOR',1,'María José De los Ángeles','CLAVE_UNICA_USERINFO','Del Río González','CLAVE_UNICA_USERINFO','12345678','5','CLAVE_UNICA_USERINFO',1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'NO_HABILITADO',NULL);
INSERT INTO `external_person` VALUES (2,6,'afectado-1',1,'AFECTADO_NNA',0,'Tomás','DECLARACION_CIUDADANA',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,9,'NO_HABILITADO',NULL);
INSERT INTO `external_person` VALUES (3,6,'relacionado-1',2,'ADULTO_RELACIONADO',0,'Rosa','DECLARACION_CIUDADANA',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'NO_HABILITADO',NULL);
INSERT INTO `external_person` VALUES (4,6,'senalado-1',3,'PERSONA_SENALADA',0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'NO_HABILITADO',NULL);
INSERT INTO `external_person` VALUES (5,7,'presentador-nna',0,'PRESENTADOR',0,'NOMBRE SINTETICO','SRCEI','APELLIDO SINTETICO','SRCEI','99999999','9','SRCEI',1,NULL,NULL,NULL,'2012-03-04','SRCEI',14,NULL,'PARCIAL',1794744000000);
INSERT INTO `external_person` VALUES (6,8,'presentador-nna',0,'PRESENTADOR',0,'NOMBRE SINTETICO','SRCEI','APELLIDO SINTETICO','SRCEI','99999999','9','SRCEI',1,NULL,NULL,NULL,'2012-03-04','SRCEI',14,NULL,'PARCIAL',1794744000000);
/*!40000 ALTER TABLE `external_person` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `flow_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kind` varchar(24) NOT NULL,
  `requirement_id` bigint NOT NULL,
  `origin_case_id` bigint DEFAULT NULL,
  `origin_scope` bigint NOT NULL,
  `target_scope` bigint NOT NULL,
  `requested_by` bigint NOT NULL,
  `created_ms` bigint NOT NULL,
  `reason` text NOT NULL,
  `objective` text,
  `state` varchar(16) NOT NULL DEFAULT 'PENDIENTE',
  `decided_by` bigint DEFAULT NULL,
  `decided_ms` bigint DEFAULT NULL,
  `decision_reason` text,
  `successor_case_id` bigint DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `requirement_id` (`requirement_id`),
  KEY `origin_case_id` (`origin_case_id`),
  KEY `origin_scope` (`origin_scope`),
  KEY `requested_by` (`requested_by`),
  KEY `decided_by` (`decided_by`),
  KEY `successor_case_id` (`successor_case_id`),
  KEY `ix_flow_target_state` (`target_scope`,`state`,`id`),
  CONSTRAINT `flow_request_ibfk_1` FOREIGN KEY (`requirement_id`) REFERENCES `requirement` (`id`),
  CONSTRAINT `flow_request_ibfk_2` FOREIGN KEY (`origin_case_id`) REFERENCES `case_record` (`id`),
  CONSTRAINT `flow_request_ibfk_3` FOREIGN KEY (`origin_scope`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `flow_request_ibfk_4` FOREIGN KEY (`target_scope`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `flow_request_ibfk_5` FOREIGN KEY (`requested_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `flow_request_ibfk_6` FOREIGN KEY (`decided_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `flow_request_ibfk_7` FOREIGN KEY (`successor_case_id`) REFERENCES `case_record` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `flow_request` WRITE;
/*!40000 ALTER TABLE `flow_request` DISABLE KEYS */;
INSERT INTO `flow_request` VALUES (1,'COLABORACION',1,NULL,4,2,4,1795532157449,'Colaboracion sintetica','Apoyo sintetico','ACEPTADA',7,1795532157466,'Aceptada',2,1);
INSERT INTO `flow_request` VALUES (2,'TRANSFERENCIA',7,NULL,1,2,5,1795532157747,'Corresponde a UPD','','ACEPTADA',7,1795532157754,'Aceptada',NULL,1);
INSERT INTO `flow_request` VALUES (3,'DERIVACION',8,3,7,2,5,1795532157816,'Competencia de otra unidad','Destino sintetico','ACEPTADA',7,1795532157834,'Aceptada',4,1);
/*!40000 ALTER TABLE `flow_request` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','siger a','SQL','V1__siger_a.sql',242221211,'siger_prueba','2026-11-24 14:55:50',681,1);
INSERT INTO `flyway_schema_history` VALUES (2,'2','agenda recordatorios','SQL','V2__agenda_recordatorios.sql',47838886,'siger_prueba','2026-11-24 14:55:50',88,1);
INSERT INTO `flyway_schema_history` VALUES (3,'3','sol area sugerida','SQL','V3__sol_area_sugerida.sql',357863808,'siger_prueba','2026-11-24 14:55:50',83,1);
INSERT INTO `flyway_schema_history` VALUES (4,'4','bandejas y continuidad','SQL','V4__bandejas_y_continuidad.sql',-940417168,'siger_prueba','2026-11-24 14:55:51',955,1);
INSERT INTO `flyway_schema_history` VALUES (5,'5','atencion progresiva','SQL','V5__atencion_progresiva.sql',-1250707106,'siger_prueba','2026-11-24 14:55:51',123,1);
INSERT INTO `flyway_schema_history` VALUES (6,'6','original comunicacion externa','SQL','V6__original_comunicacion_externa.sql',309025193,'siger_prueba','2026-11-24 14:55:51',24,1);
INSERT INTO `flyway_schema_history` VALUES (7,'7','normalizacion comunicacion externa','SQL','V7__normalizacion_comunicacion_externa.sql',-179316656,'siger_prueba','2026-11-24 14:55:52',72,1);
INSERT INTO `flyway_schema_history` VALUES (8,'8','consumidor formulario','SQL','V8__consumidor_formulario.sql',-2079685744,'siger_prueba','2026-11-24 14:55:52',123,1);
INSERT INTO `flyway_schema_history` VALUES (9,'9','operacion continua','SQL','V9__operacion_continua.sql',1258741787,'siger_prueba','2026-11-24 14:55:52',99,1);
INSERT INTO `flyway_schema_history` VALUES (10,'10','analisis documental','SQL','V10__analisis_documental.sql',-1341567348,'siger_prueba','2026-11-24 14:55:52',32,1);
INSERT INTO `flyway_schema_history` VALUES (11,'11','sedes regionales','SQL','V11__sedes_regionales.sql',782194518,'siger_prueba','2026-11-24 14:55:52',298,1);
INSERT INTO `flyway_schema_history` VALUES (12,'12','indice personas','SQL','V12__indice_personas.sql',1534134575,'siger_prueba','2026-11-24 14:55:52',47,1);
INSERT INTO `flyway_schema_history` VALUES (13,'13','calendario habil','SQL','V13__calendario_habil.sql',549796993,'siger_prueba','2026-11-24 14:55:52',27,1);
INSERT INTO `flyway_schema_history` VALUES (14,'14','hitos','SQL','V14__hitos.sql',511592049,'siger_prueba','2026-11-24 14:55:52',98,1);
INSERT INTO `flyway_schema_history` VALUES (15,'15','cobertura territorial','SQL','V15__cobertura_territorial.sql',1537035602,'siger_prueba','2026-11-24 14:55:52',36,1);
INSERT INTO `flyway_schema_history` VALUES (16,'16','region version ficha','SQL','V16__region_version_ficha.sql',144975735,'siger_prueba','2026-11-24 14:55:52',53,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `formulario_consumer_lock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `formulario_consumer_lock` (
  `id` int NOT NULL,
  `holder` varchar(36) DEFAULT NULL,
  `until_ms` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `formulario_consumer_lock` WRITE;
/*!40000 ALTER TABLE `formulario_consumer_lock` DISABLE KEYS */;
INSERT INTO `formulario_consumer_lock` VALUES (1,NULL,0);
/*!40000 ALTER TABLE `formulario_consumer_lock` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `formulario_reception`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `formulario_reception` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operation_id` varchar(36) NOT NULL,
  `state` varchar(16) NOT NULL,
  `document_version` varchar(8) DEFAULT NULL,
  `channel` varchar(40) DEFAULT NULL,
  `content_hash` char(64) DEFAULT NULL,
  `provenance_hash` char(64) DEFAULT NULL,
  `lease_id` varchar(36) DEFAULT NULL,
  `lease_until_ms` bigint DEFAULT NULL,
  `attempt` int NOT NULL DEFAULT '0',
  `communication_id` bigint DEFAULT NULL,
  `institutional_id` varchar(36) DEFAULT NULL,
  `folio_number` bigint DEFAULT NULL,
  `folio` varchar(40) DEFAULT NULL,
  `registered_ms` bigint DEFAULT NULL,
  `receipt` mediumtext,
  `receipt_key_id` varchar(64) DEFAULT NULL,
  `ack_pending` tinyint(1) NOT NULL DEFAULT '0',
  `ack_attempts` int NOT NULL DEFAULT '0',
  `ack_next_ms` bigint NOT NULL DEFAULT '0',
  `ack_blocked` tinyint(1) NOT NULL DEFAULT '0',
  `acked_ms` bigint DEFAULT NULL,
  `last_error` varchar(64) DEFAULT NULL,
  `created_ms` bigint NOT NULL,
  `updated_ms` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `operation_id` (`operation_id`),
  UNIQUE KEY `communication_id` (`communication_id`),
  UNIQUE KEY `institutional_id` (`institutional_id`),
  CONSTRAINT `formulario_reception_ibfk_1` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`),
  CONSTRAINT `formulario_reception_state` CHECK ((`state` in (_utf8mb4'RECHAZADA',_utf8mb4'REGISTRADA',_utf8mb4'CONFIRMADA')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `formulario_reception` WRITE;
/*!40000 ALTER TABLE `formulario_reception` DISABLE KEYS */;
/*!40000 ALTER TABLE `formulario_reception` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `inbox_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inbox_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `at_ms` bigint NOT NULL,
  `text_value` varchar(300) NOT NULL,
  `resource_type` varchar(24) NOT NULL,
  `resource_id` bigint NOT NULL,
  `read_ms` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_notice_unread` (`user_id`,`read_ms`,`id`),
  CONSTRAINT `inbox_notice_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `inbox_notice` WRITE;
/*!40000 ALTER TABLE `inbox_notice` DISABLE KEYS */;
INSERT INTO `inbox_notice` VALUES (1,1,1795532157278,'Hay un ingreso pendiente de clasificacion.','COM',1,NULL);
INSERT INTO `inbox_notice` VALUES (2,4,1795532157291,'Tienes una SOL pendiente de asumir.','SOL',1,NULL);
INSERT INTO `inbox_notice` VALUES (3,7,1795532157302,'Una SOL fue remitida a tu ambito.','SOL',1,NULL);
INSERT INTO `inbox_notice` VALUES (4,1,1795532157311,'Una SOL fue devuelta a clasificacion.','COM',1,NULL);
INSERT INTO `inbox_notice` VALUES (5,4,1795532157320,'Una SOL fue remitida a tu ambito.','SOL',1,NULL);
INSERT INTO `inbox_notice` VALUES (6,3,1795532157331,'Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.','REQ',1,NULL);
INSERT INTO `inbox_notice` VALUES (7,3,1795532157349,'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (8,3,1795532157377,'Tienes una tarea asignada en un Caso.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (9,3,1795532157385,'Tienes una tarea asignada en un Caso.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (10,4,1795532157407,'Un Caso requiere revision de termino.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (11,4,1795532157415,'El responsable retiro su solicitud de cierre; el Caso vuelve a gestion.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (12,4,1795532157424,'Un Caso requiere revision de termino.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (13,3,1795532157432,'La jefatura devolvio el termino con observaciones.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (14,4,1795532157441,'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (15,3,1795532157442,'Cambio tu asignacion; se conserva tu autoria historica.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (16,7,1795532157451,'Tienes una peticion de colaboracion.','FLUJO',1,NULL);
INSERT INTO `inbox_notice` VALUES (17,8,1795532157466,'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.','CAS',2,NULL);
INSERT INTO `inbox_notice` VALUES (18,4,1795532157469,'Se resolvio una peticion de colaboracion.','FLUJO',1,NULL);
INSERT INTO `inbox_notice` VALUES (19,8,1795532157478,'El Caso fue cerrado. El cierre del REQ es una decision separada.','CAS',2,NULL);
INSERT INTO `inbox_notice` VALUES (20,4,1795532157479,'Cambio el estado de un Caso del REQ.','REQ',1,NULL);
INSERT INTO `inbox_notice` VALUES (21,4,1795532157487,'El Caso fue cerrado. El cierre del REQ es una decision separada.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (22,4,1795532157489,'Cambio el estado de un Caso del REQ.','REQ',1,NULL);
INSERT INTO `inbox_notice` VALUES (23,4,1795532157501,'Un REQ fue cerrado expresamente por su jefatura principal.','REQ',1,NULL);
INSERT INTO `inbox_notice` VALUES (24,4,1795532157509,'Hay una solicitud de reapertura.','REQ',1,NULL);
INSERT INTO `inbox_notice` VALUES (25,4,1795532157522,'Hay una solicitud de reapertura.','REQ',1,NULL);
INSERT INTO `inbox_notice` VALUES (26,1,1795532157538,'Hay un ingreso pendiente de clasificacion.','COM',2,NULL);
INSERT INTO `inbox_notice` VALUES (27,3,1795532157547,'Se asigno un REQ por asuncion directa de jefatura.','REQ',2,NULL);
INSERT INTO `inbox_notice` VALUES (28,1,1795532157559,'Hay un ingreso pendiente de clasificacion.','COM',3,NULL);
INSERT INTO `inbox_notice` VALUES (29,4,1795532157568,'Tienes una SOL pendiente de asumir.','SOL',3,NULL);
INSERT INTO `inbox_notice` VALUES (30,3,1795532157610,'Tienes una primera atencion para recibir y continuar.','REQ',4,NULL);
INSERT INTO `inbox_notice` VALUES (31,2,1795532157617,'Tu entrega de primera atencion fue recibida.','REQ',4,NULL);
INSERT INTO `inbox_notice` VALUES (32,4,1795532157619,'Se registro continuidad de primera atencion; el principal del REQ no cambia.','REQ',4,NULL);
INSERT INTO `inbox_notice` VALUES (33,4,1795532157628,'Una primera atencion se resolvio sin Caso.','REQ',4,NULL);
INSERT INTO `inbox_notice` VALUES (34,1,1795532157726,'Hay un ingreso pendiente de clasificacion.','COM',11,NULL);
INSERT INTO `inbox_notice` VALUES (35,5,1795532157734,'Tienes una SOL pendiente de asumir.','SOL',4,NULL);
INSERT INTO `inbox_notice` VALUES (36,6,1795532157741,'Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.','REQ',7,NULL);
INSERT INTO `inbox_notice` VALUES (37,7,1795532157749,'Tienes una peticion de transferencia.','FLUJO',2,NULL);
INSERT INTO `inbox_notice` VALUES (38,5,1795532157756,'Se resolvio una peticion de transferencia.','FLUJO',2,NULL);
INSERT INTO `inbox_notice` VALUES (39,5,1795532157764,'Un REQ fue cerrado expresamente por su jefatura principal.','REQ',7,NULL);
INSERT INTO `inbox_notice` VALUES (40,1,1795532157771,'Hay un ingreso pendiente de clasificacion.','COM',12,NULL);
INSERT INTO `inbox_notice` VALUES (41,5,1795532157779,'Tienes una SOL pendiente de asumir.','SOL',5,NULL);
INSERT INTO `inbox_notice` VALUES (42,6,1795532157793,'Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.','REQ',8,NULL);
INSERT INTO `inbox_notice` VALUES (43,6,1795532157808,'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.','CAS',3,NULL);
INSERT INTO `inbox_notice` VALUES (44,7,1795532157818,'Tienes una peticion de derivacion.','FLUJO',3,NULL);
INSERT INTO `inbox_notice` VALUES (45,8,1795532157829,'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.','CAS',4,NULL);
INSERT INTO `inbox_notice` VALUES (46,6,1795532157831,'El Caso fue cerrado. El cierre del REQ es una decision separada.','CAS',3,NULL);
INSERT INTO `inbox_notice` VALUES (47,5,1795532157833,'Cambio el estado de un Caso del REQ.','REQ',8,NULL);
INSERT INTO `inbox_notice` VALUES (48,5,1795532157837,'Se resolvio una peticion de derivacion.','FLUJO',3,NULL);
INSERT INTO `inbox_notice` VALUES (49,1,1795532157847,'Hay un ingreso pendiente de clasificacion.','COM',13,NULL);
INSERT INTO `inbox_notice` VALUES (50,4,1795532157856,'Una COM fue propuesta como antecedente de tu REQ.','REQ',1,NULL);
INSERT INTO `inbox_notice` VALUES (51,1,1795532157873,'Hay un ingreso pendiente de clasificacion.','COM',14,NULL);
INSERT INTO `inbox_notice` VALUES (52,4,1795532157880,'Una COM fue propuesta como antecedente de tu REQ.','REQ',1,NULL);
INSERT INTO `inbox_notice` VALUES (53,1,1795532157896,'Una COM rechazada como antecedente requiere nueva clasificacion.','COM',14,NULL);
INSERT INTO `inbox_notice` VALUES (54,1,1795532157908,'Hay un ingreso pendiente de clasificacion.','COM',15,NULL);
INSERT INTO `inbox_notice` VALUES (55,4,1795532157922,'Tienes una SOL pendiente de asumir.','SOL',6,NULL);
INSERT INTO `inbox_notice` VALUES (56,1,1795532157937,'Hay un ingreso pendiente de clasificacion.','COM',16,NULL);
INSERT INTO `inbox_notice` VALUES (57,7,1795532157946,'Tienes una SOL pendiente de asumir.','SOL',7,NULL);
INSERT INTO `inbox_notice` VALUES (58,1,1795532157955,'Hay un ingreso pendiente de clasificacion.','COM',17,NULL);
/*!40000 ALTER TABLE `inbox_notice` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `intake_upload`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `intake_upload` (
  `token` varchar(36) NOT NULL,
  `user_id` bigint NOT NULL,
  `scope_id` bigint NOT NULL,
  `name` varchar(180) NOT NULL,
  `storage_key` varchar(80) NOT NULL,
  `sha256` char(64) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `created_ms` bigint NOT NULL,
  `communication_id` bigint DEFAULT NULL,
  PRIMARY KEY (`token`),
  UNIQUE KEY `storage_key` (`storage_key`),
  KEY `scope_id` (`scope_id`),
  KEY `communication_id` (`communication_id`),
  KEY `ix_intake_upload_owner` (`user_id`,`communication_id`,`created_ms`),
  CONSTRAINT `intake_upload_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `intake_upload_ibfk_2` FOREIGN KEY (`scope_id`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `intake_upload_ibfk_3` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `intake_upload` WRITE;
/*!40000 ALTER TABLE `intake_upload` DISABLE KEYS */;
/*!40000 ALTER TABLE `intake_upload` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `integration_control`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_control` (
  `id` bigint NOT NULL,
  `name` varchar(32) NOT NULL,
  `paused` tinyint(1) NOT NULL DEFAULT '0',
  `revision` bigint NOT NULL DEFAULT '0',
  `updated_ms` bigint NOT NULL DEFAULT '0',
  `updated_by` bigint DEFAULT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  KEY `updated_by` (`updated_by`),
  CONSTRAINT `integration_control_ibfk_1` FOREIGN KEY (`updated_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `integration_control_chk_1` CHECK ((`name` in (_utf8mb4'FORMULARIO_PULL',_utf8mb4'CORREO_GRAPH')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `integration_control` WRITE;
/*!40000 ALTER TABLE `integration_control` DISABLE KEYS */;
INSERT INTO `integration_control` VALUES (1,'FORMULARIO_PULL',0,0,0,NULL,NULL);
INSERT INTO `integration_control` VALUES (2,'CORREO_GRAPH',0,0,0,NULL,NULL);
/*!40000 ALTER TABLE `integration_control` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `integration_status`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_status` (
  `name` varchar(32) NOT NULL,
  `state` varchar(32) NOT NULL DEFAULT 'SIN_ACTIVIDAD',
  `holder` varchar(36) DEFAULT NULL,
  `cycles` bigint NOT NULL DEFAULT '0',
  `failures` int NOT NULL DEFAULT '0',
  `last_cycle_ms` bigint DEFAULT NULL,
  `last_success_ms` bigint DEFAULT NULL,
  `next_ms` bigint DEFAULT NULL,
  `last_error` varchar(64) DEFAULT NULL,
  `updated_ms` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`name`),
  CONSTRAINT `integration_status_chk_1` CHECK ((`name` in (_utf8mb4'FORMULARIO_PULL',_utf8mb4'CORREO_GRAPH')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `integration_status` WRITE;
/*!40000 ALTER TABLE `integration_status` DISABLE KEYS */;
INSERT INTO `integration_status` VALUES ('CORREO_GRAPH','SIN_ACTIVIDAD',NULL,0,0,NULL,NULL,NULL,NULL,0);
INSERT INTO `integration_status` VALUES ('FORMULARIO_PULL','SIN_ACTIVIDAD',NULL,0,0,NULL,NULL,NULL,NULL,0);
/*!40000 ALTER TABLE `integration_status` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mail_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mail_attempt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `outbox_id` bigint NOT NULL,
  `claim_id` char(36) NOT NULL,
  `address` varchar(200) NOT NULL,
  `started_ms` bigint NOT NULL,
  `finished_ms` bigint DEFAULT NULL,
  `state` varchar(16) NOT NULL,
  `phase` varchar(24) NOT NULL,
  `http_status` int DEFAULT NULL,
  `request_id` varchar(36) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `claim_id` (`claim_id`),
  KEY `ix_mail_attempt_outbox` (`outbox_id`,`id`),
  CONSTRAINT `mail_attempt_ibfk_1` FOREIGN KEY (`outbox_id`) REFERENCES `mail_outbox` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mail_attempt` WRITE;
/*!40000 ALTER TABLE `mail_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `mail_attempt` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mail_outbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mail_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_id` bigint NOT NULL,
  `recipient_id` bigint NOT NULL,
  `correlation_id` char(36) NOT NULL,
  `state` varchar(16) NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `next_ms` bigint NOT NULL,
  `claim_id` char(36) DEFAULT NULL,
  `lease_until_ms` bigint DEFAULT NULL,
  `last_phase` varchar(24) DEFAULT NULL,
  `last_http` int DEFAULT NULL,
  `last_request_id` varchar(36) DEFAULT NULL,
  `accepted_ms` bigint DEFAULT NULL,
  `resolution` varchar(16) DEFAULT NULL,
  `resolved_by` bigint DEFAULT NULL,
  `resolved_ms` bigint DEFAULT NULL,
  `created_ms` bigint NOT NULL,
  `updated_ms` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `notice_id` (`notice_id`),
  UNIQUE KEY `correlation_id` (`correlation_id`),
  KEY `recipient_id` (`recipient_id`),
  KEY `resolved_by` (`resolved_by`),
  KEY `ix_mail_outbox_due` (`state`,`next_ms`,`id`),
  CONSTRAINT `mail_outbox_ibfk_1` FOREIGN KEY (`notice_id`) REFERENCES `inbox_notice` (`id`),
  CONSTRAINT `mail_outbox_ibfk_2` FOREIGN KEY (`recipient_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `mail_outbox_ibfk_3` FOREIGN KEY (`resolved_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `mail_outbox_chk_1` CHECK ((`state` in (_utf8mb4'PENDIENTE',_utf8mb4'EN_INTENTO',_utf8mb4'ACEPTADO',_utf8mb4'REINTENTABLE',_utf8mb4'AMBIGUO',_utf8mb4'RECHAZADO',_utf8mb4'SUPRIMIDO',_utf8mb4'DESCARTADO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mail_outbox` WRITE;
/*!40000 ALTER TABLE `mail_outbox` DISABLE KEYS */;
/*!40000 ALTER TABLE `mail_outbox` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `management_note`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `management_note` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `requirement_id` bigint NOT NULL,
  `case_id` bigint DEFAULT NULL,
  `author_id` bigint NOT NULL,
  `effective_ms` bigint NOT NULL,
  `registered_ms` bigint NOT NULL,
  `body` mediumtext NOT NULL,
  `reserved` tinyint(1) NOT NULL DEFAULT '0',
  `rectifies_id` bigint DEFAULT NULL,
  `correction_reason` text,
  PRIMARY KEY (`id`),
  KEY `requirement_id` (`requirement_id`),
  KEY `case_id` (`case_id`),
  KEY `author_id` (`author_id`),
  KEY `rectifies_id` (`rectifies_id`),
  CONSTRAINT `management_note_ibfk_1` FOREIGN KEY (`requirement_id`) REFERENCES `requirement` (`id`),
  CONSTRAINT `management_note_ibfk_2` FOREIGN KEY (`case_id`) REFERENCES `case_record` (`id`),
  CONSTRAINT `management_note_ibfk_3` FOREIGN KEY (`author_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `management_note_ibfk_4` FOREIGN KEY (`rectifies_id`) REFERENCES `management_note` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `management_note` WRITE;
/*!40000 ALTER TABLE `management_note` DISABLE KEYS */;
INSERT INTO `management_note` VALUES (1,3,NULL,2,1795531857577,1795532157582,'ORIENTACION: Orientacion entregada',0,NULL,NULL);
INSERT INTO `management_note` VALUES (2,4,NULL,3,1795532152621,1795532157625,'ORIENTACION: Orientacion concluida',0,NULL,NULL);
/*!40000 ALTER TABLE `management_note` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `milestone`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `milestone` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event` varchar(32) NOT NULL,
  `resource_type` varchar(16) NOT NULL,
  `resource_id` bigint NOT NULL,
  `communication_id` bigint NOT NULL,
  `requirement_id` bigint DEFAULT NULL,
  `case_id` bigint DEFAULT NULL,
  `actor_id` bigint NOT NULL,
  `subject_user` bigint DEFAULT NULL,
  `scope_id` bigint DEFAULT NULL,
  `at_ms` bigint NOT NULL,
  `detail` varchar(32) DEFAULT NULL,
  `reconstructed` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `case_id` (`case_id`),
  KEY `actor_id` (`actor_id`),
  KEY `subject_user` (`subject_user`),
  KEY `scope_id` (`scope_id`),
  KEY `ix_milestone_event_at` (`event`,`at_ms`),
  KEY `ix_milestone_requirement` (`requirement_id`),
  KEY `ix_milestone_resource` (`resource_type`,`resource_id`),
  KEY `ix_milestone_communication` (`communication_id`),
  CONSTRAINT `milestone_ibfk_1` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`),
  CONSTRAINT `milestone_ibfk_2` FOREIGN KEY (`requirement_id`) REFERENCES `requirement` (`id`),
  CONSTRAINT `milestone_ibfk_3` FOREIGN KEY (`case_id`) REFERENCES `case_record` (`id`),
  CONSTRAINT `milestone_ibfk_4` FOREIGN KEY (`actor_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `milestone_ibfk_5` FOREIGN KEY (`subject_user`) REFERENCES `staff` (`id`),
  CONSTRAINT `milestone_ibfk_6` FOREIGN KEY (`scope_id`) REFERENCES `org_scope` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=78 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `milestone` WRITE;
/*!40000 ALTER TABLE `milestone` DISABLE KEYS */;
INSERT INTO `milestone` VALUES (1,'COM_REGISTRADA','COM',1,1,NULL,NULL,2,NULL,4,1795532157265,NULL,0);
INSERT INTO `milestone` VALUES (2,'SOL_DISTRIBUIDA','SOL',1,1,NULL,NULL,1,NULL,4,1795532157287,NULL,0);
INSERT INTO `milestone` VALUES (3,'SOL_REDIRIGIDA','SOL',1,1,NULL,NULL,4,NULL,2,1795532157299,NULL,0);
INSERT INTO `milestone` VALUES (4,'SOL_DEVUELTA','SOL',1,1,NULL,NULL,7,NULL,2,1795532157308,NULL,0);
INSERT INTO `milestone` VALUES (5,'SOL_REDIRIGIDA','SOL',1,1,NULL,NULL,1,NULL,4,1795532157317,NULL,0);
INSERT INTO `milestone` VALUES (6,'REQ_CREADO','REQ',1,1,1,NULL,4,3,4,1795532157328,NULL,0);
INSERT INTO `milestone` VALUES (7,'SOL_ASUMIDA','SOL',1,1,1,NULL,4,3,4,1795532157328,NULL,0);
INSERT INTO `milestone` VALUES (8,'CASO_CREADO','CAS',1,1,1,1,4,3,4,1795532157346,NULL,0);
INSERT INTO `milestone` VALUES (9,'CASO_ASIGNADO','ASIGNACION',1,1,1,1,4,3,4,1795532157348,NULL,0);
INSERT INTO `milestone` VALUES (10,'ASIGNACION_ACUSADA','ASIGNACION',1,1,1,1,3,3,4,1795532157354,NULL,0);
INSERT INTO `milestone` VALUES (11,'CASO_ESPERA','CAS',1,1,1,1,4,NULL,4,1795532157362,NULL,0);
INSERT INTO `milestone` VALUES (12,'CASO_REANUDADO','CAS',1,1,1,1,4,NULL,4,1795532157368,NULL,0);
INSERT INTO `milestone` VALUES (13,'TAREA_CREADA','TAREA',1,1,1,1,3,3,4,1795532157375,NULL,0);
INSERT INTO `milestone` VALUES (14,'TAREA_CREADA','TAREA',2,1,1,1,3,3,4,1795532157384,NULL,0);
INSERT INTO `milestone` VALUES (15,'TAREA_COMPLETADA','TAREA',1,1,1,1,3,3,4,1795532157391,NULL,0);
INSERT INTO `milestone` VALUES (16,'TAREA_CANCELADA','TAREA',2,1,1,1,3,3,4,1795532157397,NULL,0);
INSERT INTO `milestone` VALUES (17,'CIERRE_SOLICITADO','CAS',1,1,1,1,3,NULL,4,1795532157408,NULL,0);
INSERT INTO `milestone` VALUES (18,'CIERRE_RETIRADO','CAS',1,1,1,1,3,NULL,4,1795532157416,NULL,0);
INSERT INTO `milestone` VALUES (19,'CIERRE_SOLICITADO','CAS',1,1,1,1,3,NULL,4,1795532157424,NULL,0);
INSERT INTO `milestone` VALUES (20,'CIERRE_DEVUELTO','CAS',1,1,1,1,4,NULL,4,1795532157432,NULL,0);
INSERT INTO `milestone` VALUES (21,'CASO_ASIGNADO','ASIGNACION',2,1,1,1,4,4,4,1795532157440,NULL,0);
INSERT INTO `milestone` VALUES (22,'FLUJO_SOLICITADO','FLUJO',1,1,1,NULL,4,NULL,2,1795532157449,'COLABORACION',0);
INSERT INTO `milestone` VALUES (23,'CASO_CREADO','CAS',2,1,1,2,7,8,8,1795532157463,NULL,0);
INSERT INTO `milestone` VALUES (24,'CASO_ASIGNADO','ASIGNACION',3,1,1,2,7,8,8,1795532157465,NULL,0);
INSERT INTO `milestone` VALUES (25,'FLUJO_DECIDIDO','FLUJO',1,1,1,NULL,7,NULL,2,1795532157466,'ACEPTADA',0);
INSERT INTO `milestone` VALUES (26,'CASO_CERRADO','CAS',2,1,1,2,8,NULL,8,1795532157476,'PROFESIONAL_BAJA',0);
INSERT INTO `milestone` VALUES (27,'CASO_CERRADO','CAS',1,1,1,1,4,NULL,4,1795532157485,'JEFATURA_PROPIA',0);
INSERT INTO `milestone` VALUES (28,'REQ_CERRADO','REQ',1,1,1,NULL,4,NULL,4,1795532157499,'JEFATURA_PRINCIPAL',0);
INSERT INTO `milestone` VALUES (29,'REQ_REACTIVADO','REQ',1,1,1,NULL,4,NULL,4,1795532157514,NULL,0);
INSERT INTO `milestone` VALUES (30,'CASO_REABIERTO','CAS',1,1,1,1,4,NULL,4,1795532157528,NULL,0);
INSERT INTO `milestone` VALUES (31,'COM_REGISTRADA','COM',2,2,NULL,NULL,4,NULL,4,1795532157533,NULL,0);
INSERT INTO `milestone` VALUES (32,'REQ_CREADO','REQ',2,2,2,NULL,4,3,4,1795532157545,NULL,0);
INSERT INTO `milestone` VALUES (33,'SOL_ASUMIDA','SOL',2,2,2,NULL,4,3,4,1795532157545,'DIRECTA',0);
INSERT INTO `milestone` VALUES (34,'COM_REGISTRADA','COM',3,3,NULL,NULL,2,NULL,4,1795532157556,NULL,0);
INSERT INTO `milestone` VALUES (35,'SOL_DISTRIBUIDA','SOL',3,3,NULL,NULL,1,NULL,4,1795532157566,NULL,0);
INSERT INTO `milestone` VALUES (36,'SOL_DESCARTADA','SOL',3,3,NULL,NULL,4,NULL,4,1795532157574,'DUPLICADA',0);
INSERT INTO `milestone` VALUES (37,'COM_REGISTRADA','COM',4,4,NULL,NULL,2,NULL,4,1795532157579,NULL,0);
INSERT INTO `milestone` VALUES (38,'REQ_CREADO','REQ',3,4,3,NULL,2,2,4,1795532157581,NULL,0);
INSERT INTO `milestone` VALUES (39,'REQ_CERRADO','REQ',3,4,3,NULL,2,NULL,4,1795532157581,'PRIMERA_ATENCION',0);
INSERT INTO `milestone` VALUES (40,'COM_REGISTRADA','COM',5,5,NULL,NULL,2,NULL,4,1795532157591,NULL,0);
INSERT INTO `milestone` VALUES (41,'REQ_CREADO','REQ',4,5,4,NULL,2,2,4,1795532157593,NULL,0);
INSERT INTO `milestone` VALUES (42,'ENTREGA_ENVIADA','ENTREGA',1,5,4,NULL,2,3,4,1795532157609,NULL,0);
INSERT INTO `milestone` VALUES (43,'ENTREGA_DECIDIDA','ENTREGA',1,5,4,NULL,3,3,4,1795532157616,'RECIBIDA',0);
INSERT INTO `milestone` VALUES (44,'REQ_CERRADO','REQ',4,5,4,NULL,3,NULL,4,1795532157625,'PRIMERA_ATENCION',0);
INSERT INTO `milestone` VALUES (45,'COM_REGISTRADA','COM',6,6,NULL,NULL,2,NULL,4,1795532157647,NULL,0);
INSERT INTO `milestone` VALUES (46,'COM_REGISTRADA','COM',7,7,NULL,NULL,2,NULL,4,1795532157669,NULL,0);
INSERT INTO `milestone` VALUES (47,'COM_REGISTRADA','COM',8,8,NULL,NULL,2,NULL,4,1795532157683,NULL,0);
INSERT INTO `milestone` VALUES (48,'COM_REGISTRADA','COM',9,9,NULL,NULL,2,NULL,4,1795532157694,NULL,0);
INSERT INTO `milestone` VALUES (49,'REQ_CREADO','REQ',5,9,5,NULL,2,2,4,1795532157696,NULL,0);
INSERT INTO `milestone` VALUES (50,'COM_REGISTRADA','COM',10,10,NULL,NULL,2,NULL,4,1795532157714,NULL,0);
INSERT INTO `milestone` VALUES (51,'REQ_CREADO','REQ',6,10,6,NULL,2,2,4,1795532157715,NULL,0);
INSERT INTO `milestone` VALUES (52,'COM_REGISTRADA','COM',11,11,NULL,NULL,1,NULL,4,1795532157723,NULL,0);
INSERT INTO `milestone` VALUES (53,'SOL_DISTRIBUIDA','SOL',4,11,NULL,NULL,1,NULL,1,1795532157732,NULL,0);
INSERT INTO `milestone` VALUES (54,'REQ_CREADO','REQ',7,11,7,NULL,5,6,1,1795532157740,NULL,0);
INSERT INTO `milestone` VALUES (55,'SOL_ASUMIDA','SOL',4,11,7,NULL,5,6,1,1795532157740,NULL,0);
INSERT INTO `milestone` VALUES (56,'FLUJO_SOLICITADO','FLUJO',2,11,7,NULL,5,NULL,2,1795532157747,'TRANSFERENCIA',0);
INSERT INTO `milestone` VALUES (57,'FLUJO_DECIDIDO','FLUJO',2,11,7,NULL,7,NULL,2,1795532157754,'ACEPTADA',0);
INSERT INTO `milestone` VALUES (58,'REQ_CERRADO','REQ',7,11,7,NULL,7,NULL,2,1795532157762,'JEFATURA_PRINCIPAL',0);
INSERT INTO `milestone` VALUES (59,'COM_REGISTRADA','COM',12,12,NULL,NULL,1,NULL,4,1795532157768,NULL,0);
INSERT INTO `milestone` VALUES (60,'SOL_DISTRIBUIDA','SOL',5,12,NULL,NULL,1,NULL,1,1795532157777,NULL,0);
INSERT INTO `milestone` VALUES (61,'REQ_CREADO','REQ',8,12,8,NULL,5,6,1,1795532157792,NULL,0);
INSERT INTO `milestone` VALUES (62,'SOL_ASUMIDA','SOL',5,12,8,NULL,5,6,1,1795532157792,NULL,0);
INSERT INTO `milestone` VALUES (63,'CASO_CREADO','CAS',3,12,8,3,5,6,7,1795532157806,NULL,0);
INSERT INTO `milestone` VALUES (64,'CASO_ASIGNADO','ASIGNACION',4,12,8,3,5,6,7,1795532157807,NULL,0);
INSERT INTO `milestone` VALUES (65,'FLUJO_SOLICITADO','FLUJO',3,12,8,3,5,NULL,2,1795532157816,'DERIVACION',0);
INSERT INTO `milestone` VALUES (66,'CASO_CREADO','CAS',4,12,8,4,7,8,8,1795532157827,NULL,0);
INSERT INTO `milestone` VALUES (67,'CASO_ASIGNADO','ASIGNACION',5,12,8,4,7,8,8,1795532157828,NULL,0);
INSERT INTO `milestone` VALUES (68,'CASO_CERRADO','CAS',3,12,8,3,7,NULL,7,1795532157830,'DERIVACION',0);
INSERT INTO `milestone` VALUES (69,'FLUJO_DECIDIDO','FLUJO',3,12,8,3,7,NULL,2,1795532157834,'ACEPTADA',0);
INSERT INTO `milestone` VALUES (70,'COM_REGISTRADA','COM',13,13,NULL,NULL,1,NULL,4,1795532157842,NULL,0);
INSERT INTO `milestone` VALUES (71,'COM_REGISTRADA','COM',14,14,NULL,NULL,1,NULL,4,1795532157868,NULL,0);
INSERT INTO `milestone` VALUES (72,'COM_REGISTRADA','COM',15,15,NULL,NULL,1,NULL,4,1795532157903,NULL,0);
INSERT INTO `milestone` VALUES (73,'SOL_DISTRIBUIDA','SOL',6,15,NULL,NULL,1,NULL,4,1795532157919,NULL,0);
INSERT INTO `milestone` VALUES (74,'SOL_DESCARTADA','SOL',6,15,NULL,NULL,4,NULL,4,1795532157928,'INVALIDA',0);
INSERT INTO `milestone` VALUES (75,'COM_REGISTRADA','COM',16,16,NULL,NULL,1,NULL,4,1795532157933,NULL,0);
INSERT INTO `milestone` VALUES (76,'SOL_DISTRIBUIDA','SOL',7,16,NULL,NULL,1,NULL,2,1795532157943,NULL,0);
INSERT INTO `milestone` VALUES (77,'COM_REGISTRADA','COM',17,17,NULL,NULL,2,NULL,4,1795532157951,NULL,0);
/*!40000 ALTER TABLE `milestone` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `operation_lock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operation_lock` (
  `id` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `operation_lock` WRITE;
/*!40000 ALTER TABLE `operation_lock` DISABLE KEYS */;
INSERT INTO `operation_lock` VALUES (1);
/*!40000 ALTER TABLE `operation_lock` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `org_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `org_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(48) NOT NULL,
  `name` varchar(160) NOT NULL,
  `unit_code` varchar(24) NOT NULL,
  `area_code` varchar(48) DEFAULT NULL,
  `site_code` varchar(48) NOT NULL,
  `regional` tinyint(1) NOT NULL DEFAULT '0',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `region_code` varchar(4) DEFAULT NULL,
  `state` varchar(12) NOT NULL DEFAULT 'ACTIVA',
  `opens_ms` bigint DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `unit_code` (`unit_code`),
  CONSTRAINT `org_scope_ibfk_1` FOREIGN KEY (`unit_code`) REFERENCES `org_unit` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `org_scope` WRITE;
/*!40000 ALTER TABLE `org_scope` DISABLE KEYS */;
INSERT INTO `org_scope` VALUES (1,'UPR','Direccion UPR','UPR',NULL,'CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (2,'UPD','Direccion UPD','UPD',NULL,'CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (3,'UEP','Direccion UEP','UEP',NULL,'CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (4,'UPR-REQ','Requerimientos / Atencion de Personas','UPR','REQ','CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (5,'UPR-NCE','NNA bajo cuidado del Estado','UPR','NCE','CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (6,'UPR-RJ','Representacion Judicial','UPR','RJ','CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (7,'UPR-PD','Proteccion de Derechos','UPR','PD','CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (8,'UPD-EDU','Educacion','UPD','EDU','CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (9,'UPD-DIF','Difusion','UPD','DIF','CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (10,'UPD-PAR','Participacion','UPD','PAR','CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (11,'UEP-EST','Estudios','UEP','EST','CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (12,'UEP-OBS','Observatorio de Derechos','UEP','OBS','CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (13,'UGI','Gestion Institucional','UGI',NULL,'CENTRAL',0,1,NULL,'ACTIVA',NULL,0);
INSERT INTO `org_scope` VALUES (14,'REG-DEMO','Sede sintetica - no oficial','REG',NULL,'SEDE-DEMO',1,1,'06','ACTIVA',NULL,2);
INSERT INTO `org_scope` VALUES (15,'REG-PROY','Sede proyectada sintetica','REG','','SEDE-PROY',1,0,'05','PROYECTADA',NULL,0);
/*!40000 ALTER TABLE `org_scope` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `org_unit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `org_unit` (
  `code` varchar(24) NOT NULL,
  `name` varchar(160) NOT NULL,
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `org_unit` WRITE;
/*!40000 ALTER TABLE `org_unit` DISABLE KEYS */;
INSERT INTO `org_unit` VALUES ('AUD','Auditoria');
INSERT INTO `org_unit` VALUES ('DAI','Desarrollo y Analisis Institucional');
INSERT INTO `org_unit` VALUES ('DEF','Defensoria');
INSERT INTO `org_unit` VALUES ('GAB','Gabinete');
INSERT INTO `org_unit` VALUES ('REG','Sedes Regionales');
INSERT INTO `org_unit` VALUES ('UEP','Estudios Publicaciones y Estadisticas');
INSERT INTO `org_unit` VALUES ('UGI','Gestion Institucional');
INSERT INTO `org_unit` VALUES ('UPD','Promocion y Difusion de Derechos');
INSERT INTO `org_unit` VALUES ('UPR','Proteccion de Derechos y Representacion Judicial');
/*!40000 ALTER TABLE `org_unit` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `person_reference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `person_reference` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `communication_id` bigint NOT NULL,
  `requirement_id` bigint DEFAULT NULL,
  `source` varchar(16) NOT NULL,
  `person_role` varchar(24) NOT NULL,
  `display_name` varchar(500) DEFAULT NULL,
  `name_key` varchar(500) DEFAULT NULL,
  `run_number` varchar(9) DEFAULT NULL,
  `run_dv` char(1) DEFAULT NULL,
  `document_key` varchar(100) DEFAULT NULL,
  `authenticated` tinyint(1) NOT NULL DEFAULT '0',
  `updated_ms` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `requirement_id` (`requirement_id`),
  KEY `ix_person_reference_run` (`run_number`),
  KEY `ix_person_reference_document` (`document_key`),
  KEY `ix_person_reference_com` (`communication_id`),
  CONSTRAINT `person_reference_ibfk_1` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`),
  CONSTRAINT `person_reference_ibfk_2` FOREIGN KEY (`requirement_id`) REFERENCES `requirement` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `person_reference` WRITE;
/*!40000 ALTER TABLE `person_reference` DISABLE KEYS */;
INSERT INTO `person_reference` VALUES (1,5,4,'FICHA','NNA','Nombre NNA sintetico','nombre nna sintetico',NULL,NULL,NULL,0,1795532157595);
INSERT INTO `person_reference` VALUES (2,6,NULL,'FORMULARIO','PRESENTADOR','María José De los Ángeles Del Río González','maria jose de los angeles del rio gonzalez','12345678','5',NULL,1,1795532157657);
INSERT INTO `person_reference` VALUES (3,6,NULL,'FORMULARIO','AFECTADO_NNA','Tomás','tomas',NULL,NULL,NULL,0,1795532157657);
INSERT INTO `person_reference` VALUES (4,6,NULL,'FORMULARIO','ADULTO_RELACIONADO','Rosa','rosa',NULL,NULL,NULL,0,1795532157657);
INSERT INTO `person_reference` VALUES (5,7,NULL,'FORMULARIO','PRESENTADOR','NOMBRE SINTETICO APELLIDO SINTETICO','nombre sintetico apellido sintetico','99999999','9',NULL,0,1795532157673);
INSERT INTO `person_reference` VALUES (6,8,NULL,'FORMULARIO','PRESENTADOR','NOMBRE SINTETICO APELLIDO SINTETICO','nombre sintetico apellido sintetico','99999999','9',NULL,0,1795532157687);
INSERT INTO `person_reference` VALUES (8,9,5,'FICHA','REQUIRENTE','Persona Sintetica','persona sintetica','11111111','1',NULL,0,1795532157707);
INSERT INTO `person_reference` VALUES (9,9,5,'FICHA','NNA','Nino sintetico','nino sintetico',NULL,NULL,NULL,0,1795532157707);
INSERT INTO `person_reference` VALUES (10,10,6,'FICHA','NNA','Nina sintetica','nina sintetica',NULL,NULL,NULL,0,1795532157717);
/*!40000 ALTER TABLE `person_reference` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `reopening`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reopening` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `requirement_id` bigint NOT NULL,
  `case_id` bigint DEFAULT NULL,
  `requested_by` bigint NOT NULL,
  `requested_ms` bigint NOT NULL,
  `reason` text NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDIENTE',
  `principal_approved_by` bigint DEFAULT NULL,
  `principal_approved_ms` bigint DEFAULT NULL,
  `principal_scope_at_approval` bigint DEFAULT NULL,
  `decided_by` bigint DEFAULT NULL,
  `decided_ms` bigint DEFAULT NULL,
  `decision_reason` text,
  `revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `requirement_id` (`requirement_id`),
  KEY `case_id` (`case_id`),
  KEY `requested_by` (`requested_by`),
  KEY `principal_approved_by` (`principal_approved_by`),
  KEY `decided_by` (`decided_by`),
  CONSTRAINT `reopening_ibfk_1` FOREIGN KEY (`requirement_id`) REFERENCES `requirement` (`id`),
  CONSTRAINT `reopening_ibfk_2` FOREIGN KEY (`case_id`) REFERENCES `case_record` (`id`),
  CONSTRAINT `reopening_ibfk_3` FOREIGN KEY (`requested_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `reopening_ibfk_4` FOREIGN KEY (`principal_approved_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `reopening_ibfk_5` FOREIGN KEY (`decided_by`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `reopening` WRITE;
/*!40000 ALTER TABLE `reopening` DISABLE KEYS */;
INSERT INTO `reopening` VALUES (1,1,NULL,4,1795532157507,'Nuevos antecedentes','ACEPTADA',NULL,NULL,NULL,4,1795532157515,'Procede',1);
INSERT INTO `reopening` VALUES (2,1,1,4,1795532157520,'Reabrir el Caso','ACEPTADA',NULL,NULL,NULL,4,1795532157528,'Procede',1);
/*!40000 ALTER TABLE `reopening` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `requirement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `requirement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `folio` varchar(48) DEFAULT NULL,
  `communication_id` bigint NOT NULL,
  `solicitation_id` bigint DEFAULT NULL,
  `principal_scope` bigint NOT NULL,
  `attention_user` bigint DEFAULT NULL,
  `attention_scope` bigint NOT NULL,
  `complexity` varchar(12) DEFAULT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'ABIERTO',
  `created_ms` bigint NOT NULL,
  `created_by` bigint NOT NULL,
  `closed_ms` bigint DEFAULT NULL,
  `closed_by` bigint DEFAULT NULL,
  `close_reason` text,
  `revision` bigint NOT NULL DEFAULT '0',
  `matter` varchar(8) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `communication_id` (`communication_id`),
  UNIQUE KEY `folio` (`folio`),
  UNIQUE KEY `solicitation_id` (`solicitation_id`),
  KEY `principal_scope` (`principal_scope`),
  KEY `attention_scope` (`attention_scope`),
  KEY `attention_user` (`attention_user`),
  KEY `created_by` (`created_by`),
  KEY `closed_by` (`closed_by`),
  KEY `ix_req_queue` (`state`,`principal_scope`,`id`),
  CONSTRAINT `requirement_ibfk_1` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`),
  CONSTRAINT `requirement_ibfk_2` FOREIGN KEY (`solicitation_id`) REFERENCES `solicitation` (`id`),
  CONSTRAINT `requirement_ibfk_3` FOREIGN KEY (`principal_scope`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `requirement_ibfk_4` FOREIGN KEY (`attention_scope`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `requirement_ibfk_5` FOREIGN KEY (`attention_user`) REFERENCES `staff` (`id`),
  CONSTRAINT `requirement_ibfk_6` FOREIGN KEY (`created_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `requirement_ibfk_7` FOREIGN KEY (`closed_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `requirement_chk_1` CHECK (((`complexity` is null) or (`complexity` in (_utf8mb4'BAJA',_utf8mb4'MEDIANA',_utf8mb4'ALTA'))))
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `requirement` WRITE;
/*!40000 ALTER TABLE `requirement` DISABLE KEYS */;
INSERT INTO `requirement` VALUES (1,'REQ-A-000001',1,1,4,3,4,'BAJA','ABIERTO',1795532157328,4,NULL,NULL,NULL,9,NULL);
INSERT INTO `requirement` VALUES (2,'REQ-A-000002',2,2,4,3,4,NULL,'ABIERTO',1795532157545,4,NULL,NULL,NULL,0,NULL);
INSERT INTO `requirement` VALUES (3,'REQ-A-000003',4,NULL,4,2,4,NULL,'CERRADO',1795532157581,2,1795531857577,2,'Orientacion entregada',1,NULL);
INSERT INTO `requirement` VALUES (4,'REQ-A-000004',5,NULL,4,3,4,NULL,'CERRADO',1795532157593,2,1795532157625,3,'Orientacion concluida',3,NULL);
INSERT INTO `requirement` VALUES (5,'REQ-A-000005',9,NULL,4,2,4,NULL,'ABIERTO',1795532157696,2,NULL,NULL,NULL,1,NULL);
INSERT INTO `requirement` VALUES (6,'REQ-A-000006',10,NULL,4,2,4,NULL,'ABIERTO',1795532157715,2,NULL,NULL,NULL,0,NULL);
INSERT INTO `requirement` VALUES (7,'REQ-A-000007',11,4,2,6,7,NULL,'CERRADO',1795532157740,5,1795532157762,7,'Cierre sintetico tras transferencia',4,NULL);
INSERT INTO `requirement` VALUES (8,'REQ-A-000008',12,5,1,6,7,'MEDIANA','ABIERTO',1795532157792,5,NULL,NULL,NULL,6,NULL);
/*!40000 ALTER TABLE `requirement` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `role_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_code` varchar(32) NOT NULL,
  `scope_id` bigint DEFAULT NULL,
  `valid_from` bigint NOT NULL,
  `valid_until` bigint DEFAULT NULL,
  `revoked_at` bigint DEFAULT NULL,
  `granted_by` bigint DEFAULT NULL,
  `basis` varchar(1000) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `scope_id` (`scope_id`),
  KEY `granted_by` (`granted_by`),
  KEY `ix_grant_user` (`user_id`,`revoked_at`,`valid_until`),
  CONSTRAINT `role_assignment_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `role_assignment_ibfk_2` FOREIGN KEY (`scope_id`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `role_assignment_ibfk_3` FOREIGN KEY (`granted_by`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `role_assignment` WRITE;
/*!40000 ALTER TABLE `role_assignment` DISABLE KEYS */;
INSERT INTO `role_assignment` VALUES (1,1,'DISTRIBUIDOR',NULL,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (2,1,'ADMINISTRATIVO',4,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (3,2,'ADMINISTRATIVO',4,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (4,3,'PROFESIONAL',4,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (5,4,'JEFATURA',4,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (6,4,'PROFESIONAL',4,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (7,5,'DIRECCION',1,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (8,5,'DIRECCION',5,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (9,5,'DIRECCION',6,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (10,5,'DIRECCION',7,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (11,5,'LECTURA',4,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (12,6,'PROFESIONAL',7,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (13,7,'DIRECCION',2,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (14,7,'DIRECCION',8,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (15,7,'DIRECCION',9,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (16,7,'DIRECCION',10,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (17,8,'PROFESIONAL',8,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (18,9,'PROFESIONAL',9,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (19,10,'DIRECCION',3,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (20,10,'DIRECCION',11,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (21,10,'DIRECCION',12,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (22,11,'PROFESIONAL',11,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (23,12,'DIRECCION',13,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (24,13,'PROFESIONAL',13,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (25,14,'DIRECCION',14,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (26,14,'PROFESIONAL',14,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (27,15,'PROFESIONAL',14,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (28,16,'TI',NULL,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (29,17,'AUDITORIA',4,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (30,18,'LECTURA',4,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
/*!40000 ALTER TABLE `role_assignment` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `solicitation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `solicitation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `communication_id` bigint NOT NULL,
  `destination_scope` bigint NOT NULL,
  `state` varchar(24) NOT NULL DEFAULT 'PENDIENTE',
  `classification` varchar(2000) NOT NULL,
  `created_by` bigint NOT NULL,
  `created_ms` bigint NOT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `requested_area_scope` bigint DEFAULT NULL,
  `suggested_matter` varchar(8) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `communication_id` (`communication_id`),
  KEY `destination_scope` (`destination_scope`),
  KEY `created_by` (`created_by`),
  KEY `fk_sol_requested_area` (`requested_area_scope`),
  KEY `ix_sol_queue` (`state`,`destination_scope`,`id`),
  CONSTRAINT `fk_sol_requested_area` FOREIGN KEY (`requested_area_scope`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `solicitation_ibfk_1` FOREIGN KEY (`communication_id`) REFERENCES `communication` (`id`),
  CONSTRAINT `solicitation_ibfk_2` FOREIGN KEY (`destination_scope`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `solicitation_ibfk_3` FOREIGN KEY (`created_by`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `solicitation` WRITE;
/*!40000 ALTER TABLE `solicitation` DISABLE KEYS */;
INSERT INTO `solicitation` VALUES (1,1,4,'ASUMIDA','Redistribucion',1,1795532157286,4,4,NULL);
INSERT INTO `solicitation` VALUES (2,2,4,'ASUMIDA','Ingreso propio',4,1795532157545,0,NULL,NULL);
INSERT INTO `solicitation` VALUES (3,3,4,'DESCARTADA','Clasificacion sintetica',1,1795532157565,1,4,NULL);
INSERT INTO `solicitation` VALUES (4,11,1,'ASUMIDA','Competencia UPR',1,1795532157731,1,NULL,NULL);
INSERT INTO `solicitation` VALUES (5,12,1,'ASUMIDA','Competencia UPR',1,1795532157777,1,NULL,NULL);
INSERT INTO `solicitation` VALUES (6,15,4,'DESCARTADA','Clasificacion sintetica',1,1795532157919,1,4,NULL);
INSERT INTO `solicitation` VALUES (7,16,2,'PENDIENTE','Competencia UPD',1,1795532157943,0,NULL,NULL);
/*!40000 ALTER TABLE `solicitation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `staff`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `staff` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `name` varchar(160) NOT NULL,
  `email` varchar(200) DEFAULT NULL,
  `password_hash` varchar(100) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `must_change` tinyint(1) NOT NULL DEFAULT '1',
  `security_version` bigint NOT NULL DEFAULT '0',
  `home_scope` bigint NOT NULL,
  `technical` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `home_scope` (`home_scope`),
  CONSTRAINT `staff_ibfk_1` FOREIGN KEY (`home_scope`) REFERENCES `org_scope` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `staff` WRITE;
/*!40000 ALTER TABLE `staff` DISABLE KEYS */;
INSERT INTO `staff` VALUES (1,'distribucion','Distribuidor sintetico',NULL,'volcado-sintetico-sin-acceso',1,0,0,4,0);
INSERT INTO `staff` VALUES (2,'administrativo','Primera atencion sintetica',NULL,'volcado-sintetico-sin-acceso',1,0,0,4,0);
INSERT INTO `staff` VALUES (3,'profesional','Profesional Requerimientos sintetico',NULL,'volcado-sintetico-sin-acceso',1,0,0,4,0);
INSERT INTO `staff` VALUES (4,'coordinacion.requerimientos','Coordinadora de Requerimientos sintetica',NULL,'volcado-sintetico-sin-acceso',1,0,0,4,0);
INSERT INTO `staff` VALUES (5,'direccion.upr','Direccion UPR sintetica',NULL,'volcado-sintetico-sin-acceso',1,0,0,1,0);
INSERT INTO `staff` VALUES (6,'profesional.proteccion','Profesional Proteccion sintetico',NULL,'volcado-sintetico-sin-acceso',1,0,0,7,0);
INSERT INTO `staff` VALUES (7,'direccion.upd','Direccion UPD sintetica',NULL,'volcado-sintetico-sin-acceso',1,0,0,2,0);
INSERT INTO `staff` VALUES (8,'profesional.upd','Profesional Educacion UPD sintetico',NULL,'volcado-sintetico-sin-acceso',1,0,0,8,0);
INSERT INTO `staff` VALUES (9,'profesional.difusion','Profesional Difusion UPD sintetico',NULL,'volcado-sintetico-sin-acceso',1,0,0,9,0);
INSERT INTO `staff` VALUES (10,'direccion.uep','Direccion UEP sintetica',NULL,'volcado-sintetico-sin-acceso',1,0,0,3,0);
INSERT INTO `staff` VALUES (11,'profesional.uep','Profesional Estudios sintetico',NULL,'volcado-sintetico-sin-acceso',1,0,0,11,0);
INSERT INTO `staff` VALUES (12,'direccion.ugi','Direccion UGI sintetica',NULL,'volcado-sintetico-sin-acceso',1,0,0,13,0);
INSERT INTO `staff` VALUES (13,'profesional.ugi','Profesional UGI sintetico',NULL,'volcado-sintetico-sin-acceso',1,0,0,13,0);
INSERT INTO `staff` VALUES (14,'coordinacion.regional','Coordinacion regional sintetica',NULL,'volcado-sintetico-sin-acceso',1,0,0,14,0);
INSERT INTO `staff` VALUES (15,'profesional.regional','Profesional regional sintetico',NULL,'volcado-sintetico-sin-acceso',1,0,0,14,0);
INSERT INTO `staff` VALUES (16,'ti','Administracion tecnica sintetica',NULL,'volcado-sintetico-sin-acceso',1,0,0,13,0);
INSERT INTO `staff` VALUES (17,'auditoria','Auditoria de prueba',NULL,'volcado-sintetico-sin-acceso',1,0,0,4,0);
INSERT INTO `staff` VALUES (18,'gabinete','Consulta autorizada de prueba',NULL,'volcado-sintetico-sin-acceso',1,0,0,4,0);
/*!40000 ALTER TABLE `staff` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `substitution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `substitution` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `designation_id` bigint NOT NULL,
  `activated_by` bigint NOT NULL,
  `from_ms` bigint NOT NULL,
  `until_ms` bigint NOT NULL,
  `reason` varchar(2000) NOT NULL,
  `ended_ms` bigint DEFAULT NULL,
  `ended_by` bigint DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `designation_id` (`designation_id`),
  KEY `activated_by` (`activated_by`),
  KEY `ended_by` (`ended_by`),
  CONSTRAINT `substitution_ibfk_1` FOREIGN KEY (`designation_id`) REFERENCES `substitution_designation` (`id`),
  CONSTRAINT `substitution_ibfk_2` FOREIGN KEY (`activated_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `substitution_ibfk_3` FOREIGN KEY (`ended_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `substitution_chk_1` CHECK ((`until_ms` > `from_ms`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `substitution` WRITE;
/*!40000 ALTER TABLE `substitution` DISABLE KEYS */;
/*!40000 ALTER TABLE `substitution` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `substitution_designation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `substitution_designation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scope_id` bigint NOT NULL,
  `titular_id` bigint NOT NULL,
  `substitute_id` bigint NOT NULL,
  `basis` varchar(1000) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `scope_id` (`scope_id`,`titular_id`,`substitute_id`),
  KEY `titular_id` (`titular_id`),
  KEY `substitute_id` (`substitute_id`),
  CONSTRAINT `substitution_designation_ibfk_1` FOREIGN KEY (`scope_id`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `substitution_designation_ibfk_2` FOREIGN KEY (`titular_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `substitution_designation_ibfk_3` FOREIGN KEY (`substitute_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `substitution_designation_chk_1` CHECK ((`titular_id` <> `substitute_id`))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `substitution_designation` WRITE;
/*!40000 ALTER TABLE `substitution_designation` DISABLE KEYS */;
INSERT INTO `substitution_designation` VALUES (1,14,14,15,'Designacion exclusivamente sintetica para probar subrogancia.',1);
/*!40000 ALTER TABLE `substitution_designation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `substitution_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `substitution_scope` (
  `substitution_id` bigint NOT NULL,
  `grant_id` bigint NOT NULL,
  `scope_id` bigint NOT NULL,
  `role_code` varchar(32) NOT NULL,
  PRIMARY KEY (`substitution_id`,`grant_id`),
  KEY `grant_id` (`grant_id`),
  KEY `scope_id` (`scope_id`),
  CONSTRAINT `substitution_scope_ibfk_1` FOREIGN KEY (`substitution_id`) REFERENCES `substitution` (`id`),
  CONSTRAINT `substitution_scope_ibfk_2` FOREIGN KEY (`grant_id`) REFERENCES `role_assignment` (`id`),
  CONSTRAINT `substitution_scope_ibfk_3` FOREIGN KEY (`scope_id`) REFERENCES `org_scope` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `substitution_scope` WRITE;
/*!40000 ALTER TABLE `substitution_scope` DISABLE KEYS */;
/*!40000 ALTER TABLE `substitution_scope` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `task_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `case_id` bigint NOT NULL,
  `title` varchar(300) NOT NULL,
  `executor_id` bigint NOT NULL,
  `created_by` bigint NOT NULL,
  `created_ms` bigint NOT NULL,
  `due_ms` bigint DEFAULT NULL,
  `mandatory` tinyint(1) NOT NULL DEFAULT '0',
  `state` varchar(20) NOT NULL DEFAULT 'PENDIENTE',
  `revision` bigint NOT NULL DEFAULT '0',
  `reminder_interval_hours` int NOT NULL DEFAULT '24',
  `next_reminder_ms` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `case_id` (`case_id`),
  KEY `created_by` (`created_by`),
  KEY `ix_task_due_reminder` (`state`,`next_reminder_ms`),
  KEY `ix_task_owner_due` (`executor_id`,`state`,`due_ms`,`id`),
  CONSTRAINT `task_item_ibfk_1` FOREIGN KEY (`case_id`) REFERENCES `case_record` (`id`),
  CONSTRAINT `task_item_ibfk_2` FOREIGN KEY (`executor_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `task_item_ibfk_3` FOREIGN KEY (`created_by`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `task_item` WRITE;
/*!40000 ALTER TABLE `task_item` DISABLE KEYS */;
INSERT INTO `task_item` VALUES (1,1,'Llamada sintetica',3,3,1795532157375,NULL,0,'COMPLETADA',1,24,NULL);
INSERT INTO `task_item` VALUES (2,1,'Oficio sintetico',3,3,1795532157384,NULL,0,'CANCELADA',1,24,NULL);
/*!40000 ALTER TABLE `task_item` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `territorial_coverage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `territorial_coverage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scope_id` bigint DEFAULT NULL,
  `region_code` varchar(4) NOT NULL,
  `from_ms` bigint NOT NULL,
  `from_by` bigint NOT NULL,
  `from_basis` text NOT NULL,
  `until_ms` bigint DEFAULT NULL,
  `until_by` bigint DEFAULT NULL,
  `until_basis` text,
  PRIMARY KEY (`id`),
  KEY `from_by` (`from_by`),
  KEY `until_by` (`until_by`),
  KEY `ix_territorial_coverage_scope` (`scope_id`,`until_ms`),
  CONSTRAINT `territorial_coverage_ibfk_1` FOREIGN KEY (`scope_id`) REFERENCES `org_scope` (`id`),
  CONSTRAINT `territorial_coverage_ibfk_2` FOREIGN KEY (`from_by`) REFERENCES `staff` (`id`),
  CONSTRAINT `territorial_coverage_ibfk_3` FOREIGN KEY (`until_by`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `territorial_coverage` WRITE;
/*!40000 ALTER TABLE `territorial_coverage` DISABLE KEYS */;
INSERT INTO `territorial_coverage` VALUES (1,14,'07',1795532157968,16,'Acto sintetico',NULL,NULL,NULL);
INSERT INTO `territorial_coverage` VALUES (2,NULL,'13',1795532157976,16,'Acto sintetico',NULL,NULL,NULL);
/*!40000 ALTER TABLE `territorial_coverage` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

