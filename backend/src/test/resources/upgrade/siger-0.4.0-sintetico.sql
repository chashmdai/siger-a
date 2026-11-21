-- SIGER 0.4.0 (commit cd21f6e, esquema V10): volcado SINTETICO para MySql84UpgradeTest (G13).
-- Solo semillas demo de 0.4.0 y registros sinteticos creados con sus comandos; sin datos de personas reales.
-- Las claves de las cuentas demo se reemplazaron por un valor que no permite ingresar.
-- Procedimiento de generacion: docs/BITACORA_0_5_0.md, registro de G13. No editar a mano.

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
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `application_marker` WRITE;
/*!40000 ALTER TABLE `application_marker` DISABLE KEYS */;
INSERT INTO `application_marker` VALUES ('DEMO_ORGANIZATION_030',1795281577812);
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
INSERT INTO `attention_handoff` VALUES (1,4,2,4,2,1795281695286,4,NULL,'JEFATURA','RECIBIDA','Requiere Caso',4,1795281695298,'Recepcion de la ficha');
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
INSERT INTO `attention_record` VALUES (4,'GENERAL','{\"presenter_first_name\":\"Maria\",\"presenter_middle_name\":\"\",\"presenter_last_name\":\"Sintetica\",\"presenter_second_last_name\":\"Prueba\",\"presenter_run_number\":\"12345678\",\"presenter_run_dv\":\"5\",\"presenter_birth_date\":\"\",\"presenter_document_kind\":\"\",\"presenter_document_number\":\"\",\"presenter_name\":\"Maria Sintetica Prueba\",\"identity_document\":\"12.345.678-5\",\"identity_type\":\"RUN\",\"relationship\":\"\",\"contact_email\":\"\",\"contact_phone\":\"+56912345678\",\"presenter_domicile\":\"\",\"region\":\"13\",\"commune\":\"Ñuñoa\",\"preferred_name\":\"Mari\",\"pronouns\":\"\",\"supports\":\"\",\"court\":\"\",\"rit\":\"\",\"ruc\":\"\",\"guardian_name\":\"\",\"nationality\":\"Chile\",\"group_description\":\"\",\"approximate_count\":\"\",\"motive\":\"Consulta sintetica de persona adulta\",\"story\":\"\",\"request_text\":\"\",\"observations\":\"\",\"orientation\":\"\",\"actions\":\"\",\"commitments\":\"\",\"institutions\":\"\",\"safety_note\":\"\",\"participation_note\":\"\",\"child_message\":\"\",\"identity_note\":\"\",\"priority_note\":\"\",\"safe_contact\":\"\",\"judicial_status\":\"\",\"guardian_status\":\"\",\"participation\":\"\",\"participation_mode\":\"\",\"role_explained\":\"\",\"priority\":\"\",\"children_scope\":\"\",\"modality\":\"GENERAL\",\"schema_version\":\"1.1\",\"course\":\"SEGUIMIENTO\",\"people\":[{\"role\":\"NNA\",\"location\":\"\",\"identity_document\":\"\",\"age\":\"\",\"preferred_name\":\"\",\"nationality\":\"\",\"relationship\":\"\",\"name\":\"Felipe Sintetico Rojas\",\"birth_date\":\"\",\"schooling\":\"\"}]}',1,2,1795281695272);
INSERT INTO `attention_record` VALUES (5,'NNA','{\"presenter_first_name\":\"\",\"presenter_middle_name\":\"\",\"presenter_last_name\":\"\",\"presenter_second_last_name\":\"\",\"presenter_run_number\":\"\",\"presenter_run_dv\":\"\",\"presenter_birth_date\":\"\",\"presenter_document_kind\":\"\",\"presenter_document_number\":\"\",\"presenter_name\":\"\",\"identity_document\":\"\",\"identity_type\":\"\",\"relationship\":\"\",\"contact_email\":\"\",\"contact_phone\":\"\",\"presenter_domicile\":\"\",\"region\":\"\",\"commune\":\"\",\"preferred_name\":\"\",\"pronouns\":\"\",\"supports\":\"\",\"court\":\"\",\"rit\":\"\",\"ruc\":\"\",\"guardian_name\":\"\",\"nationality\":\"\",\"group_description\":\"\",\"approximate_count\":\"\",\"motive\":\"Primer contacto sintetico NNA\",\"story\":\"\",\"request_text\":\"\",\"observations\":\"\",\"orientation\":\"\",\"actions\":\"\",\"commitments\":\"\",\"institutions\":\"\",\"safety_note\":\"\",\"participation_note\":\"\",\"child_message\":\"\",\"identity_note\":\"\",\"priority_note\":\"\",\"safe_contact\":\"\",\"judicial_status\":\"\",\"guardian_status\":\"\",\"participation\":\"NO\",\"participation_mode\":\"\",\"role_explained\":\"\",\"priority\":\"\",\"children_scope\":\"\",\"modality\":\"NNA\",\"schema_version\":\"1.1\",\"course\":\"EN_ATENCION\",\"people\":[{\"role\":\"NNA\",\"location\":\"\",\"identity_document\":\"\",\"age\":\"\",\"preferred_name\":\"\",\"nationality\":\"\",\"relationship\":\"\",\"name\":\"Gabriela Sintetica Lagos\",\"birth_date\":\"\",\"schooling\":\"\"}]}',0,2,1795281695319);
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
  PRIMARY KEY (`id`),
  UNIQUE KEY `requirement_id` (`requirement_id`,`requirement_revision`),
  KEY `author_id` (`author_id`),
  KEY `ix_attention_revisions` (`requirement_id`,`id`),
  CONSTRAINT `attention_revision_ibfk_1` FOREIGN KEY (`requirement_id`) REFERENCES `attention_record` (`requirement_id`),
  CONSTRAINT `attention_revision_ibfk_2` FOREIGN KEY (`author_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `attention_revision` WRITE;
/*!40000 ALTER TABLE `attention_revision` DISABLE KEYS */;
INSERT INTO `attention_revision` VALUES (1,4,0,2,1795281695272,'Primer registro con antecedentes disponibles','{\"presenter_first_name\":\"Maria\",\"presenter_middle_name\":\"\",\"presenter_last_name\":\"Sintetica\",\"presenter_second_last_name\":\"Prueba\",\"presenter_run_number\":\"12345678\",\"presenter_run_dv\":\"5\",\"presenter_birth_date\":\"\",\"presenter_document_kind\":\"\",\"presenter_document_number\":\"\",\"presenter_name\":\"Maria Sintetica Prueba\",\"identity_document\":\"12.345.678-5\",\"identity_type\":\"RUN\",\"relationship\":\"\",\"contact_email\":\"\",\"contact_phone\":\"+56912345678\",\"presenter_domicile\":\"\",\"region\":\"13\",\"commune\":\"Ñuñoa\",\"preferred_name\":\"Mari\",\"pronouns\":\"\",\"supports\":\"\",\"court\":\"\",\"rit\":\"\",\"ruc\":\"\",\"guardian_name\":\"\",\"nationality\":\"Chile\",\"group_description\":\"\",\"approximate_count\":\"\",\"motive\":\"Consulta sintetica de persona adulta\",\"story\":\"\",\"request_text\":\"\",\"observations\":\"\",\"orientation\":\"\",\"actions\":\"\",\"commitments\":\"\",\"institutions\":\"\",\"safety_note\":\"\",\"participation_note\":\"\",\"child_message\":\"\",\"identity_note\":\"\",\"priority_note\":\"\",\"safe_contact\":\"\",\"judicial_status\":\"\",\"guardian_status\":\"\",\"participation\":\"\",\"participation_mode\":\"\",\"role_explained\":\"\",\"priority\":\"\",\"children_scope\":\"\",\"modality\":\"GENERAL\",\"schema_version\":\"1.1\",\"course\":\"SEGUIMIENTO\",\"people\":[{\"role\":\"NNA\",\"location\":\"\",\"identity_document\":\"\",\"age\":\"\",\"preferred_name\":\"\",\"nationality\":\"\",\"relationship\":\"\",\"name\":\"Felipe Sintetico Rojas\",\"birth_date\":\"\",\"schooling\":\"\"}]}');
INSERT INTO `attention_revision` VALUES (2,5,0,2,1795281695320,'Primer registro con antecedentes disponibles','{\"presenter_first_name\":\"\",\"presenter_middle_name\":\"\",\"presenter_last_name\":\"\",\"presenter_second_last_name\":\"\",\"presenter_run_number\":\"\",\"presenter_run_dv\":\"\",\"presenter_birth_date\":\"\",\"presenter_document_kind\":\"\",\"presenter_document_number\":\"\",\"presenter_name\":\"\",\"identity_document\":\"\",\"identity_type\":\"\",\"relationship\":\"\",\"contact_email\":\"\",\"contact_phone\":\"\",\"presenter_domicile\":\"\",\"region\":\"\",\"commune\":\"\",\"preferred_name\":\"\",\"pronouns\":\"\",\"supports\":\"\",\"court\":\"\",\"rit\":\"\",\"ruc\":\"\",\"guardian_name\":\"\",\"nationality\":\"\",\"group_description\":\"\",\"approximate_count\":\"\",\"motive\":\"Primer contacto sintetico NNA\",\"story\":\"\",\"request_text\":\"\",\"observations\":\"\",\"orientation\":\"\",\"actions\":\"\",\"commitments\":\"\",\"institutions\":\"\",\"safety_note\":\"\",\"participation_note\":\"\",\"child_message\":\"\",\"identity_note\":\"\",\"priority_note\":\"\",\"safe_contact\":\"\",\"judicial_status\":\"\",\"guardian_status\":\"\",\"participation\":\"NO\",\"participation_mode\":\"\",\"role_explained\":\"\",\"priority\":\"\",\"children_scope\":\"\",\"modality\":\"NNA\",\"schema_version\":\"1.1\",\"course\":\"EN_ATENCION\",\"people\":[{\"role\":\"NNA\",\"location\":\"\",\"identity_document\":\"\",\"age\":\"\",\"preferred_name\":\"\",\"nationality\":\"\",\"relationship\":\"\",\"name\":\"Gabriela Sintetica Lagos\",\"birth_date\":\"\",\"schooling\":\"\"}]}');
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
) ENGINE=InnoDB AUTO_INCREMENT=62 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `audit_event` WRITE;
/*!40000 ALTER TABLE `audit_event` DISABLE KEYS */;
INSERT INTO `audit_event` VALUES (1,17,1795281577814,'CONFIGURACION_SINTETICA','SISTEMA',0,NULL,'Cuentas y sede ficticias. No son designaciones institucionales.');
INSERT INTO `audit_event` VALUES (2,1,1795281694695,'COM_REGISTRADA','COM',1,4,'Canal TELEFONO');
INSERT INTO `audit_event` VALUES (3,1,1795281694747,'SOL_DISTRIBUIDA','SOL',1,4,'Competencia sintetica');
INSERT INTO `audit_event` VALUES (4,4,1795281694782,'REQ_CREADO','REQ',1,4,'COM 1');
INSERT INTO `audit_event` VALUES (5,4,1795281694784,'SOL_ASUMIDA','SOL',1,4,'Asuncion sintetica');
INSERT INTO `audit_event` VALUES (6,4,1795281694802,'REQ_COMPLEJIDAD','REQ',1,4,' -> BAJA. Clasificacion sintetica');
INSERT INTO `audit_event` VALUES (7,4,1795281694823,'CASO_CREADO','CAS',1,4,'Seguimiento sintetico uno');
INSERT INTO `audit_event` VALUES (8,3,1795281694833,'ASIGNACION_ACUSADA','CAS',1,4,'Acuse manual.');
INSERT INTO `audit_event` VALUES (9,3,1795281694847,'TAREA_CREADA','CAS',1,4,'Agenda independiente de actuaciones.');
INSERT INTO `audit_event` VALUES (10,3,1795281694860,'TASK_COMPLETE','CAS',1,4,'Tarea 1. Llamada realizada');
INSERT INTO `audit_event` VALUES (11,3,1795281694871,'TAREA_CREADA','CAS',1,4,'Agenda independiente de actuaciones.');
INSERT INTO `audit_event` VALUES (12,3,1795281694883,'TASK_CANCEL','CAS',1,4,'Tarea 2. Ya no corresponde');
INSERT INTO `audit_event` VALUES (13,4,1795281694892,'CASE_WAIT','CAS',1,4,'Espera sintetica de antecedentes');
INSERT INTO `audit_event` VALUES (14,4,1795281694902,'CASE_RESUME','CAS',1,4,'Llegaron los antecedentes');
INSERT INTO `audit_event` VALUES (15,3,1795281694917,'CASE_REQUEST_CLOSE','CAS',1,4,'Gestion concluida');
INSERT INTO `audit_event` VALUES (16,4,1795281694926,'CASE_RETURN','CAS',1,4,'Falta registrar la respuesta');
INSERT INTO `audit_event` VALUES (17,3,1795281694937,'CASE_REQUEST_CLOSE','CAS',1,4,'Respuesta registrada');
INSERT INTO `audit_event` VALUES (18,3,1795281694949,'CASE_WITHDRAW_CLOSE','CAS',1,4,'Retiro para complementar');
INSERT INTO `audit_event` VALUES (19,3,1795281694961,'CASO_CERRADO_PROFESIONAL_BAJA','CAS',1,4,'Cierre sintetico de Caso BAJA');
INSERT INTO `audit_event` VALUES (20,4,1795281694977,'REQ_CIERRE_EXPLICITO','REQ',1,4,'Cierre expreso sintetico');
INSERT INTO `audit_event` VALUES (21,4,1795281694987,'REAPERTURA_SOLICITADA','REAPERTURA',1,4,'Nuevo antecedente sintetico');
INSERT INTO `audit_event` VALUES (22,4,1795281694997,'REQ_REACTIVADO','REQ',1,4,'Se reactiva el REQ sintetico');
INSERT INTO `audit_event` VALUES (23,4,1795281694998,'REAPERTURA_ACEPTADA','REAPERTURA',1,4,'Se reactiva el REQ sintetico');
INSERT INTO `audit_event` VALUES (24,4,1795281695008,'COLABORACION_SOLICITADA','FLUJO',1,4,'Apoyo de difusion');
INSERT INTO `audit_event` VALUES (25,7,1795281695030,'CASO_CREADO','CAS',2,9,'Colaboracion sintetica');
INSERT INTO `audit_event` VALUES (26,7,1795281695031,'COLABORACION_ACEPTADA','FLUJO',1,2,'Colaboracion aceptada');
INSERT INTO `audit_event` VALUES (27,1,1795281695044,'COM_REGISTRADA','COM',2,4,'Canal CORREO');
INSERT INTO `audit_event` VALUES (28,1,1795281695061,'SOL_DISTRIBUIDA','SOL',2,4,'Primera clasificacion');
INSERT INTO `audit_event` VALUES (29,4,1795281695073,'SOL_DEVUELTA_DISTRIBUCION','SOL',2,4,'No corresponde a Requerimientos');
INSERT INTO `audit_event` VALUES (30,1,1795281695095,'SOL_REDIRIGIDA','SOL',2,2,'Destino anterior 4. Redistribucion sintetica');
INSERT INTO `audit_event` VALUES (31,7,1795281695115,'REQ_CREADO','REQ',2,2,'COM 2');
INSERT INTO `audit_event` VALUES (32,7,1795281695116,'SOL_ASUMIDA','SOL',2,2,'Asume UPD');
INSERT INTO `audit_event` VALUES (33,7,1795281695130,'CASO_CREADO','CAS',3,8,'Seguimiento sintetico dos');
INSERT INTO `audit_event` VALUES (34,7,1795281695141,'DERIVACION_SOLICITADA','FLUJO',2,8,'Corresponde a Requerimientos');
INSERT INTO `audit_event` VALUES (35,4,1795281695159,'CASO_CREADO','CAS',4,4,'Derivacion sintetica');
INSERT INTO `audit_event` VALUES (36,4,1795281695161,'CASO_CERRADO_DERIVACION','CAS',3,8,'Derivacion aceptada; sucesor 4');
INSERT INTO `audit_event` VALUES (37,4,1795281695164,'DERIVACION_ACEPTADA','FLUJO',2,4,'Derivacion aceptada');
INSERT INTO `audit_event` VALUES (38,7,1795281695176,'TRANSFERENCIA_SOLICITADA','FLUJO',3,2,'Transferencia sintetica rechazada');
INSERT INTO `audit_event` VALUES (39,4,1795281695188,'TRANSFERENCIA_RECHAZADA','FLUJO',3,4,'Se mantiene el principal');
INSERT INTO `audit_event` VALUES (40,1,1795281695198,'COM_REGISTRADA','COM',3,4,'Canal PRESENCIAL');
INSERT INTO `audit_event` VALUES (41,1,1795281695208,'SOL_DISTRIBUIDA','SOL',3,14,'Corresponde a la sede sintetica');
INSERT INTO `audit_event` VALUES (42,14,1795281695218,'REQ_CREADO','REQ',3,14,'COM 3');
INSERT INTO `audit_event` VALUES (43,14,1795281695219,'SOL_ASUMIDA','SOL',3,14,'Asume la sede');
INSERT INTO `audit_event` VALUES (44,14,1795281695229,'CASO_CREADO','CAS',5,14,'Seguimiento sintetico regional');
INSERT INTO `audit_event` VALUES (45,15,1795281695237,'ASIGNACION_ACUSADA','CAS',5,14,'Acuse manual.');
INSERT INTO `audit_event` VALUES (46,16,1795281695251,'SUBROGANCIA_ACTIVADA','SUBROGANCIA',1,14,'Subrogancia sintetica de prueba');
INSERT INTO `audit_event` VALUES (47,2,1795281695274,'COM_REGISTRADA_ATENCION','COM',4,4,'Atencion directa. REQ 4');
INSERT INTO `audit_event` VALUES (48,2,1795281695274,'ATENCION_INICIADA','REQ',4,4,'COM 4. Sin SOL ni Caso automatico.');
INSERT INTO `audit_event` VALUES (49,2,1795281695287,'ATENCION_ENTREGADA','REQ',4,4,'Destino 4. Requiere Caso');
INSERT INTO `audit_event` VALUES (50,4,1795281695299,'ATENCION_RECIBIDA','REQ',4,4,'Entrega 1. Recepcion de la ficha');
INSERT INTO `audit_event` VALUES (51,4,1795281695311,'CASO_CREADO','CAS',6,4,'Seguimiento de primera atencion');
INSERT INTO `audit_event` VALUES (52,2,1795281695320,'COM_REGISTRADA_ATENCION','COM',5,4,'Atencion directa. REQ 5');
INSERT INTO `audit_event` VALUES (53,2,1795281695320,'ATENCION_INICIADA','REQ',5,4,'COM 5. Sin SOL ni Caso automatico.');
INSERT INTO `audit_event` VALUES (54,2,1795281695325,'ATENCION_RESUELTA','REQ',5,4,'ORIENTACION. Resultado registrado por quien atendio.');
INSERT INTO `audit_event` VALUES (55,1,1795281695338,'COM_REGISTRADA','COM',6,4,'Canal OFICIO');
INSERT INTO `audit_event` VALUES (56,1,1795281695350,'SOL_DISTRIBUIDA','SOL',4,4,'Clasificacion sintetica');
INSERT INTO `audit_event` VALUES (57,4,1795281695360,'SOL_DUPLICADA','SOL',4,4,'Duplica la consulta uno');
INSERT INTO `audit_event` VALUES (58,4,1795281695367,'COM_REGISTRADA','COM',7,4,'Canal PRESENCIAL');
INSERT INTO `audit_event` VALUES (59,4,1795281695368,'REQ_CREADO','REQ',6,4,'COM 7');
INSERT INTO `audit_event` VALUES (60,4,1795281695370,'PRIMERA_ATENCION_RESUELTA','REQ',6,4,'Respuesta sintetica entregada');
INSERT INTO `audit_event` VALUES (61,2,1795281695378,'COM_REGISTRADA','COM',8,4,'Canal OTRO');
/*!40000 ALTER TABLE `audit_event` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `case_assignment` WRITE;
/*!40000 ALTER TABLE `case_assignment` DISABLE KEYS */;
INSERT INTO `case_assignment` VALUES (1,1,3,4,1795281694822,1795281694832,1);
INSERT INTO `case_assignment` VALUES (2,2,9,7,1795281695029,NULL,1);
INSERT INTO `case_assignment` VALUES (3,3,8,7,1795281695129,NULL,1);
INSERT INTO `case_assignment` VALUES (4,4,3,4,1795281695159,NULL,1);
INSERT INTO `case_assignment` VALUES (5,5,15,14,1795281695229,1795281695236,1);
INSERT INTO `case_assignment` VALUES (6,6,3,4,1795281695311,NULL,1);
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `case_record` WRITE;
/*!40000 ALTER TABLE `case_record` DISABLE KEYS */;
INSERT INTO `case_record` VALUES (1,'CAS-A-000001',1,4,3,'Seguimiento sintetico uno','UPR','CERRADO',1795281694818,4,1795281694959,3,'PROFESIONAL_BAJA','Cierre sintetico de Caso BAJA',NULL,12);
INSERT INTO `case_record` VALUES (2,'CAS-A-000002',1,9,9,'Colaboracion sintetica','UPD','EN_GESTION',1795281695027,7,NULL,NULL,NULL,NULL,NULL,0);
INSERT INTO `case_record` VALUES (3,'CAS-A-000003',2,8,8,'Seguimiento sintetico dos','UPD','CERRADO',1795281695128,7,1795281695160,4,'DERIVACION','Derivacion aceptada; sucesor 4',NULL,1);
INSERT INTO `case_record` VALUES (4,'CAS-A-000004',2,4,3,'Derivacion sintetica','UPD','EN_GESTION',1795281695158,4,NULL,NULL,NULL,NULL,3,0);
INSERT INTO `case_record` VALUES (5,'CAS-A-000005',3,14,15,'Seguimiento sintetico regional','UPD','EN_GESTION',1795281695228,14,NULL,NULL,NULL,NULL,NULL,1);
INSERT INTO `case_record` VALUES (6,'CAS-A-000006',4,4,3,'Seguimiento de primera atencion','UPR','EN_GESTION',1795281695310,4,NULL,NULL,NULL,NULL,NULL,0);
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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `closure_record` WRITE;
/*!40000 ALTER TABLE `closure_record` DISABLE KEYS */;
INSERT INTO `closure_record` VALUES (1,1,1,3,1795281694959,'PROFESIONAL_BAJA','Cierre sintetico de Caso BAJA');
INSERT INTO `closure_record` VALUES (2,1,NULL,4,1795281694976,'JEFATURA_PRINCIPAL','Cierre expreso sintetico');
INSERT INTO `closure_record` VALUES (3,2,3,4,1795281695160,'DERIVACION','Derivacion aceptada; sucesor 4');
INSERT INTO `closure_record` VALUES (4,5,NULL,2,1795281695325,'PRIMERA_ATENCION','Orientacion sintetica concluida');
INSERT INTO `closure_record` VALUES (5,6,NULL,4,1795275095363,'PRIMERA_ATENCION','Respuesta sintetica entregada');
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
INSERT INTO `command_log` VALUES ('00adb553-3148-4739-a296-1a9add534947',3,'301f18c2125b8f777b3ff78eee4a45dc3cb3488afe1cfdaf4126711cc94ca09f','CAS',1,'Operacion registrada.',1795281694950);
INSERT INTO `command_log` VALUES ('0350586d-8455-4257-a309-bdf482d053d8',3,'d3083866c18283600a3ef5c610ff55afda601cc0d2cd30bdf977712dd9ebf21d','CAS',1,'Operacion registrada.',1795281694873);
INSERT INTO `command_log` VALUES ('0d3c7c19-3bd9-4484-9b16-4bca878522e0',4,'82c5494d4c2698af7b2baab3709a70e713871ad86fb9b917f5cf6877b66f4410','CAS',1,'Operacion registrada.',1795281694824);
INSERT INTO `command_log` VALUES ('0fc355c1-24d6-4033-bba4-1da03e555c29',7,'8964dfdf255cf5f4a4b0312911a01085bdcd9b473e5e365f0468470041ec110c','REQ',2,'Operacion registrada.',1795281695178);
INSERT INTO `command_log` VALUES ('19546a45-1f57-4f10-9830-c6beb09bc8b7',4,'79794d535c43a3cd914e18cc29452060174824e6a08f5628385087d04133a724','CAS',1,'Operacion registrada.',1795281694893);
INSERT INTO `command_log` VALUES ('1b1f9a40-3788-4093-bf08-c32b9a7bbed4',3,'cec8b9fe448444bf1006c26082aee414d085f3a1f897a26919181f10d0a0cd31','CAS',1,'Operacion registrada.',1795281694884);
INSERT INTO `command_log` VALUES ('2ef7e4f5-7c64-40f2-b936-0a41ce4d1d82',2,'88eca8bac0514ea100edecfa81c7cd11f7cd517c956a6f78b280256040f6e058','COM',8,'Operacion registrada.',1795281695382);
INSERT INTO `command_log` VALUES ('2f941b35-dce0-445e-8d83-07e39e8da283',7,'06793dbde8ca5eb9d4883a71fd912c31d03b88cf56bcb8ed0c5295671af6b911','REQ',2,'Operacion registrada.',1795281695117);
INSERT INTO `command_log` VALUES ('3712f4af-edfb-41c7-a60f-57c5aac31a2e',4,'32c5f9d7190f732769d0aa46b2e21431b46978a93e2061c00c837a70725ac0d4','REQ',6,'Operacion registrada.',1795281695370);
INSERT INTO `command_log` VALUES ('3eedf97d-6fda-42fb-bd10-4d55087ca382',4,'84edd4fd9c772f76f8973352838979a7b017bee954e9fd02dc60b86a12d86406','FLUJO',3,'Operacion registrada.',1795281695191);
INSERT INTO `command_log` VALUES ('3f650429-0ce0-425d-a52f-d5e0c54836bb',4,'812546e2d1990d80f2b8205c67c3aba5769474f2a0dbb9a5d3106c42f1fd14a9','CAS',1,'Operacion registrada.',1795281694926);
INSERT INTO `command_log` VALUES ('4492fa59-0661-4e11-b35b-dde310e0327b',1,'b600636b570a8c9df07c98372eab0229a845ac659edbb041fc8d8f6ddd82a64b','SOL',1,'Operacion registrada.',1795281694757);
INSERT INTO `command_log` VALUES ('44fb061e-56b6-49f1-ab23-ce60f1916cfe',4,'52dfa4f7a489fc8e66143f41b5298e040a84fc038f5acfd412844174f9f1be51','REQ',1,'Operacion registrada.',1795281695011);
INSERT INTO `command_log` VALUES ('4531e55c-f8db-4a10-a047-22f9fc78c7d5',16,'60d41990f3a46df4ec8462f9bfb38283b4c7f43f4d2aba5d4f200946db54f607','SUBROGANCIA',1,'Operacion registrada.',1795281695251);
INSERT INTO `command_log` VALUES ('4e96c25c-384a-489b-9073-1c83d7c51170',7,'da7ae7274f9fe0f2225555c874c91cd2c979666ba83a5433dcb51f8f870ae9d0','REQ',2,'Operacion registrada.',1795281695145);
INSERT INTO `command_log` VALUES ('52017a50-1f9f-46d7-b618-8c9375103301',4,'0b209804ad5cdd1532aad22f28d3fb9ab2c49bdf47120ca47490d2ddbbcedfc0','CAS',1,'Operacion registrada.',1795281694903);
INSERT INTO `command_log` VALUES ('59c61351-bca5-4218-8620-c22a874b1a47',4,'7a8e4286eb151fa796a13ccff578b298b1ccbfa81545341dad93697049c9f16a','REQ',1,'Operacion registrada.',1795281694787);
INSERT INTO `command_log` VALUES ('6ce3e8dd-9557-4709-9c21-9f7eb94bd957',1,'e69c6b288dba0186885a97c8734399c3cd762b9249963d99b6f4a464853ae94b','COM',3,'Operacion registrada.',1795281695200);
INSERT INTO `command_log` VALUES ('7dfe5a67-2cd8-45ff-ab2e-7f5afc087f36',4,'7b3fcc41111f70618bc87d6d67253709dcc565ea260a4516fb72732d06d70c49','REQ',1,'Operacion registrada.',1795281694991);
INSERT INTO `command_log` VALUES ('7fd95136-8a18-48b8-9222-cadef9fe8b1e',7,'849a8b1ce86b1f9102924abe92d8227ec469ba0ac1e0059d4994693aab83daed','FLUJO',1,'Operacion registrada.',1795281695036);
INSERT INTO `command_log` VALUES ('812ddad8-d2bf-41ca-86ba-54aa8e16b7e0',4,'9337d55e1ea3c476a32359d3e04e7e3cbb18f1c7377743d88290c65276c5a16c','REQ',1,'Operacion registrada.',1795281694802);
INSERT INTO `command_log` VALUES ('85e5c9a8-46d7-42a6-95d2-9bf0f6ebefa4',4,'7bc2066feb6c7f4097ed6c18dab7ba4f2270ddb409970eadb0362a519a47f612','REQ',4,'Entrega registrada. El mismo REQ conserva su folio y antecedentes.',1795281695302);
INSERT INTO `command_log` VALUES ('8af74769-1a8a-426b-ae25-be612f2a7862',4,'58f63f259aae36d544a249118a3d7d6a22ba71fe956e809b3a5e65384c89fa23','SOL',4,'Operacion registrada.',1795281695360);
INSERT INTO `command_log` VALUES ('916d1f4d-5677-4f9e-9522-ee80bf0a276e',1,'6587e031e76e9eb53309a8214d6b58860676f123ade12d190e1e32dc1ecde039','SOL',4,'Operacion registrada.',1795281695354);
INSERT INTO `command_log` VALUES ('9254b6d0-70dc-4a8e-a0d2-c419bdf26e9c',4,'df7317fd1c55a5d1c8e8063955c42df3fb73445b5044a103f942283e1273122d','FLUJO',2,'Operacion registrada.',1795281695167);
INSERT INTO `command_log` VALUES ('94a7f8dd-970f-4271-ae19-b25f1dc83303',7,'7fee62fdba8926cda58e9fcf37ec12bb6def2812b22e65d1ee46508caf97137a','CAS',3,'Operacion registrada.',1795281695131);
INSERT INTO `command_log` VALUES ('9b8c7d54-6dd4-46d9-a57a-5d07e8cbf2e0',1,'50f4db23bb083910d271b8c1edacf762ce50e6977a647c41ba451134d93461f3','SOL',3,'Operacion registrada.',1795281695210);
INSERT INTO `command_log` VALUES ('9f424fd5-1dc6-4a7d-accb-42c88c7ebda9',1,'caaf8af3006997c0dec040b1d74d1bf8a1d397e5ba955ecd2f1b7deabb9f139d','COM',2,'Operacion registrada.',1795281695048);
INSERT INTO `command_log` VALUES ('a36ea6d8-144e-45bb-836d-09e2423f9e88',4,'b7ed88eac1fbc5aa7109f41015a3457b51381c1fc64b7b4d4da5a5d40895edef','REQ',1,'Operacion registrada.',1795281694979);
INSERT INTO `command_log` VALUES ('a5f54f1d-cdc1-4b29-9a83-13fff32db65c',14,'bc309867630da87db20ff8ed9d8d53b092f08023dc3a66146bdb6e69fc343fb8','CAS',5,'Operacion registrada.',1795281695230);
INSERT INTO `command_log` VALUES ('ad8e4353-2afa-478b-9220-0de88f37f177',2,'69f753a25fa619027c65c24984bb5cb6e391eff5e0e27e9d9fb44b6edb32ea3d','REQ',5,'Atencion resuelta y REQ cerrado sin SOL ni Caso. Resultado e historial conservados.',1795281695329);
INSERT INTO `command_log` VALUES ('b081d32a-de4c-4a1f-a3ba-e9eeaaafff49',3,'fe5663d04e71c85cbe1e4837c5c026cfa234f2cd3e8174fc7d2025a12e1969fb','CAS',1,'Operacion registrada.',1795281694918);
INSERT INTO `command_log` VALUES ('b1bfa988-fcb6-40ca-929e-b29ac76499f9',4,'0e3d3e484517f1196bc55647a8c5f8bdd2cd140ad64ca364b66d9385a7657939','CAS',6,'Operacion registrada.',1795281695312);
INSERT INTO `command_log` VALUES ('b7de5e67-f977-4bab-bbed-75b85c9c5d1b',1,'3292796387c3394150cffc8abd0cd3b030f4bd652d7d6d7124c6bd93c4fec73f','SOL',2,'Operacion registrada.',1795281695065);
INSERT INTO `command_log` VALUES ('bd2cf2aa-da67-4f53-84b0-1ab269c40f6c',2,'79aa997a0d361766c28db8cac7e744aa181f1a41d2caab408aec64fb3e550828','REQ',4,'Entrega enviada. Conservas el encargo hasta su recepcion; no se cambia la jefatura principal.',1795281695291);
INSERT INTO `command_log` VALUES ('beea5b85-4d2d-4cde-9b99-104144e4ee33',3,'c70870dfd3f2c07c061f66b7888fc2e30c1cb973765106e70bdc1b2345da5425','CAS',1,'Operacion registrada.',1795281694861);
INSERT INTO `command_log` VALUES ('bf34bf01-3151-481f-9409-b6e992c68638',2,'e44482c16c20b353ab37d1b4436bd9b4069190c0201b73419e0910fde2657c19','REQ',4,'Atencion registrada. COM y REQ creados; puedes completar la ficha o entregarla.',1795281695278);
INSERT INTO `command_log` VALUES ('c49f46b7-8f91-4d19-9dd5-089e70e00568',4,'c64cb29ef2b597231def231db8a153c4b1be194a1c60252a2fdacd88ad548ee4','REQ',1,'Operacion registrada.',1795281694998);
INSERT INTO `command_log` VALUES ('c6a3f5ce-8c7c-430d-bf86-5948c07181e7',3,'22db2a769df2c16de087d5b088f10b539c0dab9419c52019857654deff6c22fb','CAS',1,'Operacion registrada.',1795281694835);
INSERT INTO `command_log` VALUES ('ca97108e-a52e-4c42-b9bf-f2695b6142ea',3,'6cb08a67effe02f8f96cd88fd0500dea62f2f9fcb0709c7d7295171a88dccf83','CAS',1,'Operacion registrada.',1795281694850);
INSERT INTO `command_log` VALUES ('d0f62532-3dbe-47e1-89e8-b978c4d17001',15,'6173b6cde15996347c19644e58026fe51996599a98c234f6f5459dbffa8e7c5a','CAS',5,'Operacion registrada.',1795281695238);
INSERT INTO `command_log` VALUES ('da034701-12be-4643-bd9e-c18a268359b3',3,'81c2c16853304e6c6ed229bd8a7e985a8888c5ab0877ee9d6c3a72c550604f0a','CAS',1,'Operacion registrada.',1795281694966);
INSERT INTO `command_log` VALUES ('e04bc143-58c2-46b8-ba11-bdd0010cacf2',1,'4e7055b3ffdaf7e1b776cf2de04e7c31088dc47bb58fc8b4e8b422aae1b3d930','COM',6,'Operacion registrada.',1795281695342);
INSERT INTO `command_log` VALUES ('e311fac3-63d1-4b4c-9454-4647e8c0c959',4,'8fc292e61c5c781012db44bb334250656b2c78b0c8e36fbf796df20eab0b0f97','SOL',2,'Operacion registrada.',1795281695085);
INSERT INTO `command_log` VALUES ('e4552243-8f34-4638-9084-4b1ef18682e5',1,'dc5a10d0846dcc66bbef406735a656461d91bbf21d46b7249eb15255ca8b9df0','SOL',2,'Operacion registrada.',1795281695100);
INSERT INTO `command_log` VALUES ('ef08b7c7-c430-48e0-8e5b-5eb83edfdab1',1,'8e9c54b68c07783b282cdac6866ff74a89d62a64a211aba95aa3e44fa6e455ca','COM',1,'Operacion registrada.',1795281694710);
INSERT INTO `command_log` VALUES ('fa225292-eb5f-448c-9647-b01f9c1d6a8f',14,'2958f5323e7c1cf6ba3e370123b0fb54a4c0bb8eb9b743fac301712cd2b0b651','REQ',3,'Operacion registrada.',1795281695219);
INSERT INTO `command_log` VALUES ('fb5e4bea-5789-47be-9fec-472f7be8ea85',3,'bbd13b70f457aa9b24e704ab596bf410d6d2135277ff06adac89c8ee96420a74','CAS',1,'Operacion registrada.',1795281694938);
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
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `communication` WRITE;
/*!40000 ALTER TABLE `communication` DISABLE KEYS */;
INSERT INTO `communication` VALUES (1,'COM-A-000001',NULL,NULL,'TELEFONO',1795245694658,1795281694683,1,4,'Ana Sintetica Perez','Contacto sintetico','12.345.678-5','Consulta sintetica uno','Relato sintetico de prueba de actualizacion.','ENVIADA',1,'','','','');
INSERT INTO `communication` VALUES (2,'COM-A-000002',NULL,NULL,'CORREO',1795245695039,1795281695043,1,4,'Daniel Sintetico Mora','','Pasaporte AB123456','Consulta sintetica dos','Relato sintetico de prueba de actualizacion.','ENVIADA',1,'','','','');
INSERT INTO `communication` VALUES (3,'COM-A-000003',NULL,NULL,'PRESENCIAL',1795245695193,1795281695196,1,4,'Elena Sintetica Diaz','','11.111.111-1','Consulta sintetica regional','Relato sintetico de prueba de actualizacion.','ENVIADA',1,'','','','');
INSERT INTO `communication` VALUES (4,'COM-A-000004',NULL,NULL,'PRESENCIAL',1795263695254,1795281695269,2,4,'Maria Sintetica Prueba',NULL,'12.345.678-5','Consulta sintetica de persona adulta','Consulta sintetica de persona adulta','ATENDIDA',0,'','+56912345678','','');
INSERT INTO `communication` VALUES (5,'COM-A-000005',NULL,NULL,'TELEFONO',1795269695314,1795281695317,2,4,'',NULL,'','Primer contacto sintetico NNA','Primer contacto sintetico NNA','ATENDIDA',0,'','','','');
INSERT INTO `communication` VALUES (6,'COM-A-000006',NULL,NULL,'OFICIO',1795245695333,1795281695337,1,4,'Ana Sintetica Perez','','','Consulta sintetica duplicada','Relato sintetico de prueba de actualizacion.','DUPLICADA',2,'','','','');
INSERT INTO `communication` VALUES (7,'COM-A-000007',NULL,NULL,'PRESENCIAL',1795274495363,1795281695366,4,4,'Hugo Sintetico Vera','','','Orientacion sintetica inmediata','Relato sintetico.','ATENDIDA',1,'','','','');
INSERT INTO `communication` VALUES (8,'COM-A-000008',NULL,NULL,'OTRO',1795245695374,1795281695377,2,4,'','','','Consulta sintetica sin encaminar','Relato sintetico de prueba de actualizacion.','NUEVA',0,'','','','');
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `communication_link` WRITE;
/*!40000 ALTER TABLE `communication_link` DISABLE KEYS */;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `communication_original` WRITE;
/*!40000 ALTER TABLE `communication_original` DISABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `communication_person` WRITE;
/*!40000 ALTER TABLE `communication_person` DISABLE KEYS */;
INSERT INTO `communication_person` VALUES (1,1,'NNA','Benjamin Sintetico Rojas','22.222.222-2','','');
INSERT INTO `communication_person` VALUES (2,1,'ACOMPANANTE','Carla Sintetica Soto','','','');
INSERT INTO `communication_person` VALUES (3,1,'OTRA_PERSONA','Persona Sintetica Senalada','','','');
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `external_attribute` WRITE;
/*!40000 ALTER TABLE `external_attribute` DISABLE KEYS */;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `external_field` WRITE;
/*!40000 ALTER TABLE `external_field` DISABLE KEYS */;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `external_person` WRITE;
/*!40000 ALTER TABLE `external_person` DISABLE KEYS */;
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
INSERT INTO `flow_request` VALUES (1,'COLABORACION',1,NULL,4,2,4,1795281695007,'Apoyo de difusion','Colaboracion sintetica','ACEPTADA',7,1795281695030,'Colaboracion aceptada',2,1);
INSERT INTO `flow_request` VALUES (2,'DERIVACION',2,3,8,4,7,1795281695141,'Corresponde a Requerimientos','Derivacion sintetica','ACEPTADA',4,1795281695164,'Derivacion aceptada',4,1);
INSERT INTO `flow_request` VALUES (3,'TRANSFERENCIA',2,NULL,2,4,7,1795281695175,'Transferencia sintetica rechazada','','RECHAZADA',4,1795281695185,'Se mantiene el principal',NULL,1);
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
INSERT INTO `flyway_schema_history` VALUES (1,'1','siger a','SQL','V1__siger_a.sql',242221211,'root','2026-11-21 17:19:30',674,1);
INSERT INTO `flyway_schema_history` VALUES (2,'2','agenda recordatorios','SQL','V2__agenda_recordatorios.sql',47838886,'root','2026-11-21 17:19:30',89,1);
INSERT INTO `flyway_schema_history` VALUES (3,'3','sol area sugerida','SQL','V3__sol_area_sugerida.sql',357863808,'root','2026-11-21 17:19:31',91,1);
INSERT INTO `flyway_schema_history` VALUES (4,'4','bandejas y continuidad','SQL','V4__bandejas_y_continuidad.sql',-940417168,'root','2026-11-21 17:19:32',1103,1);
INSERT INTO `flyway_schema_history` VALUES (5,'5','atencion progresiva','SQL','V5__atencion_progresiva.sql',-1250707106,'root','2026-11-21 17:19:32',117,1);
INSERT INTO `flyway_schema_history` VALUES (6,'6','original comunicacion externa','SQL','V6__original_comunicacion_externa.sql',309025193,'root','2026-11-21 17:19:32',29,1);
INSERT INTO `flyway_schema_history` VALUES (7,'7','normalizacion comunicacion externa','SQL','V7__normalizacion_comunicacion_externa.sql',-179316656,'root','2026-11-21 17:19:32',90,1);
INSERT INTO `flyway_schema_history` VALUES (8,'8','consumidor formulario','SQL','V8__consumidor_formulario.sql',-2079685744,'root','2026-11-21 17:19:32',168,1);
INSERT INTO `flyway_schema_history` VALUES (9,'9','operacion continua','SQL','V9__operacion_continua.sql',1258741787,'root','2026-11-21 17:19:32',116,1);
INSERT INTO `flyway_schema_history` VALUES (10,'10','analisis documental','SQL','V10__analisis_documental.sql',-1341567348,'root','2026-11-21 17:19:32',36,1);
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
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `inbox_notice` WRITE;
/*!40000 ALTER TABLE `inbox_notice` DISABLE KEYS */;
INSERT INTO `inbox_notice` VALUES (1,1,1795281694708,'Hay un ingreso pendiente de clasificacion.','COM',1,NULL);
INSERT INTO `inbox_notice` VALUES (2,4,1795281694756,'Tienes una SOL pendiente de asumir.','SOL',1,NULL);
INSERT INTO `inbox_notice` VALUES (3,3,1795281694785,'Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.','REQ',1,NULL);
INSERT INTO `inbox_notice` VALUES (4,3,1795281694822,'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (5,3,1795281694848,'Tienes una tarea asignada en un Caso.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (6,3,1795281694872,'Tienes una tarea asignada en un Caso.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (7,4,1795281694916,'Un Caso requiere revision de termino.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (8,3,1795281694925,'La jefatura devolvio el termino con observaciones.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (9,4,1795281694936,'Un Caso requiere revision de termino.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (10,4,1795281694948,'El responsable retiro su solicitud de cierre; el Caso vuelve a gestion.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (11,3,1795281694961,'El Caso fue cerrado. El cierre del REQ es una decision separada.','CAS',1,NULL);
INSERT INTO `inbox_notice` VALUES (12,4,1795281694965,'Cambio el estado de un Caso del REQ.','REQ',1,NULL);
INSERT INTO `inbox_notice` VALUES (13,4,1795281694978,'Un REQ fue cerrado expresamente por su jefatura principal.','REQ',1,NULL);
INSERT INTO `inbox_notice` VALUES (14,4,1795281694990,'Hay una solicitud de reapertura.','REQ',1,NULL);
INSERT INTO `inbox_notice` VALUES (15,7,1795281695010,'Tienes una peticion de colaboracion.','FLUJO',1,NULL);
INSERT INTO `inbox_notice` VALUES (16,9,1795281695029,'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.','CAS',2,NULL);
INSERT INTO `inbox_notice` VALUES (17,4,1795281695035,'Se resolvio una peticion de colaboracion.','FLUJO',1,NULL);
INSERT INTO `inbox_notice` VALUES (18,1,1795281695048,'Hay un ingreso pendiente de clasificacion.','COM',2,NULL);
INSERT INTO `inbox_notice` VALUES (19,4,1795281695064,'Tienes una SOL pendiente de asumir.','SOL',2,NULL);
INSERT INTO `inbox_notice` VALUES (20,1,1795281695076,'Una SOL fue devuelta a clasificacion.','COM',2,NULL);
INSERT INTO `inbox_notice` VALUES (21,7,1795281695099,'Una SOL fue remitida a tu ambito.','SOL',2,NULL);
INSERT INTO `inbox_notice` VALUES (22,8,1795281695116,'Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.','REQ',2,NULL);
INSERT INTO `inbox_notice` VALUES (23,8,1795281695130,'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.','CAS',3,NULL);
INSERT INTO `inbox_notice` VALUES (24,4,1795281695144,'Tienes una peticion de derivacion.','FLUJO',2,NULL);
INSERT INTO `inbox_notice` VALUES (25,3,1795281695159,'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.','CAS',4,NULL);
INSERT INTO `inbox_notice` VALUES (26,8,1795281695161,'El Caso fue cerrado. El cierre del REQ es una decision separada.','CAS',3,NULL);
INSERT INTO `inbox_notice` VALUES (27,7,1795281695163,'Cambio el estado de un Caso del REQ.','REQ',2,NULL);
INSERT INTO `inbox_notice` VALUES (28,7,1795281695167,'Se resolvio una peticion de derivacion.','FLUJO',2,NULL);
INSERT INTO `inbox_notice` VALUES (29,4,1795281695177,'Tienes una peticion de transferencia.','FLUJO',3,NULL);
INSERT INTO `inbox_notice` VALUES (30,7,1795281695190,'Se resolvio una peticion de transferencia.','FLUJO',3,NULL);
INSERT INTO `inbox_notice` VALUES (31,1,1795281695200,'Hay un ingreso pendiente de clasificacion.','COM',3,NULL);
INSERT INTO `inbox_notice` VALUES (32,14,1795281695210,'Tienes una SOL pendiente de asumir.','SOL',3,NULL);
INSERT INTO `inbox_notice` VALUES (33,15,1795281695219,'Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.','REQ',3,NULL);
INSERT INTO `inbox_notice` VALUES (34,15,1795281695229,'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.','CAS',5,NULL);
INSERT INTO `inbox_notice` VALUES (35,15,1795281695251,'Una autoridad habilito tu subrogancia durante el periodo registrado.','SUBROGANCIA',1,NULL);
INSERT INTO `inbox_notice` VALUES (36,14,1795281695251,'Se registro una subrogancia de tu funcion; tu cartera profesional no se transfiere.','SUBROGANCIA',1,NULL);
INSERT INTO `inbox_notice` VALUES (37,4,1795281695278,'Una primera atencion requiere un Caso de seguimiento.','REQ',4,NULL);
INSERT INTO `inbox_notice` VALUES (38,4,1795281695290,'Tienes una primera atencion para recibir.','REQ',4,NULL);
INSERT INTO `inbox_notice` VALUES (39,2,1795281695299,'Tu entrega de primera atencion fue recibida.','REQ',4,NULL);
INSERT INTO `inbox_notice` VALUES (40,4,1795281695302,'Se registro continuidad de primera atencion; el principal del REQ no cambia.','REQ',4,NULL);
INSERT INTO `inbox_notice` VALUES (41,3,1795281695311,'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.','CAS',6,NULL);
INSERT INTO `inbox_notice` VALUES (42,4,1795281695329,'Una primera atencion se resolvio sin Caso.','REQ',5,NULL);
INSERT INTO `inbox_notice` VALUES (43,1,1795281695342,'Hay un ingreso pendiente de clasificacion.','COM',6,NULL);
INSERT INTO `inbox_notice` VALUES (44,4,1795281695353,'Tienes una SOL pendiente de asumir.','SOL',4,NULL);
INSERT INTO `inbox_notice` VALUES (45,1,1795281695381,'Hay un ingreso pendiente de clasificacion.','COM',8,NULL);
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
INSERT INTO `management_note` VALUES (1,5,NULL,2,1795270295314,1795281695323,'ORIENTACION: Orientacion sintetica concluida',0,NULL,NULL);
INSERT INTO `management_note` VALUES (2,6,NULL,4,1795275095363,1795281695368,'RESPUESTA_DIRECTA: Respuesta sintetica entregada',0,NULL,NULL);
/*!40000 ALTER TABLE `management_note` ENABLE KEYS */;
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
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `unit_code` (`unit_code`),
  CONSTRAINT `org_scope_ibfk_1` FOREIGN KEY (`unit_code`) REFERENCES `org_unit` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `org_scope` WRITE;
/*!40000 ALTER TABLE `org_scope` DISABLE KEYS */;
INSERT INTO `org_scope` VALUES (1,'UPR','Direccion UPR','UPR',NULL,'CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (2,'UPD','Direccion UPD','UPD',NULL,'CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (3,'UEP','Direccion UEP','UEP',NULL,'CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (4,'UPR-REQ','Requerimientos / Atencion de Personas','UPR','REQ','CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (5,'UPR-NCE','NNA bajo cuidado del Estado','UPR','NCE','CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (6,'UPR-RJ','Representacion Judicial','UPR','RJ','CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (7,'UPR-PD','Proteccion de Derechos','UPR','PD','CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (8,'UPD-EDU','Educacion','UPD','EDU','CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (9,'UPD-DIF','Difusion','UPD','DIF','CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (10,'UPD-PAR','Participacion','UPD','PAR','CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (11,'UEP-EST','Estudios','UEP','EST','CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (12,'UEP-OBS','Observatorio de Derechos','UEP','OBS','CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (13,'UGI','Gestion Institucional','UGI',NULL,'CENTRAL',0,1);
INSERT INTO `org_scope` VALUES (14,'REG-DEMO','Sede sintetica - no oficial','REG',NULL,'SEDE-DEMO',1,1);
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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `reopening` WRITE;
/*!40000 ALTER TABLE `reopening` DISABLE KEYS */;
INSERT INTO `reopening` VALUES (1,1,NULL,4,1795281694987,'Nuevo antecedente sintetico','ACEPTADA',NULL,NULL,NULL,4,1795281694997,'Se reactiva el REQ sintetico',1);
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `requirement` WRITE;
/*!40000 ALTER TABLE `requirement` DISABLE KEYS */;
INSERT INTO `requirement` VALUES (1,'REQ-A-000001',1,1,4,3,4,'BAJA','ABIERTO',1795281694778,4,NULL,NULL,NULL,8);
INSERT INTO `requirement` VALUES (2,'REQ-A-000002',2,2,2,8,8,NULL,'ABIERTO',1795281695113,7,NULL,NULL,NULL,7);
INSERT INTO `requirement` VALUES (3,'REQ-A-000003',3,3,14,15,14,NULL,'ABIERTO',1795281695218,14,NULL,NULL,NULL,1);
INSERT INTO `requirement` VALUES (4,'REQ-A-000004',4,NULL,4,4,4,NULL,'ABIERTO',1795281695270,2,NULL,NULL,NULL,3);
INSERT INTO `requirement` VALUES (5,'REQ-A-000005',5,NULL,4,2,4,NULL,'CERRADO',1795281695318,2,1795281695324,2,'Orientacion sintetica concluida',1);
INSERT INTO `requirement` VALUES (6,'REQ-A-000006',7,NULL,4,4,4,NULL,'CERRADO',1795281695367,4,1795275095363,4,'Respuesta sintetica entregada',1);
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
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
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
INSERT INTO `role_assignment` VALUES (25,14,'JEFATURA',14,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (26,14,'PROFESIONAL',14,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (27,15,'PROFESIONAL',14,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (28,16,'AUTORIDAD',1,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (29,16,'AUTORIDAD',2,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (30,16,'AUTORIDAD',3,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (31,16,'AUTORIDAD',4,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (32,16,'AUTORIDAD',5,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (33,16,'AUTORIDAD',6,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (34,16,'AUTORIDAD',7,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (35,16,'AUTORIDAD',8,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (36,16,'AUTORIDAD',9,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (37,16,'AUTORIDAD',10,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (38,16,'AUTORIDAD',11,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (39,16,'AUTORIDAD',12,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (40,16,'AUTORIDAD',13,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (41,16,'AUTORIDAD',14,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (42,17,'TI',NULL,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (43,18,'AUDITORIA',4,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
INSERT INTO `role_assignment` VALUES (44,19,'LECTURA',4,0,NULL,NULL,NULL,'CONFIGURACION SINTETICA DE PRUEBA');
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `solicitation` WRITE;
/*!40000 ALTER TABLE `solicitation` DISABLE KEYS */;
INSERT INTO `solicitation` VALUES (1,1,4,'ASUMIDA','Competencia sintetica',1,1795281694745,1,4);
INSERT INTO `solicitation` VALUES (2,2,2,'ASUMIDA','Redistribucion sintetica',1,1795281695060,3,8);
INSERT INTO `solicitation` VALUES (3,3,14,'ASUMIDA','Corresponde a la sede sintetica',1,1795281695207,1,NULL);
INSERT INTO `solicitation` VALUES (4,6,4,'DESCARTADA','Clasificacion sintetica',1,1795281695350,1,4);
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
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
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
INSERT INTO `staff` VALUES (14,'coordinacion.regional','Coordinacion regional sintetica',NULL,'volcado-sintetico-sin-acceso',1,0,1,14,0);
INSERT INTO `staff` VALUES (15,'profesional.regional','Profesional regional sintetico',NULL,'volcado-sintetico-sin-acceso',1,0,1,14,0);
INSERT INTO `staff` VALUES (16,'autoridad','Autoridad habilitante sintetica',NULL,'volcado-sintetico-sin-acceso',1,0,0,13,0);
INSERT INTO `staff` VALUES (17,'ti','Administracion tecnica sintetica',NULL,'volcado-sintetico-sin-acceso',1,0,0,13,0);
INSERT INTO `staff` VALUES (18,'auditoria','Auditoria de prueba',NULL,'volcado-sintetico-sin-acceso',1,0,0,4,0);
INSERT INTO `staff` VALUES (19,'gabinete','Consulta autorizada de prueba',NULL,'volcado-sintetico-sin-acceso',1,0,0,4,0);
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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `substitution` WRITE;
/*!40000 ALTER TABLE `substitution` DISABLE KEYS */;
INSERT INTO `substitution` VALUES (1,1,16,1795278095241,4102401600000,'Subrogancia sintetica de prueba',NULL,NULL,0);
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
INSERT INTO `substitution_scope` VALUES (1,25,14,'JEFATURA');
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
INSERT INTO `task_item` VALUES (1,1,'Llamada sintetica',3,3,1795281694845,NULL,0,'COMPLETADA',1,24,NULL);
INSERT INTO `task_item` VALUES (2,1,'Tarea sintetica que se cancela',3,3,1795281694871,NULL,0,'CANCELADA',1,24,NULL);
/*!40000 ALTER TABLE `task_item` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

