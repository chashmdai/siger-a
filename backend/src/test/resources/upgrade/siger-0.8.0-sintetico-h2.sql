-- SIGER 0.8.0 (commit 842d012 = v0.8.0, esquema V24): SCRIPT de H2 SINTETICO para H2Upgrade080Test (A00 de 0.9.1).
-- Mismo contenido que siger-0.8.0-sintetico.sql (MySQL), copiado tabla por tabla a una H2 MODE=MySQL con las migraciones
-- de 0.8.0 y escrito con SCRIPT de H2 2.4.240; se retiraron SET DB_CLOSE_DELAY y CREATE USER "SA" (la base de la prueba ya
-- tiene su usuario). Sin datos de personas reales; claves de las cuentas invalidadas.
-- Procedimiento de generacion: docs/BITACORA_0_9_1.md, registro de A00. No editar a mano.
-- H2 2.4.240;
;
CREATE MEMORY TABLE "public"."flyway_schema_history"(
    "installed_rank" INTEGER NOT NULL,
    "version" CHARACTER VARYING(50),
    "description" CHARACTER VARYING(200) NOT NULL,
    "type" CHARACTER VARYING(20) NOT NULL,
    "script" CHARACTER VARYING(1000) NOT NULL,
    "checksum" INTEGER,
    "installed_by" CHARACTER VARYING(100) NOT NULL,
    "installed_on" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "execution_time" INTEGER NOT NULL,
    "success" BOOLEAN NOT NULL
);
ALTER TABLE "public"."flyway_schema_history" ADD CONSTRAINT "public"."flyway_schema_history_pk" PRIMARY KEY("installed_rank");
-- 25 +/- SELECT COUNT(*) FROM public.flyway_schema_history;
INSERT INTO "public"."flyway_schema_history" VALUES
(-1, NULL, '<< Flyway Schema History table created >>', 'TABLE', '', NULL, 'sa', TIMESTAMP '2026-11-27 15:58:44.090948', 0, TRUE),
(1, '1', 'siger a', 'SQL', 'V1__siger_a.sql', 242221211, 'sa', TIMESTAMP '2026-11-27 15:58:44.174946', 73, TRUE),
(2, '2', 'agenda recordatorios', 'SQL', 'V2__agenda_recordatorios.sql', 47838886, 'sa', TIMESTAMP '2026-11-27 15:58:44.190944', 10, TRUE),
(3, '3', 'sol area sugerida', 'SQL', 'V3__sol_area_sugerida.sql', 357863808, 'sa', TIMESTAMP '2026-11-27 15:58:44.196946', 3, TRUE),
(4, '4', 'bandejas y continuidad', 'SQL', 'V4__bandejas_y_continuidad.sql', -940417168, 'sa', TIMESTAMP '2026-11-27 15:58:44.254466', 53, TRUE),
(5, '5', 'atencion progresiva', 'SQL', 'V5__atencion_progresiva.sql', -1250707106, 'sa', TIMESTAMP '2026-11-27 15:58:44.265466', 7, TRUE),
(6, '6', 'original comunicacion externa', 'SQL', 'V6__original_comunicacion_externa.sql', 309025193, 'sa', TIMESTAMP '2026-11-27 15:58:44.269977', 2, TRUE),
(7, '7', 'normalizacion comunicacion externa', 'SQL', 'V7__normalizacion_comunicacion_externa.sql', -179316656, 'sa', TIMESTAMP '2026-11-27 15:58:44.27699', 3, TRUE),
(8, '8', 'consumidor formulario', 'SQL', 'V8__consumidor_formulario.sql', -2079685744, 'sa', TIMESTAMP '2026-11-27 15:58:44.286988', 7, TRUE),
(9, '9', 'operacion continua', 'SQL', 'V9__operacion_continua.sql', 1258741787, 'sa', TIMESTAMP '2026-11-27 15:58:44.29499', 5, TRUE),
(10, '10', 'analisis documental', 'SQL', 'V10__analisis_documental.sql', -1341567348, 'sa', TIMESTAMP '2026-11-27 15:58:44.29899', 1, TRUE),
(11, '11', 'sedes regionales', 'SQL', 'V11__sedes_regionales.sql', 782194518, 'sa', TIMESTAMP '2026-11-27 15:58:44.322512', 20, TRUE),
(12, '12', 'indice personas', 'SQL', 'V12__indice_personas.sql', 1534134575, 'sa', TIMESTAMP '2026-11-27 15:58:44.327513', 2, TRUE),
(13, '13', 'calendario habil', 'SQL', 'V13__calendario_habil.sql', 549796993, 'sa', TIMESTAMP '2026-11-27 15:58:44.330513', 1, TRUE),
(14, '14', 'hitos', 'SQL', 'V14__hitos.sql', 511592049, 'sa', TIMESTAMP '2026-11-27 15:58:44.337512', 4, TRUE),
(15, '15', 'cobertura territorial', 'SQL', 'V15__cobertura_territorial.sql', 1537035602, 'sa', TIMESTAMP '2026-11-27 15:58:44.340512', 1, TRUE),
(16, '16', 'region version ficha', 'SQL', 'V16__region_version_ficha.sql', 144975735, 'sa', TIMESTAMP '2026-11-27 15:58:44.344512', 1, TRUE),
(17, '17', 'catalogos estadisticos', 'SQL', 'V17__catalogos_estadisticos.sql', 1992664123, 'sa', TIMESTAMP '2026-11-27 15:58:44.351511', 4, TRUE),
(18, '18', 'normalizacion captura', 'SQL', 'V18__normalizacion_captura.sql', -321801313, 'sa', TIMESTAMP '2026-11-27 15:58:44.357512', 3, TRUE),
(19, '19', 'clasificacion estadistica', 'SQL', 'V19__clasificacion_estadistica.sql', 783563500, 'sa', TIMESTAMP '2026-11-27 15:58:44.36051', 1, TRUE),
(20, '20', 'derechos y actuaciones', 'SQL', 'V20__derechos_y_actuaciones.sql', -703533758, 'sa', TIMESTAMP '2026-11-27 15:58:44.376028', 12, TRUE),
(21, '21', 'dimensiones declaradas', 'SQL', 'V21__dimensiones_declaradas.sql', -554227708, 'sa', TIMESTAMP '2026-11-27 15:58:44.380028', 1, TRUE),
(22, '22', 'indices motor estadistico', 'SQL', 'V22__indices_motor_estadistico.sql', -1312422834, 'sa', TIMESTAMP '2026-11-27 15:58:44.382028', 0, TRUE),
(23, '23', 'cortes y vistas', 'SQL', 'V23__cortes_y_vistas.sql', 2048106386, 'sa', TIMESTAMP '2026-11-27 15:58:44.388029', 2, TRUE),
(24, '24', 'series y exportacion', 'SQL', 'V24__series_y_exportacion.sql', -1293265283, 'sa', TIMESTAMP '2026-11-27 15:58:44.392029', 2, TRUE);
CREATE INDEX "public"."flyway_schema_history_s_idx" ON "public"."flyway_schema_history"("success" NULLS FIRST);
CREATE MEMORY TABLE "public"."app_meta"(
    "id" INTEGER NOT NULL,
    "namespace" CHARACTER VARYING(40) NOT NULL
);
ALTER TABLE "public"."app_meta" ADD CONSTRAINT "public"."CONSTRAINT_4" PRIMARY KEY("id");
-- 1 +/- SELECT COUNT(*) FROM public.app_meta;
INSERT INTO "public"."app_meta" VALUES
(1, 'SIGER_A_2026');
CREATE MEMORY TABLE "public"."operation_lock"(
    "id" INTEGER NOT NULL
);
ALTER TABLE "public"."operation_lock" ADD CONSTRAINT "public"."CONSTRAINT_A" PRIMARY KEY("id");
-- 1 +/- SELECT COUNT(*) FROM public.operation_lock;
INSERT INTO "public"."operation_lock" VALUES
(1);
CREATE MEMORY TABLE "public"."org_unit"(
    "code" CHARACTER VARYING(24) NOT NULL,
    "name" CHARACTER VARYING(160) NOT NULL
);
ALTER TABLE "public"."org_unit" ADD CONSTRAINT "public"."CONSTRAINT_4E" PRIMARY KEY("code");
-- 9 +/- SELECT COUNT(*) FROM public.org_unit;
INSERT INTO "public"."org_unit" VALUES
('AUD', 'Auditoria'),
('DAI', 'Desarrollo y Analisis Institucional'),
('DEF', 'Defensoria'),
('GAB', 'Gabinete'),
('REG', 'Sedes Regionales'),
('UEP', 'Estudios Publicaciones y Estadisticas'),
('UGI', 'Gestion Institucional'),
('UPD', 'Promocion y Difusion de Derechos'),
('UPR', 'Proteccion de Derechos y Representacion Judicial');
CREATE MEMORY TABLE "public"."org_scope"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 16) DEFAULT ON NULL NOT NULL,
    "code" CHARACTER VARYING(48) NOT NULL,
    "name" CHARACTER VARYING(160) NOT NULL,
    "unit_code" CHARACTER VARYING(24) NOT NULL,
    "area_code" CHARACTER VARYING(48),
    "site_code" CHARACTER VARYING(48) NOT NULL,
    "regional" BOOLEAN DEFAULT FALSE NOT NULL,
    "enabled" BOOLEAN DEFAULT TRUE NOT NULL,
    "region_code" CHARACTER VARYING(4),
    "state" CHARACTER VARYING(12) DEFAULT 'ACTIVA' NOT NULL,
    "opens_ms" BIGINT,
    "revision" BIGINT DEFAULT 0 NOT NULL
);
ALTER TABLE "public"."org_scope" ADD CONSTRAINT "public"."CONSTRAINT_7" PRIMARY KEY("id");
-- 15 +/- SELECT COUNT(*) FROM public.org_scope;
INSERT INTO "public"."org_scope" VALUES
(1, 'UPR', 'Direccion UPR', 'UPR', NULL, 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(2, 'UPD', 'Direccion UPD', 'UPD', NULL, 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(3, 'UEP', 'Direccion UEP', 'UEP', NULL, 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(4, 'UPR-REQ', 'Requerimientos / Atencion de Personas', 'UPR', 'REQ', 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(5, 'UPR-NCE', 'NNA bajo cuidado del Estado', 'UPR', 'NCE', 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(6, 'UPR-RJ', 'Representacion Judicial', 'UPR', 'RJ', 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(7, 'UPR-PD', 'Proteccion de Derechos', 'UPR', 'PD', 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(8, 'UPD-EDU', 'Educacion', 'UPD', 'EDU', 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(9, 'UPD-DIF', 'Difusion', 'UPD', 'DIF', 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(10, 'UPD-PAR', 'Participacion', 'UPD', 'PAR', 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(11, 'UEP-EST', 'Estudios', 'UEP', 'EST', 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(12, 'UEP-OBS', 'Observatorio de Derechos', 'UEP', 'OBS', 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(13, 'UGI', 'Gestion Institucional', 'UGI', NULL, 'CENTRAL', FALSE, TRUE, NULL, 'ACTIVA', NULL, 0),
(14, 'REG-DEMO', 'Sede sintetica - no oficial', 'REG', NULL, 'SEDE-DEMO', TRUE, TRUE, '06', 'ACTIVA', NULL, 2),
(15, 'REG-PROY', 'Sede proyectada sintetica', 'REG', '', 'SEDE-PROY', TRUE, FALSE, '05', 'PROYECTADA', NULL, 0);
CREATE MEMORY TABLE "public"."formulario_reception"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) DEFAULT ON NULL NOT NULL,
    "operation_id" CHARACTER VARYING(36) NOT NULL,
    "state" CHARACTER VARYING(16) NOT NULL,
    "document_version" CHARACTER VARYING(8),
    "channel" CHARACTER VARYING(40),
    "content_hash" CHARACTER(64),
    "provenance_hash" CHARACTER(64),
    "lease_id" CHARACTER VARYING(36),
    "lease_until_ms" BIGINT,
    "attempt" INTEGER DEFAULT 0 NOT NULL,
    "communication_id" BIGINT,
    "institutional_id" CHARACTER VARYING(36),
    "folio_number" BIGINT,
    "folio" CHARACTER VARYING(40),
    "registered_ms" BIGINT,
    "receipt" CHARACTER VARYING,
    "receipt_key_id" CHARACTER VARYING(64),
    "ack_pending" BOOLEAN DEFAULT FALSE NOT NULL,
    "ack_attempts" INTEGER DEFAULT 0 NOT NULL,
    "ack_next_ms" BIGINT DEFAULT 0 NOT NULL,
    "ack_blocked" BOOLEAN DEFAULT FALSE NOT NULL,
    "acked_ms" BIGINT,
    "last_error" CHARACTER VARYING(64),
    "created_ms" BIGINT NOT NULL,
    "updated_ms" BIGINT NOT NULL
);
ALTER TABLE "public"."formulario_reception" ADD CONSTRAINT "public"."CONSTRAINT_AC9" PRIMARY KEY("id");
-- 0 +/- SELECT COUNT(*) FROM public.formulario_reception;
CREATE MEMORY TABLE "public"."role_assignment"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 34) DEFAULT ON NULL NOT NULL,
    "user_id" BIGINT NOT NULL,
    "role_code" CHARACTER VARYING(32) NOT NULL,
    "scope_id" BIGINT,
    "valid_from" BIGINT NOT NULL,
    "valid_until" BIGINT,
    "revoked_at" BIGINT,
    "granted_by" BIGINT,
    "basis" CHARACTER VARYING(1000) NOT NULL
);
ALTER TABLE "public"."role_assignment" ADD CONSTRAINT "public"."CONSTRAINT_1" PRIMARY KEY("id");
-- 33 +/- SELECT COUNT(*) FROM public.role_assignment;
INSERT INTO "public"."role_assignment" VALUES
(1, 1, 'DISTRIBUIDOR', NULL, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(2, 1, 'ADMINISTRATIVO', 4, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(3, 2, 'ADMINISTRATIVO', 4, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(4, 3, 'PROFESIONAL', 4, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(5, 4, 'JEFATURA', 4, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(6, 4, 'PROFESIONAL', 4, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(7, 5, 'DIRECCION', 1, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(8, 5, 'DIRECCION', 5, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(9, 5, 'DIRECCION', 6, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(10, 5, 'DIRECCION', 7, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(11, 5, 'LECTURA', 4, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(12, 6, 'PROFESIONAL', 7, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(13, 7, 'DIRECCION', 2, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(14, 7, 'DIRECCION', 8, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(15, 7, 'DIRECCION', 9, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(16, 7, 'DIRECCION', 10, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(17, 8, 'PROFESIONAL', 8, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(18, 9, 'PROFESIONAL', 9, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(19, 10, 'DIRECCION', 3, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(20, 10, 'DIRECCION', 11, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(21, 10, 'DIRECCION', 12, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(22, 11, 'PROFESIONAL', 11, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(23, 12, 'DIRECCION', 13, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(24, 13, 'PROFESIONAL', 13, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(25, 14, 'DIRECCION', 14, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(26, 14, 'PROFESIONAL', 14, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(27, 15, 'PROFESIONAL', 14, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(28, 16, 'TI', NULL, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(29, 17, 'AUDITORIA', 4, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(30, 18, 'LECTURA', 4, 0, NULL, NULL, NULL, 'CONFIGURACION SINTETICA DE PRUEBA'),
(31, 19, 'REPORTES', NULL, 0, NULL, NULL, NULL, 'Volcado sintetico'),
(32, 19, 'GESTION_ESTADISTICA', NULL, 0, NULL, NULL, NULL, 'Volcado sintetico'),
(33, 20, 'REPORTES', NULL, 0, NULL, NULL, NULL, 'Volcado sintetico');
CREATE INDEX "public"."ix_grant_user" ON "public"."role_assignment"("user_id" NULLS FIRST, "revoked_at" NULLS FIRST, "valid_until" NULLS FIRST);
CREATE MEMORY TABLE "public"."substitution_designation"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 2) DEFAULT ON NULL NOT NULL,
    "scope_id" BIGINT NOT NULL,
    "titular_id" BIGINT NOT NULL,
    "substitute_id" BIGINT NOT NULL,
    "basis" CHARACTER VARYING(1000) NOT NULL,
    "enabled" BOOLEAN DEFAULT TRUE NOT NULL
);
ALTER TABLE "public"."substitution_designation" ADD CONSTRAINT "public"."CONSTRAINT_A7" PRIMARY KEY("id");
-- 1 +/- SELECT COUNT(*) FROM public.substitution_designation;
INSERT INTO "public"."substitution_designation" VALUES
(1, 14, 14, 15, 'Designacion exclusivamente sintetica para probar subrogancia.', TRUE);
CREATE MEMORY TABLE "public"."substitution"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) DEFAULT ON NULL NOT NULL,
    "designation_id" BIGINT NOT NULL,
    "activated_by" BIGINT NOT NULL,
    "from_ms" BIGINT NOT NULL,
    "until_ms" BIGINT NOT NULL,
    "reason" CHARACTER VARYING(2000) NOT NULL,
    "ended_ms" BIGINT,
    "ended_by" BIGINT,
    "revision" BIGINT DEFAULT 0 NOT NULL
);
ALTER TABLE "public"."substitution" ADD CONSTRAINT "public"."CONSTRAINT_3" PRIMARY KEY("id");
-- 0 +/- SELECT COUNT(*) FROM public.substitution;
CREATE MEMORY TABLE "public"."external_document"(
    "document_id" BIGINT NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "operation_id" CHARACTER VARYING(36) NOT NULL,
    "attachment_id" CHARACTER VARYING(36) NOT NULL,
    "media_type" CHARACTER VARYING(255) NOT NULL
);
ALTER TABLE "public"."external_document" ADD CONSTRAINT "public"."CONSTRAINT_9" PRIMARY KEY("document_id");
-- 0 +/- SELECT COUNT(*) FROM public.external_document;
CREATE MEMORY TABLE "public"."communication"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 22) DEFAULT ON NULL NOT NULL,
    "folio" CHARACTER VARYING(48),
    "external_source" CHARACTER VARYING(48),
    "external_id" CHARACTER VARYING(100),
    "channel" CHARACTER VARYING(32) NOT NULL,
    "received_ms" BIGINT NOT NULL,
    "created_ms" BIGINT NOT NULL,
    "created_by" BIGINT NOT NULL,
    "origin_scope" BIGINT NOT NULL,
    "presenter" CHARACTER VARYING(200),
    "contact" CHARACTER VARYING(300),
    "identity_document" CHARACTER VARYING(100),
    "subject" CHARACTER VARYING(240) NOT NULL,
    "body" CHARACTER VARYING NOT NULL,
    "state" CHARACTER VARYING(24) DEFAULT 'NUEVA' NOT NULL,
    "revision" BIGINT DEFAULT 0 NOT NULL,
    "contact_email" CHARACTER VARYING(200),
    "contact_phone" CHARACTER VARYING(80),
    "contact_restrictions" CHARACTER VARYING(1000),
    "presenter_domicile" CHARACTER VARYING(500)
);
ALTER TABLE "public"."communication" ADD CONSTRAINT "public"."CONSTRAINT_C" PRIMARY KEY("id");
-- 21 +/- SELECT COUNT(*) FROM public.communication;
INSERT INTO "public"."communication" VALUES
(1, 'COM-A-000001', NULL, NULL, 'TELEFONO', 1795531857253, 1795532157265, 2, 4, '', '', '', 'COM con todo el recorrido', 'Relato sintetico', 'ENVIADA', 1, '', '', '', ''),
(2, 'COM-A-000002', NULL, NULL, 'TELEFONO', 1795531857531, 1795532157533, 4, 4, '', '', '', 'COM asumida por la jefatura', 'Relato sintetico', 'ENVIADA', 1, '', '', '', ''),
(3, 'COM-A-000003', NULL, NULL, 'TELEFONO', 1795531857553, 1795532157556, 2, 4, '', '', '', 'COM duplicada', 'Relato sintetico', 'DUPLICADA', 2, '', '', '', ''),
(4, 'COM-A-000004', NULL, NULL, 'TELEFONO', 1795531557576, 1795532157579, 2, 4, '', '', '', 'Orientacion', 'Relato sintetico', 'ATENDIDA', 1, '', '', '', ''),
(5, 'COM-A-000005', NULL, NULL, 'PRESENCIAL', 1795531557586, 1795532157591, 2, 4, '', NULL, '', 'Contacto sintetico NNA', 'Contacto sintetico NNA', 'ATENDIDA', 0, '', '', '', ''),
(6, 'COM-A-000006', 'FORMULARIO_PUBLICO_DDN', 'd76662e1-7441-4b6b-a731-e2bfb1d3c0bf', 'FORMULARIO_WEB_PUBLICO', 1793793600000, 1795532157647, 2, 4, U&'Mar\00eda Jos\00e9 De los \00c1ngeles Del R\00edo Gonz\00e1lez', NULL, '12345678-5', U&'Formulario web adulto \00b7 Vulneraci\00f3n de derechos', U&'Relato sint\00e9tico con tildes: ni\00f1o, se\00f1al, acci\00f3n.', 'NUEVA', 0, 'maria.jose@example.cl', '+56912345678', NULL, U&'Avenida Irarr\00e1zaval 1234, depto. 5, \00d1u\00f1oa, Metropolitana de Santiago'),
(7, 'COM-A-000007', 'FORMULARIO_PUBLICO_DDN', '66666666-6666-4666-8666-666666666670', 'FORMULARIO_WEB_NNA', 1794744000000, 1795532157669, 2, 4, 'NOMBRE SINTETICO APELLIDO SINTETICO', 'Contacto principal: No tiene una forma segura de contacto', '99999999-9', 'Formulario web NNA', U&'Relato exclusivamente sint\00e9tico para el contrato NNA SRCeI.', 'NUEVA', 0, NULL, NULL, 'No tiene una forma segura de contacto.', 'Metropolitana de Santiago'),
(8, 'COM-A-000008', 'FORMULARIO_PUBLICO_DDN', '66666666-6666-4666-8666-666666666671', 'FORMULARIO_WEB_NNA', 1794744000000, 1795532157683, 2, 4, 'NOMBRE SINTETICO APELLIDO SINTETICO', 'Contacto principal: No tiene una forma segura de contacto', '99999999-9', 'Formulario web NNA', U&'Relato exclusivamente sint\00e9tico para el contrato NNA SRCeI.', 'NUEVA', 0, NULL, NULL, 'No tiene una forma segura de contacto.', 'Metropolitana de Santiago'),
(9, 'COM-A-000009', NULL, NULL, 'PRESENCIAL', 1795531357691, 1795532157694, 2, 4, 'Persona Sintetica', NULL, '11.111.111-1', 'Consulta sintetica', 'Consulta sintetica', 'ATENDIDA', 0, 'persona@ejemplo.cl', '+56900000000', '', ''),
(10, 'COM-A-000010', NULL, NULL, 'PRESENCIAL', 1795531457711, 1795532157714, 2, 4, '', NULL, '', 'Contacto sintetico NNA 1.1', 'Contacto sintetico NNA 1.1', 'ATENDIDA', 0, '', '', '', ''),
(11, 'COM-A-000011', NULL, NULL, 'TELEFONO', 1795531257721, 1795532157723, 1, 4, '', '', '', 'Ingreso para transferir', 'Relato sintetico', 'ENVIADA', 1, '', '', '', ''),
(12, 'COM-A-000012', NULL, NULL, 'TELEFONO', 1795531257766, 1795532157768, 1, 4, '', '', '', 'Ingreso para derivar', 'Relato sintetico', 'ENVIADA', 1, '', '', '', ''),
(13, 'COM-A-000013', NULL, NULL, 'TELEFONO', 1795531257840, 1795532157842, 1, 4, '', '', '', 'Antecedente adicional', 'Relato sintetico', 'VINCULADA', 1, '', '', '', ''),
(14, 'COM-A-000014', NULL, NULL, 'TELEFONO', 1795531257865, 1795532157868, 1, 4, '', '', '', 'Antecedente devuelto', 'Relato sintetico', 'NUEVA', 2, '', '', '', ''),
(15, 'COM-A-000015', NULL, NULL, 'TELEFONO', 1795531257901, 1795532157903, 1, 4, '', '', '', 'Ingreso invalido', 'Relato sintetico', 'INVALIDA', 2, '', '', '', ''),
(16, 'COM-A-000016', NULL, NULL, 'TELEFONO', 1795531257931, 1795532157933, 1, 4, '', '', '', 'Ingreso con SOL pendiente', 'Relato sintetico', 'ENVIADA', 1, '', '', '', ''),
(17, 'COM-A-000017', NULL, NULL, 'TELEFONO', 1795531257949, 1795532157951, 2, 4, '', '', '', 'Ingreso sin encaminar', 'Relato sintetico', 'NUEVA', 0, '', '', '', ''),
(18, 'COMA-000018', 'FORMULARIO_PUBLICO_DDN', '3433d3bb-3619-486d-9ae2-b952b71ee3c9', 'FORMULARIO_WEB_PUBLICO', 1793793600000, 1795805923580, 2, 4, U&'Mar\00eda Jos\00e9 De los \00c1ngeles Del R\00edo Gonz\00e1lez', NULL, '12345678-5', U&'Formulario web adulto \00b7 Vulneraci\00f3n de derechos', U&'Relato sint\00e9tico con tildes: ni\00f1o, se\00f1al, acci\00f3n.', 'ENVIADA', 1, 'maria.jose@example.cl', '+56912345678', NULL, U&'Avenida Irarr\00e1zaval 1234, depto. 5, \00d1u\00f1oa, Metropolitana de Santiago');
INSERT INTO "public"."communication" VALUES
(19, 'COMA-000019', NULL, NULL, 'CORREO', 1795798723675, 1795805923677, 4, 4, 'Persona Sintetica', '', '11.111.111-1', 'Ingreso con restricciones', 'Relato sintetico', 'ENVIADA', 1, 'persona.sintetica@example.cl', '', 'Contactar solo por correo electronico; no llamar al domicilio.', ''),
(20, 'COMA-000020', NULL, NULL, 'CORREO', 1795798723727, 1795805923730, 4, 4, 'Otra Persona', '', '', 'Ingreso con Caso cerrado', 'Relato sintetico', 'ENVIADA', 1, 'otra.persona@example.cl', '', '', ''),
(21, 'COMA-000021', NULL, NULL, 'CORREO', 1795798723796, 1795805923798, 4, 4, '', '', '', 'Antecedente posterior', 'Relato sintetico', 'VINCULADA', 1, '', '', '', '');
CREATE INDEX "public"."ix_com_queue" ON "public"."communication"("state" NULLS FIRST, "origin_scope" NULLS FIRST, "id" NULLS FIRST);
CREATE INDEX "public"."ix_communication_received" ON "public"."communication"("received_ms" NULLS FIRST);
CREATE INDEX "public"."ix_communication_created" ON "public"."communication"("created_ms" NULLS FIRST);
CREATE MEMORY TABLE "public"."person_reference"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 16) DEFAULT ON NULL NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "requirement_id" BIGINT,
    "source" CHARACTER VARYING(16) NOT NULL,
    "person_role" CHARACTER VARYING(24) NOT NULL,
    "display_name" CHARACTER VARYING(500),
    "name_key" CHARACTER VARYING(500),
    "run_number" CHARACTER VARYING(9),
    "run_dv" CHARACTER(1),
    "document_key" CHARACTER VARYING(100),
    "authenticated" BOOLEAN DEFAULT FALSE NOT NULL,
    "updated_ms" BIGINT NOT NULL
);
ALTER TABLE "public"."person_reference" ADD CONSTRAINT "public"."CONSTRAINT_9D" PRIMARY KEY("id");
-- 14 +/- SELECT COUNT(*) FROM public.person_reference;
INSERT INTO "public"."person_reference" VALUES
(1, 5, 4, 'FICHA', 'NNA', 'Nombre NNA sintetico', 'nombre nna sintetico', NULL, NULL, NULL, FALSE, 1795532157595),
(2, 6, NULL, 'FORMULARIO', 'PRESENTADOR', U&'Mar\00eda Jos\00e9 De los \00c1ngeles Del R\00edo Gonz\00e1lez', 'maria jose de los angeles del rio gonzalez', '12345678', '5', NULL, TRUE, 1795532157657),
(3, 6, NULL, 'FORMULARIO', 'AFECTADO_NNA', U&'Tom\00e1s', 'tomas', NULL, NULL, NULL, FALSE, 1795532157657),
(4, 6, NULL, 'FORMULARIO', 'ADULTO_RELACIONADO', 'Rosa', 'rosa', NULL, NULL, NULL, FALSE, 1795532157657),
(5, 7, NULL, 'FORMULARIO', 'PRESENTADOR', 'NOMBRE SINTETICO APELLIDO SINTETICO', 'nombre sintetico apellido sintetico', '99999999', '9', NULL, FALSE, 1795532157673),
(6, 8, NULL, 'FORMULARIO', 'PRESENTADOR', 'NOMBRE SINTETICO APELLIDO SINTETICO', 'nombre sintetico apellido sintetico', '99999999', '9', NULL, FALSE, 1795532157687),
(8, 9, 5, 'FICHA', 'REQUIRENTE', 'Persona Sintetica', 'persona sintetica', '11111111', '1', NULL, FALSE, 1795532157707),
(9, 9, 5, 'FICHA', 'NNA', 'Nino sintetico', 'nino sintetico', NULL, NULL, NULL, FALSE, 1795532157707),
(10, 10, 6, 'FICHA', 'NNA', 'Nina sintetica', 'nina sintetica', NULL, NULL, NULL, FALSE, 1795532157717),
(11, 18, NULL, 'FORMULARIO', 'PRESENTADOR', U&'Mar\00eda Jos\00e9 De los \00c1ngeles Del R\00edo Gonz\00e1lez', 'maria jose de los angeles del rio gonzalez', '12345678', '5', NULL, TRUE, 1795805923594),
(12, 18, NULL, 'FORMULARIO', 'AFECTADO_NNA', U&'Tom\00e1s', 'tomas', NULL, NULL, NULL, FALSE, 1795805923594),
(13, 18, NULL, 'FORMULARIO', 'ADULTO_RELACIONADO', 'Rosa', 'rosa', NULL, NULL, NULL, FALSE, 1795805923594),
(14, 19, NULL, 'COM', 'REQUIRENTE', 'Persona Sintetica', 'persona sintetica', '11111111', '1', NULL, FALSE, 1795805923685),
(15, 20, NULL, 'COM', 'REQUIRENTE', 'Otra Persona', 'otra persona', NULL, NULL, NULL, FALSE, 1795805923733);
CREATE INDEX "public"."ix_person_reference_run" ON "public"."person_reference"("run_number" NULLS FIRST);
CREATE INDEX "public"."ix_person_reference_document" ON "public"."person_reference"("document_key" NULLS FIRST);
CREATE INDEX "public"."ix_person_reference_com" ON "public"."person_reference"("communication_id" NULLS FIRST);
CREATE MEMORY TABLE "public"."requirement"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 12) DEFAULT ON NULL NOT NULL,
    "folio" CHARACTER VARYING(48),
    "communication_id" BIGINT NOT NULL,
    "solicitation_id" BIGINT,
    "principal_scope" BIGINT NOT NULL,
    "attention_user" BIGINT,
    "attention_scope" BIGINT NOT NULL,
    "complexity" CHARACTER VARYING(12),
    "state" CHARACTER VARYING(16) DEFAULT 'ABIERTO' NOT NULL,
    "created_ms" BIGINT NOT NULL,
    "created_by" BIGINT NOT NULL,
    "closed_ms" BIGINT,
    "closed_by" BIGINT,
    "close_reason" CHARACTER VARYING,
    "revision" BIGINT DEFAULT 0 NOT NULL,
    "matter" CHARACTER VARYING(8),
    "immediate_result" CHARACTER VARYING(24),
    "immediate_result_reconstructed" BOOLEAN DEFAULT FALSE NOT NULL
);
ALTER TABLE "public"."requirement" ADD CONSTRAINT "public"."CONSTRAINT_15" PRIMARY KEY("id");
-- 11 +/- SELECT COUNT(*) FROM public.requirement;
INSERT INTO "public"."requirement" VALUES
(1, 'REQ-A-000001', 1, 1, 4, 3, 4, 'BAJA', 'ABIERTO', 1795532157328, 4, NULL, NULL, NULL, 9, NULL, NULL, FALSE),
(2, 'REQ-A-000002', 2, 2, 4, 3, 4, NULL, 'ABIERTO', 1795532157545, 4, NULL, NULL, NULL, 0, NULL, NULL, FALSE),
(3, 'REQ-A-000003', 4, NULL, 4, 2, 4, NULL, 'CERRADO', 1795532157581, 2, 1795531857577, 2, 'Orientacion entregada', 1, NULL, 'ORIENTACION', TRUE),
(4, 'REQ-A-000004', 5, NULL, 4, 3, 4, NULL, 'CERRADO', 1795532157593, 2, 1795532157625, 3, 'Orientacion concluida', 3, NULL, 'ORIENTACION', TRUE),
(5, 'REQ-A-000005', 9, NULL, 4, 2, 4, NULL, 'ABIERTO', 1795532157696, 2, NULL, NULL, NULL, 1, NULL, NULL, FALSE),
(6, 'REQ-A-000006', 10, NULL, 4, 2, 4, NULL, 'ABIERTO', 1795532157715, 2, NULL, NULL, NULL, 0, NULL, NULL, FALSE),
(7, 'REQ-A-000007', 11, 4, 2, 6, 7, NULL, 'CERRADO', 1795532157740, 5, 1795532157762, 7, 'Cierre sintetico tras transferencia', 4, NULL, NULL, FALSE),
(8, 'REQ-A-000008', 12, 5, 1, 6, 7, 'MEDIANA', 'ABIERTO', 1795532157792, 5, NULL, NULL, NULL, 6, NULL, NULL, FALSE),
(9, 'REQ-A-000009', 18, 8, 4, 3, 4, 'MEDIANA', 'ABIERTO', 1795805923629, 4, NULL, NULL, NULL, 2, NULL, NULL, FALSE),
(10, 'REQ-A-000010', 19, 9, 4, 3, 4, 'BAJA', 'ABIERTO', 1795805923709, 4, NULL, NULL, NULL, 2, NULL, NULL, FALSE),
(11, 'REQ-A-000011', 20, 10, 4, 3, 4, 'BAJA', 'ABIERTO', 1795805923755, 4, NULL, NULL, NULL, 3, NULL, NULL, FALSE);
CREATE INDEX "public"."ix_req_queue" ON "public"."requirement"("state" NULLS FIRST, "principal_scope" NULLS FIRST, "id" NULLS FIRST);
CREATE INDEX "public"."ix_requirement_created" ON "public"."requirement"("created_ms" NULLS FIRST);
CREATE MEMORY TABLE "public"."territorial_coverage"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 3) DEFAULT ON NULL NOT NULL,
    "scope_id" BIGINT,
    "region_code" CHARACTER VARYING(4) NOT NULL,
    "from_ms" BIGINT NOT NULL,
    "from_by" BIGINT NOT NULL,
    "from_basis" CHARACTER VARYING NOT NULL,
    "until_ms" BIGINT,
    "until_by" BIGINT,
    "until_basis" CHARACTER VARYING
);
ALTER TABLE "public"."territorial_coverage" ADD CONSTRAINT "public"."CONSTRAINT_81" PRIMARY KEY("id");
-- 2 +/- SELECT COUNT(*) FROM public.territorial_coverage;
INSERT INTO "public"."territorial_coverage" VALUES
(1, 14, '07', 1795532157968, 16, 'Acto sintetico', NULL, NULL, NULL),
(2, NULL, '13', 1795532157976, 16, 'Acto sintetico', NULL, NULL, NULL);
CREATE INDEX "public"."ix_territorial_coverage_scope" ON "public"."territorial_coverage"("scope_id" NULLS FIRST, "until_ms" NULLS FIRST);
CREATE MEMORY TABLE "public"."substitution_scope"(
    "substitution_id" BIGINT NOT NULL,
    "grant_id" BIGINT NOT NULL,
    "scope_id" BIGINT NOT NULL,
    "role_code" CHARACTER VARYING(32) NOT NULL
);
ALTER TABLE "public"."substitution_scope" ADD CONSTRAINT "public"."CONSTRAINT_87" PRIMARY KEY("substitution_id", "grant_id");
-- 0 +/- SELECT COUNT(*) FROM public.substitution_scope;
CREATE MEMORY TABLE "public"."business_holiday"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) DEFAULT ON NULL NOT NULL,
    "holiday_day" CHARACTER(10) NOT NULL,
    "region_code" CHARACTER VARYING(4),
    "region_key" CHARACTER VARYING(4) NOT NULL,
    "name" CHARACTER VARYING(120) NOT NULL,
    "created_by" BIGINT NOT NULL,
    "created_ms" BIGINT NOT NULL
);
ALTER TABLE "public"."business_holiday" ADD CONSTRAINT "public"."CONSTRAINT_E" PRIMARY KEY("id");
-- 0 +/- SELECT COUNT(*) FROM public.business_holiday;
CREATE UNIQUE NULLS DISTINCT INDEX "public"."ux_business_holiday_day_region" ON "public"."business_holiday"("holiday_day" NULLS FIRST, "region_key" NULLS FIRST);
CREATE MEMORY TABLE "public"."case_record"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 8) DEFAULT ON NULL NOT NULL,
    "folio" CHARACTER VARYING(48),
    "requirement_id" BIGINT NOT NULL,
    "scope_id" BIGINT NOT NULL,
    "responsible_id" BIGINT NOT NULL,
    "objective" CHARACTER VARYING NOT NULL,
    "matter" CHARACTER VARYING(24) NOT NULL,
    "state" CHARACTER VARYING(24) DEFAULT 'EN_GESTION' NOT NULL,
    "created_ms" BIGINT NOT NULL,
    "created_by" BIGINT NOT NULL,
    "closed_ms" BIGINT,
    "closed_by" BIGINT,
    "closure_mode" CHARACTER VARYING(32),
    "close_reason" CHARACTER VARYING,
    "origin_case_id" BIGINT,
    "revision" BIGINT DEFAULT 0 NOT NULL
);
ALTER TABLE "public"."case_record" ADD CONSTRAINT "public"."CONSTRAINT_2" PRIMARY KEY("id");
-- 7 +/- SELECT COUNT(*) FROM public.case_record;
INSERT INTO "public"."case_record" VALUES
(1, 'CAS-A-000001', 1, 4, 4, 'Seguimiento sintetico', 'UPR', 'EN_GESTION', 1795532157346, 4, NULL, NULL, NULL, NULL, NULL, 14),
(2, 'CAS-A-000002', 1, 8, 8, 'Apoyo sintetico', 'UPD', 'CERRADO', 1795532157463, 7, 1795532157476, 8, 'PROFESIONAL_BAJA', 'Apoyo entregado', NULL, 1),
(3, 'CAS-A-000003', 8, 7, 6, 'Seguimiento sintetico', 'UPR', 'CERRADO', 1795532157806, 5, 1795532157830, 7, 'DERIVACION', 'Aceptada; sucesor 4', NULL, 1),
(4, 'CAS-A-000004', 8, 8, 8, 'Destino sintetico', 'UPR', 'EN_GESTION', 1795532157827, 7, NULL, NULL, NULL, NULL, 3, 0),
(5, 'CAS-A-000005', 9, 4, 3, 'Gestion sintetica', 'UPR', 'EN_GESTION', 1795805923648, 4, NULL, NULL, NULL, NULL, NULL, 1),
(6, 'CAS-A-000006', 10, 4, 3, 'Gestion sintetica', 'UPR', 'EN_GESTION', 1795805923723, 4, NULL, NULL, NULL, NULL, NULL, 0),
(7, 'CAS-A-000007', 11, 4, 3, 'Gestion sintetica', 'UPR', 'CERRADO', 1795805923769, 4, 1795805923790, 3, 'PROFESIONAL_BAJA', 'Cierre sintetico', NULL, 2);
CREATE INDEX "public"."ix_case_req" ON "public"."case_record"("requirement_id" NULLS FIRST, "state" NULLS FIRST);
CREATE INDEX "public"."ix_case_scope_state" ON "public"."case_record"("scope_id" NULLS FIRST, "state" NULLS FIRST, "id" NULLS FIRST);
CREATE INDEX "public"."ix_case_owner_state" ON "public"."case_record"("responsible_id" NULLS FIRST, "state" NULLS FIRST, "id" NULLS FIRST);
CREATE INDEX "public"."ix_case_created" ON "public"."case_record"("created_ms" NULLS FIRST);
CREATE MEMORY TABLE "public"."milestone"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 98) DEFAULT ON NULL NOT NULL,
    "event" CHARACTER VARYING(32) NOT NULL,
    "resource_type" CHARACTER VARYING(16) NOT NULL,
    "resource_id" BIGINT NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "requirement_id" BIGINT,
    "case_id" BIGINT,
    "actor_id" BIGINT NOT NULL,
    "subject_user" BIGINT,
    "scope_id" BIGINT,
    "at_ms" BIGINT NOT NULL,
    "detail" CHARACTER VARYING(32),
    "reconstructed" BOOLEAN DEFAULT FALSE NOT NULL
);
ALTER TABLE "public"."milestone" ADD CONSTRAINT "public"."CONSTRAINT_C0" PRIMARY KEY("id");
-- 97 +/- SELECT COUNT(*) FROM public.milestone;
INSERT INTO "public"."milestone" VALUES
(1, 'COM_REGISTRADA', 'COM', 1, 1, NULL, NULL, 2, NULL, 4, 1795532157265, NULL, FALSE),
(2, 'SOL_DISTRIBUIDA', 'SOL', 1, 1, NULL, NULL, 1, NULL, 4, 1795532157287, NULL, FALSE),
(3, 'SOL_REDIRIGIDA', 'SOL', 1, 1, NULL, NULL, 4, NULL, 2, 1795532157299, NULL, FALSE),
(4, 'SOL_DEVUELTA', 'SOL', 1, 1, NULL, NULL, 7, NULL, 2, 1795532157308, NULL, FALSE),
(5, 'SOL_REDIRIGIDA', 'SOL', 1, 1, NULL, NULL, 1, NULL, 4, 1795532157317, NULL, FALSE),
(6, 'REQ_CREADO', 'REQ', 1, 1, 1, NULL, 4, 3, 4, 1795532157328, NULL, FALSE),
(7, 'SOL_ASUMIDA', 'SOL', 1, 1, 1, NULL, 4, 3, 4, 1795532157328, NULL, FALSE),
(8, 'CASO_CREADO', 'CAS', 1, 1, 1, 1, 4, 3, 4, 1795532157346, NULL, FALSE),
(9, 'CASO_ASIGNADO', 'ASIGNACION', 1, 1, 1, 1, 4, 3, 4, 1795532157348, NULL, FALSE),
(10, 'ASIGNACION_ACUSADA', 'ASIGNACION', 1, 1, 1, 1, 3, 3, 4, 1795532157354, NULL, FALSE),
(11, 'CASO_ESPERA', 'CAS', 1, 1, 1, 1, 4, NULL, 4, 1795532157362, NULL, FALSE),
(12, 'CASO_REANUDADO', 'CAS', 1, 1, 1, 1, 4, NULL, 4, 1795532157368, NULL, FALSE),
(13, 'TAREA_CREADA', 'TAREA', 1, 1, 1, 1, 3, 3, 4, 1795532157375, NULL, FALSE),
(14, 'TAREA_CREADA', 'TAREA', 2, 1, 1, 1, 3, 3, 4, 1795532157384, NULL, FALSE),
(15, 'TAREA_COMPLETADA', 'TAREA', 1, 1, 1, 1, 3, 3, 4, 1795532157391, NULL, FALSE),
(16, 'TAREA_CANCELADA', 'TAREA', 2, 1, 1, 1, 3, 3, 4, 1795532157397, NULL, FALSE),
(17, 'CIERRE_SOLICITADO', 'CAS', 1, 1, 1, 1, 3, NULL, 4, 1795532157408, NULL, FALSE),
(18, 'CIERRE_RETIRADO', 'CAS', 1, 1, 1, 1, 3, NULL, 4, 1795532157416, NULL, FALSE),
(19, 'CIERRE_SOLICITADO', 'CAS', 1, 1, 1, 1, 3, NULL, 4, 1795532157424, NULL, FALSE),
(20, 'CIERRE_DEVUELTO', 'CAS', 1, 1, 1, 1, 4, NULL, 4, 1795532157432, NULL, FALSE),
(21, 'CASO_ASIGNADO', 'ASIGNACION', 2, 1, 1, 1, 4, 4, 4, 1795532157440, NULL, FALSE),
(22, 'FLUJO_SOLICITADO', 'FLUJO', 1, 1, 1, NULL, 4, NULL, 2, 1795532157449, 'COLABORACION', FALSE),
(23, 'CASO_CREADO', 'CAS', 2, 1, 1, 2, 7, 8, 8, 1795532157463, NULL, FALSE),
(24, 'CASO_ASIGNADO', 'ASIGNACION', 3, 1, 1, 2, 7, 8, 8, 1795532157465, NULL, FALSE),
(25, 'FLUJO_DECIDIDO', 'FLUJO', 1, 1, 1, NULL, 7, NULL, 2, 1795532157466, 'ACEPTADA', FALSE),
(26, 'CASO_CERRADO', 'CAS', 2, 1, 1, 2, 8, NULL, 8, 1795532157476, 'PROFESIONAL_BAJA', FALSE),
(27, 'CASO_CERRADO', 'CAS', 1, 1, 1, 1, 4, NULL, 4, 1795532157485, 'JEFATURA_PROPIA', FALSE),
(28, 'REQ_CERRADO', 'REQ', 1, 1, 1, NULL, 4, NULL, 4, 1795532157499, 'JEFATURA_PRINCIPAL', FALSE),
(29, 'REQ_REACTIVADO', 'REQ', 1, 1, 1, NULL, 4, NULL, 4, 1795532157514, NULL, FALSE),
(30, 'CASO_REABIERTO', 'CAS', 1, 1, 1, 1, 4, NULL, 4, 1795532157528, NULL, FALSE),
(31, 'COM_REGISTRADA', 'COM', 2, 2, NULL, NULL, 4, NULL, 4, 1795532157533, NULL, FALSE),
(32, 'REQ_CREADO', 'REQ', 2, 2, 2, NULL, 4, 3, 4, 1795532157545, NULL, FALSE),
(33, 'SOL_ASUMIDA', 'SOL', 2, 2, 2, NULL, 4, 3, 4, 1795532157545, 'DIRECTA', FALSE),
(34, 'COM_REGISTRADA', 'COM', 3, 3, NULL, NULL, 2, NULL, 4, 1795532157556, NULL, FALSE),
(35, 'SOL_DISTRIBUIDA', 'SOL', 3, 3, NULL, NULL, 1, NULL, 4, 1795532157566, NULL, FALSE),
(36, 'SOL_DESCARTADA', 'SOL', 3, 3, NULL, NULL, 4, NULL, 4, 1795532157574, 'DUPLICADA', FALSE),
(37, 'COM_REGISTRADA', 'COM', 4, 4, NULL, NULL, 2, NULL, 4, 1795532157579, NULL, FALSE),
(38, 'REQ_CREADO', 'REQ', 3, 4, 3, NULL, 2, 2, 4, 1795532157581, NULL, FALSE),
(39, 'REQ_CERRADO', 'REQ', 3, 4, 3, NULL, 2, NULL, 4, 1795532157581, 'PRIMERA_ATENCION', FALSE),
(40, 'COM_REGISTRADA', 'COM', 5, 5, NULL, NULL, 2, NULL, 4, 1795532157591, NULL, FALSE),
(41, 'REQ_CREADO', 'REQ', 4, 5, 4, NULL, 2, 2, 4, 1795532157593, NULL, FALSE),
(42, 'ENTREGA_ENVIADA', 'ENTREGA', 1, 5, 4, NULL, 2, 3, 4, 1795532157609, NULL, FALSE),
(43, 'ENTREGA_DECIDIDA', 'ENTREGA', 1, 5, 4, NULL, 3, 3, 4, 1795532157616, 'RECIBIDA', FALSE),
(44, 'REQ_CERRADO', 'REQ', 4, 5, 4, NULL, 3, NULL, 4, 1795532157625, 'PRIMERA_ATENCION', FALSE),
(45, 'COM_REGISTRADA', 'COM', 6, 6, NULL, NULL, 2, NULL, 4, 1795532157647, NULL, FALSE),
(46, 'COM_REGISTRADA', 'COM', 7, 7, NULL, NULL, 2, NULL, 4, 1795532157669, NULL, FALSE),
(47, 'COM_REGISTRADA', 'COM', 8, 8, NULL, NULL, 2, NULL, 4, 1795532157683, NULL, FALSE);
INSERT INTO "public"."milestone" VALUES
(48, 'COM_REGISTRADA', 'COM', 9, 9, NULL, NULL, 2, NULL, 4, 1795532157694, NULL, FALSE),
(49, 'REQ_CREADO', 'REQ', 5, 9, 5, NULL, 2, 2, 4, 1795532157696, NULL, FALSE),
(50, 'COM_REGISTRADA', 'COM', 10, 10, NULL, NULL, 2, NULL, 4, 1795532157714, NULL, FALSE),
(51, 'REQ_CREADO', 'REQ', 6, 10, 6, NULL, 2, 2, 4, 1795532157715, NULL, FALSE),
(52, 'COM_REGISTRADA', 'COM', 11, 11, NULL, NULL, 1, NULL, 4, 1795532157723, NULL, FALSE),
(53, 'SOL_DISTRIBUIDA', 'SOL', 4, 11, NULL, NULL, 1, NULL, 1, 1795532157732, NULL, FALSE),
(54, 'REQ_CREADO', 'REQ', 7, 11, 7, NULL, 5, 6, 1, 1795532157740, NULL, FALSE),
(55, 'SOL_ASUMIDA', 'SOL', 4, 11, 7, NULL, 5, 6, 1, 1795532157740, NULL, FALSE),
(56, 'FLUJO_SOLICITADO', 'FLUJO', 2, 11, 7, NULL, 5, NULL, 2, 1795532157747, 'TRANSFERENCIA', FALSE),
(57, 'FLUJO_DECIDIDO', 'FLUJO', 2, 11, 7, NULL, 7, NULL, 2, 1795532157754, 'ACEPTADA', FALSE),
(58, 'REQ_CERRADO', 'REQ', 7, 11, 7, NULL, 7, NULL, 2, 1795532157762, 'JEFATURA_PRINCIPAL', FALSE),
(59, 'COM_REGISTRADA', 'COM', 12, 12, NULL, NULL, 1, NULL, 4, 1795532157768, NULL, FALSE),
(60, 'SOL_DISTRIBUIDA', 'SOL', 5, 12, NULL, NULL, 1, NULL, 1, 1795532157777, NULL, FALSE),
(61, 'REQ_CREADO', 'REQ', 8, 12, 8, NULL, 5, 6, 1, 1795532157792, NULL, FALSE),
(62, 'SOL_ASUMIDA', 'SOL', 5, 12, 8, NULL, 5, 6, 1, 1795532157792, NULL, FALSE),
(63, 'CASO_CREADO', 'CAS', 3, 12, 8, 3, 5, 6, 7, 1795532157806, NULL, FALSE),
(64, 'CASO_ASIGNADO', 'ASIGNACION', 4, 12, 8, 3, 5, 6, 7, 1795532157807, NULL, FALSE),
(65, 'FLUJO_SOLICITADO', 'FLUJO', 3, 12, 8, 3, 5, NULL, 2, 1795532157816, 'DERIVACION', FALSE),
(66, 'CASO_CREADO', 'CAS', 4, 12, 8, 4, 7, 8, 8, 1795532157827, NULL, FALSE),
(67, 'CASO_ASIGNADO', 'ASIGNACION', 5, 12, 8, 4, 7, 8, 8, 1795532157828, NULL, FALSE),
(68, 'CASO_CERRADO', 'CAS', 3, 12, 8, 3, 7, NULL, 7, 1795532157830, 'DERIVACION', FALSE),
(69, 'FLUJO_DECIDIDO', 'FLUJO', 3, 12, 8, 3, 7, NULL, 2, 1795532157834, 'ACEPTADA', FALSE),
(70, 'COM_REGISTRADA', 'COM', 13, 13, NULL, NULL, 1, NULL, 4, 1795532157842, NULL, FALSE),
(71, 'COM_REGISTRADA', 'COM', 14, 14, NULL, NULL, 1, NULL, 4, 1795532157868, NULL, FALSE),
(72, 'COM_REGISTRADA', 'COM', 15, 15, NULL, NULL, 1, NULL, 4, 1795532157903, NULL, FALSE),
(73, 'SOL_DISTRIBUIDA', 'SOL', 6, 15, NULL, NULL, 1, NULL, 4, 1795532157919, NULL, FALSE),
(74, 'SOL_DESCARTADA', 'SOL', 6, 15, NULL, NULL, 4, NULL, 4, 1795532157928, 'INVALIDA', FALSE),
(75, 'COM_REGISTRADA', 'COM', 16, 16, NULL, NULL, 1, NULL, 4, 1795532157933, NULL, FALSE),
(76, 'SOL_DISTRIBUIDA', 'SOL', 7, 16, NULL, NULL, 1, NULL, 2, 1795532157943, NULL, FALSE),
(77, 'COM_REGISTRADA', 'COM', 17, 17, NULL, NULL, 2, NULL, 4, 1795532157951, NULL, FALSE),
(78, 'COM_REGISTRADA', 'COM', 18, 18, NULL, NULL, 2, NULL, 4, 1795805923580, NULL, FALSE),
(79, 'SOL_DISTRIBUIDA', 'SOL', 8, 18, NULL, NULL, 1, NULL, 4, 1795805923614, NULL, FALSE),
(80, 'REQ_CREADO', 'REQ', 9, 18, 9, NULL, 4, 3, 4, 1795805923629, NULL, FALSE),
(81, 'SOL_ASUMIDA', 'SOL', 8, 18, 9, NULL, 4, 3, 4, 1795805923629, NULL, FALSE),
(82, 'CASO_CREADO', 'CAS', 5, 18, 9, 5, 4, 3, 4, 1795805923648, NULL, FALSE),
(83, 'CASO_ASIGNADO', 'ASIGNACION', 6, 18, 9, 5, 4, 3, 4, 1795805923650, NULL, FALSE),
(84, 'COM_REGISTRADA', 'COM', 19, 19, NULL, NULL, 4, NULL, 4, 1795805923677, NULL, FALSE),
(85, 'SOL_DISTRIBUIDA', 'SOL', 9, 19, NULL, NULL, 1, NULL, 4, 1795805923699, NULL, FALSE),
(86, 'REQ_CREADO', 'REQ', 10, 19, 10, NULL, 4, 3, 4, 1795805923709, NULL, FALSE),
(87, 'SOL_ASUMIDA', 'SOL', 9, 19, 10, NULL, 4, 3, 4, 1795805923709, NULL, FALSE),
(88, 'CASO_CREADO', 'CAS', 6, 19, 10, 6, 4, 3, 4, 1795805923723, NULL, FALSE),
(89, 'CASO_ASIGNADO', 'ASIGNACION', 7, 19, 10, 6, 4, 3, 4, 1795805923724, NULL, FALSE),
(90, 'COM_REGISTRADA', 'COM', 20, 20, NULL, NULL, 4, NULL, 4, 1795805923730, NULL, FALSE),
(91, 'SOL_DISTRIBUIDA', 'SOL', 10, 20, NULL, NULL, 1, NULL, 4, 1795805923745, NULL, FALSE),
(92, 'REQ_CREADO', 'REQ', 11, 20, 11, NULL, 4, 3, 4, 1795805923755, NULL, FALSE),
(93, 'SOL_ASUMIDA', 'SOL', 10, 20, 11, NULL, 4, 3, 4, 1795805923755, NULL, FALSE),
(94, 'CASO_CREADO', 'CAS', 7, 20, 11, 7, 4, 3, 4, 1795805923769, NULL, FALSE);
INSERT INTO "public"."milestone" VALUES
(95, 'CASO_ASIGNADO', 'ASIGNACION', 8, 20, 11, 7, 4, 3, 4, 1795805923770, NULL, FALSE),
(96, 'CASO_CERRADO', 'CAS', 7, 20, 11, 7, 3, NULL, 4, 1795805923790, 'PROFESIONAL_BAJA', FALSE),
(97, 'COM_REGISTRADA', 'COM', 21, 21, NULL, NULL, 4, NULL, 4, 1795805923798, NULL, FALSE);
CREATE INDEX "public"."ix_milestone_event_at" ON "public"."milestone"("event" NULLS FIRST, "at_ms" NULLS FIRST);
CREATE INDEX "public"."ix_milestone_requirement" ON "public"."milestone"("requirement_id" NULLS FIRST);
CREATE INDEX "public"."ix_milestone_resource" ON "public"."milestone"("resource_type" NULLS FIRST, "resource_id" NULLS FIRST);
CREATE INDEX "public"."ix_milestone_communication" ON "public"."milestone"("communication_id" NULLS FIRST);
CREATE MEMORY TABLE "public"."case_assignment"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 9) DEFAULT ON NULL NOT NULL,
    "case_id" BIGINT NOT NULL,
    "user_id" BIGINT NOT NULL,
    "assigned_by" BIGINT NOT NULL,
    "assigned_ms" BIGINT NOT NULL,
    "acknowledged_ms" BIGINT,
    "current_flag" BOOLEAN NOT NULL
);
ALTER TABLE "public"."case_assignment" ADD CONSTRAINT "public"."CONSTRAINT_D" PRIMARY KEY("id");
-- 8 +/- SELECT COUNT(*) FROM public.case_assignment;
INSERT INTO "public"."case_assignment" VALUES
(1, 1, 3, 4, 1795532157348, 1795532157354, FALSE),
(2, 1, 4, 4, 1795532157440, NULL, TRUE),
(3, 2, 8, 7, 1795532157465, NULL, TRUE),
(4, 3, 6, 5, 1795532157807, NULL, TRUE),
(5, 4, 8, 7, 1795532157828, NULL, TRUE),
(6, 5, 3, 4, 1795805923650, NULL, TRUE),
(7, 6, 3, 4, 1795805923724, NULL, TRUE),
(8, 7, 3, 4, 1795805923770, NULL, TRUE);
CREATE INDEX "public"."ix_assignment_current" ON "public"."case_assignment"("case_id" NULLS FIRST, "current_flag" NULLS FIRST, "acknowledged_ms" NULLS FIRST);
CREATE MEMORY TABLE "public"."formulario_consumer_lock"(
    "id" INTEGER NOT NULL,
    "holder" CHARACTER VARYING(36),
    "until_ms" BIGINT DEFAULT 0 NOT NULL
);
ALTER TABLE "public"."formulario_consumer_lock" ADD CONSTRAINT "public"."CONSTRAINT_1B" PRIMARY KEY("id");
-- 1 +/- SELECT COUNT(*) FROM public.formulario_consumer_lock;
INSERT INTO "public"."formulario_consumer_lock" VALUES
(1, NULL, 0);
CREATE MEMORY TABLE "public"."case_member"(
    "case_id" BIGINT NOT NULL,
    "user_id" BIGINT NOT NULL,
    "added_by" BIGINT NOT NULL,
    "added_ms" BIGINT NOT NULL
);
ALTER TABLE "public"."case_member" ADD CONSTRAINT "public"."CONSTRAINT_1C" PRIMARY KEY("case_id", "user_id");
-- 0 +/- SELECT COUNT(*) FROM public.case_member;
CREATE MEMORY TABLE "public"."integration_control"(
    "id" BIGINT NOT NULL,
    "name" CHARACTER VARYING(32) NOT NULL,
    "paused" BOOLEAN DEFAULT FALSE NOT NULL,
    "revision" BIGINT DEFAULT 0 NOT NULL,
    "updated_ms" BIGINT DEFAULT 0 NOT NULL,
    "updated_by" BIGINT,
    "reason" CHARACTER VARYING(1000)
);
ALTER TABLE "public"."integration_control" ADD CONSTRAINT "public"."CONSTRAINT_6D" PRIMARY KEY("id");
-- 2 +/- SELECT COUNT(*) FROM public.integration_control;
INSERT INTO "public"."integration_control" VALUES
(1, 'FORMULARIO_PULL', FALSE, 0, 0, NULL, NULL),
(2, 'CORREO_GRAPH', FALSE, 0, 0, NULL, NULL);
CREATE MEMORY TABLE "public"."management_note"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 5) DEFAULT ON NULL NOT NULL,
    "requirement_id" BIGINT NOT NULL,
    "case_id" BIGINT,
    "author_id" BIGINT NOT NULL,
    "effective_ms" BIGINT NOT NULL,
    "registered_ms" BIGINT NOT NULL,
    "body" CHARACTER VARYING NOT NULL,
    "reserved" BOOLEAN DEFAULT FALSE NOT NULL,
    "rectifies_id" BIGINT,
    "correction_reason" CHARACTER VARYING,
    "action_type" CHARACTER VARYING(40),
    "action_catalog_version" BIGINT
);
ALTER TABLE "public"."management_note" ADD CONSTRAINT "public"."CONSTRAINT_2A" PRIMARY KEY("id");
-- 4 +/- SELECT COUNT(*) FROM public.management_note;
INSERT INTO "public"."management_note" VALUES
(1, 3, NULL, 2, 1795531857577, 1795532157582, 'ORIENTACION: Orientacion entregada', FALSE, NULL, NULL, NULL, NULL),
(2, 4, NULL, 3, 1795532152621, 1795532157625, 'ORIENTACION: Orientacion concluida', FALSE, NULL, NULL, NULL, NULL),
(3, 9, 5, 3, 1795805323666, 1795805923671, 'Constancia sintetica', FALSE, NULL, NULL, 'OFICIO', 6),
(4, 11, 7, 3, 1795805623780, 1795805923783, 'Gestion realizada', FALSE, NULL, NULL, 'ORIENTACION', 6);
CREATE MEMORY TABLE "public"."task_item"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 3) DEFAULT ON NULL NOT NULL,
    "case_id" BIGINT NOT NULL,
    "title" CHARACTER VARYING(300) NOT NULL,
    "executor_id" BIGINT NOT NULL,
    "created_by" BIGINT NOT NULL,
    "created_ms" BIGINT NOT NULL,
    "due_ms" BIGINT,
    "mandatory" BOOLEAN DEFAULT FALSE NOT NULL,
    "state" CHARACTER VARYING(20) DEFAULT 'PENDIENTE' NOT NULL,
    "revision" BIGINT DEFAULT 0 NOT NULL,
    "reminder_interval_hours" INTEGER DEFAULT 24 NOT NULL,
    "next_reminder_ms" BIGINT,
    "action_type" CHARACTER VARYING(40),
    "action_catalog_version" BIGINT
);
ALTER TABLE "public"."task_item" ADD CONSTRAINT "public"."CONSTRAINT_AC" PRIMARY KEY("id");
-- 2 +/- SELECT COUNT(*) FROM public.task_item;
INSERT INTO "public"."task_item" VALUES
(1, 1, 'Llamada sintetica', 3, 3, 1795532157375, NULL, FALSE, 'COMPLETADA', 1, 24, NULL, NULL, NULL),
(2, 1, 'Oficio sintetico', 3, 3, 1795532157384, NULL, FALSE, 'CANCELADA', 1, 24, NULL, NULL, NULL);
CREATE INDEX "public"."ix_task_due_reminder" ON "public"."task_item"("state" NULLS FIRST, "next_reminder_ms" NULLS FIRST);
CREATE INDEX "public"."ix_task_owner_due" ON "public"."task_item"("executor_id" NULLS FIRST, "state" NULLS FIRST, "due_ms" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."flow_request"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 4) DEFAULT ON NULL NOT NULL,
    "kind" CHARACTER VARYING(24) NOT NULL,
    "requirement_id" BIGINT NOT NULL,
    "origin_case_id" BIGINT,
    "origin_scope" BIGINT NOT NULL,
    "target_scope" BIGINT NOT NULL,
    "requested_by" BIGINT NOT NULL,
    "created_ms" BIGINT NOT NULL,
    "reason" CHARACTER VARYING NOT NULL,
    "objective" CHARACTER VARYING,
    "state" CHARACTER VARYING(16) DEFAULT 'PENDIENTE' NOT NULL,
    "decided_by" BIGINT,
    "decided_ms" BIGINT,
    "decision_reason" CHARACTER VARYING,
    "successor_case_id" BIGINT,
    "revision" BIGINT DEFAULT 0 NOT NULL
);
ALTER TABLE "public"."flow_request" ADD CONSTRAINT "public"."CONSTRAINT_86" PRIMARY KEY("id");
-- 3 +/- SELECT COUNT(*) FROM public.flow_request;
INSERT INTO "public"."flow_request" VALUES
(1, 'COLABORACION', 1, NULL, 4, 2, 4, 1795532157449, 'Colaboracion sintetica', 'Apoyo sintetico', 'ACEPTADA', 7, 1795532157466, 'Aceptada', 2, 1),
(2, 'TRANSFERENCIA', 7, NULL, 1, 2, 5, 1795532157747, 'Corresponde a UPD', '', 'ACEPTADA', 7, 1795532157754, 'Aceptada', NULL, 1),
(3, 'DERIVACION', 8, 3, 7, 2, 5, 1795532157816, 'Competencia de otra unidad', 'Destino sintetico', 'ACEPTADA', 7, 1795532157834, 'Aceptada', 4, 1);
CREATE INDEX "public"."ix_flow_target_state" ON "public"."flow_request"("target_scope" NULLS FIRST, "state" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."document_item"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) DEFAULT ON NULL NOT NULL,
    "communication_id" BIGINT,
    "requirement_id" BIGINT,
    "case_id" BIGINT,
    "name" CHARACTER VARYING(180) NOT NULL,
    "storage_key" CHARACTER VARYING(80) NOT NULL,
    "sha256" CHARACTER(64) NOT NULL,
    "size_bytes" BIGINT NOT NULL,
    "content_type" CHARACTER VARYING(120) NOT NULL,
    "uploaded_by" BIGINT NOT NULL,
    "created_ms" BIGINT NOT NULL,
    "reserved" BOOLEAN DEFAULT FALSE NOT NULL,
    "quarantined" BOOLEAN DEFAULT TRUE NOT NULL,
    "note_id" BIGINT,
    "document_type" CHARACTER VARYING(32) DEFAULT 'OTRO' NOT NULL,
    "description" CHARACTER VARYING(500),
    "previous_document_id" BIGINT,
    "upload_key" CHARACTER VARYING(36)
);
ALTER TABLE "public"."document_item" ADD CONSTRAINT "public"."CONSTRAINT_5" PRIMARY KEY("id");
-- 0 +/- SELECT COUNT(*) FROM public.document_item;
CREATE UNIQUE NULLS DISTINCT INDEX "public"."ux_document_upload" ON "public"."document_item"("upload_key" NULLS FIRST);
CREATE UNIQUE NULLS DISTINCT INDEX "public"."ux_document_previous" ON "public"."document_item"("previous_document_id" NULLS FIRST);
CREATE MEMORY TABLE "public"."reopening"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 3) DEFAULT ON NULL NOT NULL,
    "requirement_id" BIGINT NOT NULL,
    "case_id" BIGINT,
    "requested_by" BIGINT NOT NULL,
    "requested_ms" BIGINT NOT NULL,
    "reason" CHARACTER VARYING NOT NULL,
    "state" CHARACTER VARYING(16) DEFAULT 'PENDIENTE' NOT NULL,
    "principal_approved_by" BIGINT,
    "principal_approved_ms" BIGINT,
    "principal_scope_at_approval" BIGINT,
    "decided_by" BIGINT,
    "decided_ms" BIGINT,
    "decision_reason" CHARACTER VARYING,
    "revision" BIGINT DEFAULT 0 NOT NULL
);
ALTER TABLE "public"."reopening" ADD CONSTRAINT "public"."CONSTRAINT_B" PRIMARY KEY("id");
-- 2 +/- SELECT COUNT(*) FROM public.reopening;
INSERT INTO "public"."reopening" VALUES
(1, 1, NULL, 4, 1795532157507, 'Nuevos antecedentes', 'ACEPTADA', NULL, NULL, NULL, 4, 1795532157515, 'Procede', 1),
(2, 1, 1, 4, 1795532157520, 'Reabrir el Caso', 'ACEPTADA', NULL, NULL, NULL, 4, 1795532157528, 'Procede', 1);
CREATE MEMORY TABLE "public"."integration_status"(
    "name" CHARACTER VARYING(32) NOT NULL,
    "state" CHARACTER VARYING(32) DEFAULT 'SIN_ACTIVIDAD' NOT NULL,
    "holder" CHARACTER VARYING(36),
    "cycles" BIGINT DEFAULT 0 NOT NULL,
    "failures" INTEGER DEFAULT 0 NOT NULL,
    "last_cycle_ms" BIGINT,
    "last_success_ms" BIGINT,
    "next_ms" BIGINT,
    "last_error" CHARACTER VARYING(64),
    "updated_ms" BIGINT DEFAULT 0 NOT NULL
);
ALTER TABLE "public"."integration_status" ADD CONSTRAINT "public"."CONSTRAINT_F5" PRIMARY KEY("name");
-- 2 +/- SELECT COUNT(*) FROM public.integration_status;
INSERT INTO "public"."integration_status" VALUES
('CORREO_GRAPH', 'SIN_ACTIVIDAD', NULL, 0, 0, NULL, NULL, NULL, NULL, 0),
('FORMULARIO_PULL', 'SIN_ACTIVIDAD', NULL, 0, 0, NULL, NULL, NULL, NULL, 0);
CREATE MEMORY TABLE "public"."attention_record"(
    "requirement_id" BIGINT NOT NULL,
    "modality" CHARACTER VARYING(16) NOT NULL,
    "data_json" CHARACTER VARYING NOT NULL,
    "followup_required" BOOLEAN DEFAULT FALSE NOT NULL,
    "updated_by" BIGINT NOT NULL,
    "updated_ms" BIGINT NOT NULL
);
ALTER TABLE "public"."attention_record" ADD CONSTRAINT "public"."CONSTRAINT_F" PRIMARY KEY("requirement_id");
-- 3 +/- SELECT COUNT(*) FROM public.attention_record;
INSERT INTO "public"."attention_record" VALUES
(4, 'NNA', '{"presenter_name":"","identity_document":"","identity_type":"","relationship":"","contact_email":"","contact_phone":"","presenter_domicile":"","region":"","commune":"","preferred_name":"","pronouns":"","supports":"","court":"","rit":"","ruc":"","guardian_name":"","nationality":"","group_description":"","approximate_count":"","motive":"Contacto sintetico NNA","story":"","request_text":"","observations":"","orientation":"","actions":"","commitments":"","institutions":"","safety_note":"","participation_note":"","child_message":"","identity_note":"","priority_note":"","safe_contact":"","judicial_status":"","guardian_status":"","participation":"NO","participation_mode":"","role_explained":"","priority":"","children_scope":"","modality":"NNA","schema_version":"1.0","course":"EN_ATENCION","people":[{"role":"NNA","preferred_name":"","nationality":"","relationship":"","name":"Nombre NNA sintetico","birth_date":"","schooling":"","location":"","identity_document":"","age":""}]}', FALSE, 2, 1795532157594),
(5, 'GENERAL', '{"presenter_first_name":"Persona","presenter_middle_name":"","presenter_last_name":"Sintetica","presenter_second_last_name":"","presenter_run_number":"11111111","presenter_run_dv":"1","presenter_birth_date":"","presenter_document_kind":"","presenter_document_number":"","presenter_name":"Persona Sintetica","identity_document":"11.111.111-1","identity_type":"RUN","relationship":"","contact_email":"persona@ejemplo.cl","contact_phone":"+56900000000","presenter_domicile":"","region":"07","commune":"Talca","preferred_name":"","pronouns":"","supports":"","court":"","rit":"","ruc":"","guardian_name":"","nationality":"Chile","group_description":"","approximate_count":"","motive":"Consulta sintetica","story":"","request_text":"","observations":"","orientation":"","actions":"","commitments":"","institutions":"","safety_note":"","participation_note":"","child_message":"","identity_note":"","priority_note":"","safe_contact":"","judicial_status":"","guardian_status":"","participation":"","participation_mode":"","role_explained":"","priority":"","children_scope":"","modality":"GENERAL","schema_version":"1.1","course":"EN_ATENCION","people":[{"role":"NNA","preferred_name":"","nationality":"","relationship":"","name":"Nino sintetico","birth_date":"","schooling":"","location":"","identity_document":"","age":"9"}]}', FALSE, 2, 1795532157706),
(6, 'NNA', '{"presenter_first_name":"","presenter_middle_name":"","presenter_last_name":"","presenter_second_last_name":"","presenter_run_number":"","presenter_run_dv":"","presenter_birth_date":"","presenter_document_kind":"","presenter_document_number":"","presenter_name":"","identity_document":"","identity_type":"","relationship":"","contact_email":"","contact_phone":"","presenter_domicile":"","region":"","commune":"","preferred_name":"","pronouns":"","supports":"","court":"","rit":"","ruc":"","guardian_name":"","nationality":"","group_description":"","approximate_count":"","motive":"Contacto sintetico NNA 1.1","story":"","request_text":"","observations":"","orientation":"","actions":"","commitments":"","institutions":"","safety_note":"","participation_note":"","child_message":"","identity_note":"","priority_note":"","safe_contact":"","judicial_status":"","guardian_status":"","participation":"NO","participation_mode":"","role_explained":"","priority":"","children_scope":"","modality":"NNA","schema_version":"1.1","course":"EN_ATENCION","people":[{"role":"NNA","preferred_name":"","nationality":"","relationship":"","name":"Nina sintetica","birth_date":"","schooling":"","location":"","identity_document":"","age":"doce"}]}', FALSE, 2, 1795532157716);
CREATE MEMORY TABLE "public"."audit_event"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 138) DEFAULT ON NULL NOT NULL,
    "actor_id" BIGINT NOT NULL,
    "at_ms" BIGINT NOT NULL,
    "action" CHARACTER VARYING(48) NOT NULL,
    "resource_type" CHARACTER VARYING(24) NOT NULL,
    "resource_id" BIGINT NOT NULL,
    "scope_id" BIGINT,
    "note" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."audit_event" ADD CONSTRAINT "public"."CONSTRAINT_36" PRIMARY KEY("id");
-- 137 +/- SELECT COUNT(*) FROM public.audit_event;
INSERT INTO "public"."audit_event" VALUES
(1, 16, 1795532157209, 'CONFIGURACION_SINTETICA', 'SISTEMA', 0, NULL, 'Cuentas y sede ficticias. No son designaciones institucionales.'),
(2, 2, 1795532157272, 'COM_REGISTRADA', 'COM', 1, 4, 'Canal TELEFONO'),
(3, 1, 1795532157287, 'SOL_DISTRIBUIDA', 'SOL', 1, 4, 'Clasificacion sintetica'),
(4, 4, 1795532157299, 'SOL_REDIRIGIDA', 'SOL', 1, 2, 'Destino anterior 4. Corresponde a UPD'),
(5, 7, 1795532157308, 'SOL_DEVUELTA_DISTRIBUCION', 'SOL', 1, 2, 'Vuelve a clasificacion'),
(6, 1, 1795532157317, 'SOL_REDIRIGIDA', 'SOL', 1, 4, 'Destino anterior 2. Redistribucion'),
(7, 4, 1795532157329, 'REQ_CREADO', 'REQ', 1, 4, 'COM 1'),
(8, 4, 1795532157330, 'SOL_ASUMIDA', 'SOL', 1, 4, 'Asumida'),
(9, 4, 1795532157338, 'REQ_COMPLEJIDAD', 'REQ', 1, 4, ' -> BAJA. Clasificacion sintetica'),
(10, 4, 1795532157349, 'CASO_CREADO', 'CAS', 1, 4, 'Seguimiento sintetico'),
(11, 3, 1795532157355, 'ASIGNACION_ACUSADA', 'CAS', 1, 4, 'Acuse manual.'),
(12, 4, 1795532157362, 'CASE_WAIT', 'CAS', 1, 4, 'Espera de antecedentes'),
(13, 4, 1795532157368, 'CASE_RESUME', 'CAS', 1, 4, 'Llegaron los antecedentes'),
(14, 3, 1795532157376, 'TAREA_CREADA', 'CAS', 1, 4, 'Agenda independiente de actuaciones.'),
(15, 3, 1795532157385, 'TAREA_CREADA', 'CAS', 1, 4, 'Agenda independiente de actuaciones.'),
(16, 3, 1795532157391, 'TASK_COMPLETE', 'CAS', 1, 4, 'Tarea 1. Llamada realizada'),
(17, 3, 1795532157397, 'TASK_CANCEL', 'CAS', 1, 4, 'Tarea 2. Ya no corresponde'),
(18, 3, 1795532157408, 'CASE_REQUEST_CLOSE', 'CAS', 1, 4, 'Gestion concluida'),
(19, 3, 1795532157416, 'CASE_WITHDRAW_CLOSE', 'CAS', 1, 4, 'Falta un antecedente'),
(20, 3, 1795532157424, 'CASE_REQUEST_CLOSE', 'CAS', 1, 4, 'Gestion concluida'),
(21, 4, 1795532157432, 'CASE_RETURN', 'CAS', 1, 4, 'Falta informar al requirente'),
(22, 4, 1795532157443, 'CASE_ASSIGN', 'CAS', 1, 4, 'Continua la coordinacion'),
(23, 4, 1795532157449, 'COLABORACION_SOLICITADA', 'FLUJO', 1, 4, 'Colaboracion sintetica'),
(24, 7, 1795532157466, 'CASO_CREADO', 'CAS', 2, 8, 'Apoyo sintetico'),
(25, 7, 1795532157467, 'COLABORACION_ACEPTADA', 'FLUJO', 1, 2, 'Aceptada'),
(26, 8, 1795532157478, 'CASO_CERRADO_PROFESIONAL_BAJA', 'CAS', 2, 8, 'Apoyo entregado'),
(27, 4, 1795532157486, 'CASO_CERRADO_JEFATURA_PROPIA', 'CAS', 1, 4, 'Cierre confirmado'),
(28, 4, 1795532157501, 'REQ_CIERRE_EXPLICITO', 'REQ', 1, 4, 'Motivo de cierre sintetico'),
(29, 4, 1795532157507, 'REAPERTURA_SOLICITADA', 'REAPERTURA', 1, 4, 'Nuevos antecedentes'),
(30, 4, 1795532157514, 'REQ_REACTIVADO', 'REQ', 1, 4, 'Procede'),
(31, 4, 1795532157515, 'REAPERTURA_ACEPTADA', 'REAPERTURA', 1, 4, 'Procede'),
(32, 4, 1795532157520, 'REAPERTURA_SOLICITADA', 'REAPERTURA', 2, 4, 'Reabrir el Caso'),
(33, 4, 1795532157528, 'CASO_REABIERTO', 'CAS', 1, 4, 'Procede'),
(34, 4, 1795532157529, 'REAPERTURA_ACEPTADA', 'REAPERTURA', 2, 4, 'Procede'),
(35, 4, 1795532157536, 'COM_REGISTRADA', 'COM', 2, 4, 'Canal TELEFONO'),
(36, 4, 1795532157546, 'REQ_CREADO', 'REQ', 2, 4, 'COM 2'),
(37, 4, 1795532157547, 'ASUNCION_INGRESO_PROPIO', 'SOL', 2, 4, 'Ingreso propio'),
(38, 2, 1795532157558, 'COM_REGISTRADA', 'COM', 3, 4, 'Canal TELEFONO'),
(39, 1, 1795532157566, 'SOL_DISTRIBUIDA', 'SOL', 3, 4, 'Clasificacion sintetica'),
(40, 4, 1795532157574, 'SOL_DUPLICADA', 'SOL', 3, 4, 'Ya ingresada'),
(41, 2, 1795532157580, 'COM_REGISTRADA', 'COM', 4, 4, 'Canal TELEFONO'),
(42, 2, 1795532157581, 'REQ_CREADO', 'REQ', 3, 4, 'COM 4'),
(43, 2, 1795532157584, 'PRIMERA_ATENCION_RESUELTA', 'REQ', 3, 4, 'Orientacion entregada'),
(44, 2, 1795532157602, 'COM_REGISTRADA_ATENCION', 'COM', 5, 4, 'Atencion directa. REQ 4'),
(45, 2, 1795532157602, 'ATENCION_INICIADA', 'REQ', 4, 4, 'COM 5. Sin SOL ni Caso automatico.'),
(46, 2, 1795532157610, 'ATENCION_ENTREGADA', 'REQ', 4, 4, 'Destino 4. Continuar la atencion'),
(47, 3, 1795532157617, 'ATENCION_RECIBIDA', 'REQ', 4, 4, 'Entrega 1. Recibo la atencion'),
(48, 3, 1795532157626, 'ATENCION_RESUELTA', 'REQ', 4, 4, 'ORIENTACION. Resultado registrado por quien atendio.'),
(49, 2, 1795532157660, 'COM_ORIGINAL_CONSERVADO', 'COM', 6, 4, 'Origen FORMULARIO_PUBLICO_DDN d76662e1-7441-4b6b-a731-e2bfb1d3c0bf');
INSERT INTO "public"."audit_event" VALUES
(50, 2, 1795532157674, 'COM_ORIGINAL_CONSERVADO', 'COM', 7, 4, 'Origen FORMULARIO_PUBLICO_DDN 66666666-6666-4666-8666-666666666670'),
(51, 2, 1795532157689, 'COM_ORIGINAL_CONSERVADO', 'COM', 8, 4, 'Origen FORMULARIO_PUBLICO_DDN 66666666-6666-4666-8666-666666666671'),
(52, 2, 1795532157699, 'COM_REGISTRADA_ATENCION', 'COM', 9, 4, 'Atencion directa. REQ 5'),
(53, 2, 1795532157699, 'ATENCION_INICIADA', 'REQ', 5, 4, 'COM 9. Sin SOL ni Caso automatico.'),
(54, 2, 1795532157709, 'FICHA_COMPLEMENTADA', 'REQ', 5, 4, 'Version 1. Complementacion sintetica'),
(55, 2, 1795532157718, 'COM_REGISTRADA_ATENCION', 'COM', 10, 4, 'Atencion directa. REQ 6'),
(56, 2, 1795532157718, 'ATENCION_INICIADA', 'REQ', 6, 4, 'COM 10. Sin SOL ni Caso automatico.'),
(57, 1, 1795532157725, 'COM_REGISTRADA', 'COM', 11, 4, 'Canal TELEFONO'),
(58, 1, 1795532157732, 'SOL_DISTRIBUIDA', 'SOL', 4, 1, 'Competencia UPR'),
(59, 5, 1795532157741, 'REQ_CREADO', 'REQ', 7, 1, 'COM 11'),
(60, 5, 1795532157741, 'SOL_ASUMIDA', 'SOL', 4, 1, 'Asuncion sintetica'),
(61, 5, 1795532157747, 'TRANSFERENCIA_SOLICITADA', 'FLUJO', 2, 1, 'Corresponde a UPD'),
(62, 7, 1795532157755, 'TRANSFERENCIA_ACEPTADA', 'FLUJO', 2, 2, 'Aceptada'),
(63, 7, 1795532157763, 'REQ_CIERRE_EXPLICITO', 'REQ', 7, 2, 'Cierre sintetico tras transferencia'),
(64, 1, 1795532157770, 'COM_REGISTRADA', 'COM', 12, 4, 'Canal TELEFONO'),
(65, 1, 1795532157777, 'SOL_DISTRIBUIDA', 'SOL', 5, 1, 'Competencia UPR'),
(66, 5, 1795532157793, 'REQ_CREADO', 'REQ', 8, 1, 'COM 12'),
(67, 5, 1795532157793, 'SOL_ASUMIDA', 'SOL', 5, 1, 'Asuncion sintetica'),
(68, 5, 1795532157799, 'REQ_COMPLEJIDAD', 'REQ', 8, 1, ' -> MEDIANA. Clasificacion sintetica'),
(69, 5, 1795532157808, 'CASO_CREADO', 'CAS', 3, 7, 'Seguimiento sintetico'),
(70, 5, 1795532157817, 'DERIVACION_SOLICITADA', 'FLUJO', 3, 7, 'Competencia de otra unidad'),
(71, 7, 1795532157829, 'CASO_CREADO', 'CAS', 4, 8, 'Destino sintetico'),
(72, 7, 1795532157831, 'CASO_CERRADO_DERIVACION', 'CAS', 3, 7, 'Aceptada; sucesor 4'),
(73, 7, 1795532157835, 'DERIVACION_ACEPTADA', 'FLUJO', 3, 2, 'Aceptada'),
(74, 1, 1795532157845, 'COM_REGISTRADA', 'COM', 13, 4, 'Canal TELEFONO'),
(75, 1, 1795532157855, 'COM_HIJA_PROPUESTA', 'COM', 13, 4, 'REQ 1. Posible relacion'),
(76, 4, 1795532157862, 'ANTECEDENTE_ADMITIDA', 'REQ', 1, 4, 'COM 13. Mismo asunto'),
(77, 1, 1795532157871, 'COM_REGISTRADA', 'COM', 14, 4, 'Canal TELEFONO'),
(78, 1, 1795532157879, 'COM_HIJA_PROPUESTA', 'COM', 14, 4, 'REQ 1. Posible relacion'),
(79, 4, 1795532157886, 'ANTECEDENTE_RECHAZADA', 'REQ', 1, 4, 'COM 14. Asunto distinto'),
(80, 4, 1795532157894, 'ANTECEDENTE_DEVUELTO', 'REQ', 1, 4, 'COM 14. Reclasificar'),
(81, 4, 1795532157894, 'COM_DEVUELTA_CLASIFICACION', 'COM', 14, 4, 'Vinculo 2 rechazado. Reclasificar'),
(82, 1, 1795532157906, 'COM_REGISTRADA', 'COM', 15, 4, 'Canal TELEFONO'),
(83, 1, 1795532157919, 'SOL_DISTRIBUIDA', 'SOL', 6, 4, 'Clasificacion sintetica'),
(84, 4, 1795532157928, 'SOL_INVALIDA', 'SOL', 6, 4, 'No corresponde'),
(85, 1, 1795532157935, 'COM_REGISTRADA', 'COM', 16, 4, 'Canal TELEFONO'),
(86, 1, 1795532157943, 'SOL_DISTRIBUIDA', 'SOL', 7, 2, 'Competencia UPD'),
(87, 2, 1795532157953, 'COM_REGISTRADA', 'COM', 17, 4, 'Canal TELEFONO'),
(88, 16, 1795532157962, 'AMBITO_ACTUALIZADO', 'AMBITO', 14, 14, 'Region: 05 -> 06. Efecto territorial: 05 sin atencion. Acto sintetico: la region 05 queda sin atencion'),
(89, 16, 1795532157968, 'COBERTURA_ACTUALIZADA', 'AMBITO', 14, 14, 'Cobertura adicional: ninguna -> 07. Efecto territorial: 06 sigue atendida por REG-DEMO. Acto sintetico'),
(90, 16, 1795532157976, 'COBERTURA_NIVEL_CENTRAL_ACTUALIZADA', 'AMBITO', 0, NULL, 'Cobertura del nivel central: ninguna -> 13. Acto sintetico'),
(91, 16, 1795532157982, 'AMBITO_CREADO', 'AMBITO', 15, 15, 'Sede PROYECTADA, region 05. Acto sintetico'),
(92, 16, 1795805923415, 'CATALOGO_IMPORTADO', 'CATALOGO_ESTADISTICO', 1, NULL, U&'TIPO_SOLICITUD versi\00f3n 1 propuesta: 5 \00edtems, huella 92639c46c37b. Volcado sintetico'),
(93, 16, 1795805923426, 'CATALOGO_ACTIVADO', 'CATALOGO_ESTADISTICO', 1, NULL, U&'TIPO_SOLICITUD versi\00f3n 1 vigente. Volcado sintetico');
INSERT INTO "public"."audit_event" VALUES
(94, 16, 1795805923436, 'CATALOGO_IMPORTADO', 'CATALOGO_ESTADISTICO', 2, NULL, U&'TIPO_REQUIRENTE versi\00f3n 2 propuesta: 4 \00edtems, huella af9895fa20c4. Volcado sintetico'),
(95, 16, 1795805923442, 'CATALOGO_ACTIVADO', 'CATALOGO_ESTADISTICO', 2, NULL, U&'TIPO_REQUIRENTE versi\00f3n 2 vigente. Volcado sintetico'),
(96, 16, 1795805923455, 'CATALOGO_IMPORTADO', 'CATALOGO_ESTADISTICO', 3, NULL, U&'VIA_INGRESO versi\00f3n 3 propuesta: 15 \00edtems, huella ad910b8946d7. Volcado sintetico'),
(97, 16, 1795805923461, 'CATALOGO_ACTIVADO', 'CATALOGO_ESTADISTICO', 3, NULL, U&'VIA_INGRESO versi\00f3n 3 vigente. Volcado sintetico'),
(98, 16, 1795805923492, 'CATALOGO_IMPORTADO', 'CATALOGO_ESTADISTICO', 4, NULL, U&'DERECHO versi\00f3n 4 propuesta: 81 \00edtems, huella d645dfe3c5d3. Volcado sintetico'),
(99, 16, 1795805923498, 'CATALOGO_ACTIVADO', 'CATALOGO_ESTADISTICO', 4, NULL, U&'DERECHO versi\00f3n 4 vigente. Volcado sintetico'),
(100, 16, 1795805923508, 'CATALOGO_IMPORTADO', 'CATALOGO_ESTADISTICO', 5, NULL, U&'CONTEXTO versi\00f3n 5 propuesta: 12 \00edtems, huella a5d08c9fcc76. Volcado sintetico'),
(101, 16, 1795805923514, 'CATALOGO_ACTIVADO', 'CATALOGO_ESTADISTICO', 5, NULL, U&'CONTEXTO versi\00f3n 5 vigente. Volcado sintetico'),
(102, 16, 1795805923527, 'CATALOGO_IMPORTADO', 'CATALOGO_ESTADISTICO', 6, NULL, U&'TIPO_ACTUACION versi\00f3n 6 propuesta: 14 \00edtems, huella 72bfcbd3fd74. Volcado sintetico'),
(103, 16, 1795805923533, 'CATALOGO_ACTIVADO', 'CATALOGO_ESTADISTICO', 6, NULL, U&'TIPO_ACTUACION versi\00f3n 6 vigente. Volcado sintetico'),
(104, 16, 1795805923542, 'CATALOGO_IMPORTADO', 'CATALOGO_ESTADISTICO', 7, NULL, U&'RELACION versi\00f3n 7 propuesta: 11 \00edtems, huella 9d043acbe5e6. Volcado sintetico'),
(105, 16, 1795805923548, 'CATALOGO_ACTIVADO', 'CATALOGO_ESTADISTICO', 7, NULL, U&'RELACION versi\00f3n 7 vigente. Volcado sintetico'),
(106, 16, 1795805923554, 'CATALOGO_IMPORTADO', 'CATALOGO_ESTADISTICO', 8, NULL, U&'TIPO_INSTITUCION versi\00f3n 8 propuesta: 5 \00edtems, huella ba3eacb3ead8. Volcado sintetico'),
(107, 16, 1795805923560, 'CATALOGO_ACTIVADO', 'CATALOGO_ESTADISTICO', 8, NULL, U&'TIPO_INSTITUCION versi\00f3n 8 vigente. Volcado sintetico'),
(108, 2, 1795805923599, 'COM_ORIGINAL_CONSERVADO', 'COM', 18, 4, 'Origen FORMULARIO_PUBLICO_DDN 3433d3bb-3619-486d-9ae2-b952b71ee3c9'),
(109, 1, 1795805923613, 'CLASIFICACION_REGISTRADA', 'COM', 18, 4, 'Variables: TIPO_SOLICITUD, TIPO_REQUIRENTE.'),
(110, 1, 1795805923614, 'SOL_DISTRIBUIDA', 'SOL', 8, 4, 'Distribucion sintetica'),
(111, 4, 1795805923630, 'REQ_CREADO', 'REQ', 9, 4, 'COM 18'),
(112, 4, 1795805923630, 'SOL_ASUMIDA', 'SOL', 8, 4, 'Asume'),
(113, 4, 1795805923639, 'REQ_COMPLEJIDAD', 'REQ', 9, 4, ' -> MEDIANA. Clasificacion sintetica'),
(114, 4, 1795805923651, 'CASO_CREADO', 'CAS', 5, 4, 'Gestion sintetica'),
(115, 4, 1795805923664, 'CLASIFICACION_REGISTRADA', 'REQ', 9, 4, 'Variables: DERECHO, CONTEXTO.'),
(116, 3, 1795805923672, 'ACTUACION_REGISTRADA', 'CAS', 5, 4, 'Constancia 3'),
(117, 4, 1795805923686, 'COM_REGISTRADA', 'COM', 19, 4, 'Canal CORREO'),
(118, 1, 1795805923698, 'CLASIFICACION_REGISTRADA', 'COM', 19, 4, 'Variables: TIPO_SOLICITUD, TIPO_REQUIRENTE.'),
(119, 1, 1795805923699, 'SOL_DISTRIBUIDA', 'SOL', 9, 4, 'Distribucion sintetica'),
(120, 4, 1795805923710, 'REQ_CREADO', 'REQ', 10, 4, 'COM 19'),
(121, 4, 1795805923710, 'SOL_ASUMIDA', 'SOL', 9, 4, 'Asume'),
(122, 4, 1795805923716, 'REQ_COMPLEJIDAD', 'REQ', 10, 4, ' -> BAJA. Clasificacion sintetica'),
(123, 4, 1795805923725, 'CASO_CREADO', 'CAS', 6, 4, 'Gestion sintetica'),
(124, 4, 1795805923734, 'COM_REGISTRADA', 'COM', 20, 4, 'Canal CORREO'),
(125, 1, 1795805923745, 'CLASIFICACION_REGISTRADA', 'COM', 20, 4, 'Variables: TIPO_SOLICITUD, TIPO_REQUIRENTE.'),
(126, 1, 1795805923745, 'SOL_DISTRIBUIDA', 'SOL', 10, 4, 'Distribucion sintetica'),
(127, 4, 1795805923755, 'REQ_CREADO', 'REQ', 11, 4, 'COM 20'),
(128, 4, 1795805923756, 'SOL_ASUMIDA', 'SOL', 10, 4, 'Asume'),
(129, 4, 1795805923762, 'REQ_COMPLEJIDAD', 'REQ', 11, 4, ' -> BAJA. Clasificacion sintetica'),
(130, 4, 1795805923771, 'CASO_CREADO', 'CAS', 7, 4, 'Gestion sintetica');
INSERT INTO "public"."audit_event" VALUES
(131, 4, 1795805923778, 'CLASIFICACION_REGISTRADA', 'REQ', 11, 4, 'Variables: DERECHO, CONTEXTO.'),
(132, 3, 1795805923783, 'ACTUACION_REGISTRADA', 'CAS', 7, 4, 'Constancia 4'),
(133, 3, 1795805923792, 'CASO_CERRADO_PROFESIONAL_BAJA', 'CAS', 7, 4, 'Cierre sintetico'),
(134, 4, 1795805923803, 'COM_REGISTRADA', 'COM', 21, 4, 'Canal CORREO'),
(135, 1, 1795805923812, 'COM_HIJA_PROPUESTA', 'COM', 21, 4, 'REQ 9. Antecedente sintetico'),
(136, 4, 1795805923820, 'ANTECEDENTE_ADMITIDA', 'REQ', 9, 4, 'COM 21. Antecedente del Caso'),
(137, 19, 1795805923907, 'CORTE_GENERADO', 'SISTEMA', 0, NULL, 'Corte CORTE-2026-001; alcance institucional; periodo PERSONALIZADO');
CREATE INDEX "public"."ix_audit_resource" ON "public"."audit_event"("resource_type" NULLS FIRST, "resource_id" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."inbox_notice"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 75) DEFAULT ON NULL NOT NULL,
    "user_id" BIGINT NOT NULL,
    "at_ms" BIGINT NOT NULL,
    "text_value" CHARACTER VARYING(300) NOT NULL,
    "resource_type" CHARACTER VARYING(24) NOT NULL,
    "resource_id" BIGINT NOT NULL,
    "read_ms" BIGINT
);
ALTER TABLE "public"."inbox_notice" ADD CONSTRAINT "public"."CONSTRAINT_4EF" PRIMARY KEY("id");
-- 74 +/- SELECT COUNT(*) FROM public.inbox_notice;
INSERT INTO "public"."inbox_notice" VALUES
(1, 1, 1795532157278, 'Hay un ingreso pendiente de clasificacion.', 'COM', 1, NULL),
(2, 4, 1795532157291, 'Tienes una SOL pendiente de asumir.', 'SOL', 1, NULL),
(3, 7, 1795532157302, 'Una SOL fue remitida a tu ambito.', 'SOL', 1, NULL),
(4, 1, 1795532157311, 'Una SOL fue devuelta a clasificacion.', 'COM', 1, NULL),
(5, 4, 1795532157320, 'Una SOL fue remitida a tu ambito.', 'SOL', 1, NULL),
(6, 3, 1795532157331, 'Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.', 'REQ', 1, NULL),
(7, 3, 1795532157349, 'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.', 'CAS', 1, NULL),
(8, 3, 1795532157377, 'Tienes una tarea asignada en un Caso.', 'CAS', 1, NULL),
(9, 3, 1795532157385, 'Tienes una tarea asignada en un Caso.', 'CAS', 1, NULL),
(10, 4, 1795532157407, 'Un Caso requiere revision de termino.', 'CAS', 1, NULL),
(11, 4, 1795532157415, 'El responsable retiro su solicitud de cierre; el Caso vuelve a gestion.', 'CAS', 1, NULL),
(12, 4, 1795532157424, 'Un Caso requiere revision de termino.', 'CAS', 1, NULL),
(13, 3, 1795532157432, 'La jefatura devolvio el termino con observaciones.', 'CAS', 1, NULL),
(14, 4, 1795532157441, 'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.', 'CAS', 1, NULL),
(15, 3, 1795532157442, 'Cambio tu asignacion; se conserva tu autoria historica.', 'CAS', 1, NULL),
(16, 7, 1795532157451, 'Tienes una peticion de colaboracion.', 'FLUJO', 1, NULL),
(17, 8, 1795532157466, 'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.', 'CAS', 2, NULL),
(18, 4, 1795532157469, 'Se resolvio una peticion de colaboracion.', 'FLUJO', 1, NULL),
(19, 8, 1795532157478, 'El Caso fue cerrado. El cierre del REQ es una decision separada.', 'CAS', 2, NULL),
(20, 4, 1795532157479, 'Cambio el estado de un Caso del REQ.', 'REQ', 1, NULL),
(21, 4, 1795532157487, 'El Caso fue cerrado. El cierre del REQ es una decision separada.', 'CAS', 1, NULL),
(22, 4, 1795532157489, 'Cambio el estado de un Caso del REQ.', 'REQ', 1, NULL),
(23, 4, 1795532157501, 'Un REQ fue cerrado expresamente por su jefatura principal.', 'REQ', 1, NULL),
(24, 4, 1795532157509, 'Hay una solicitud de reapertura.', 'REQ', 1, NULL),
(25, 4, 1795532157522, 'Hay una solicitud de reapertura.', 'REQ', 1, NULL),
(26, 1, 1795532157538, 'Hay un ingreso pendiente de clasificacion.', 'COM', 2, NULL),
(27, 3, 1795532157547, 'Se asigno un REQ por asuncion directa de jefatura.', 'REQ', 2, NULL),
(28, 1, 1795532157559, 'Hay un ingreso pendiente de clasificacion.', 'COM', 3, NULL),
(29, 4, 1795532157568, 'Tienes una SOL pendiente de asumir.', 'SOL', 3, NULL),
(30, 3, 1795532157610, 'Tienes una primera atencion para recibir y continuar.', 'REQ', 4, NULL),
(31, 2, 1795532157617, 'Tu entrega de primera atencion fue recibida.', 'REQ', 4, NULL),
(32, 4, 1795532157619, 'Se registro continuidad de primera atencion; el principal del REQ no cambia.', 'REQ', 4, NULL),
(33, 4, 1795532157628, 'Una primera atencion se resolvio sin Caso.', 'REQ', 4, NULL),
(34, 1, 1795532157726, 'Hay un ingreso pendiente de clasificacion.', 'COM', 11, NULL),
(35, 5, 1795532157734, 'Tienes una SOL pendiente de asumir.', 'SOL', 4, NULL),
(36, 6, 1795532157741, 'Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.', 'REQ', 7, NULL),
(37, 7, 1795532157749, 'Tienes una peticion de transferencia.', 'FLUJO', 2, NULL),
(38, 5, 1795532157756, 'Se resolvio una peticion de transferencia.', 'FLUJO', 2, NULL),
(39, 5, 1795532157764, 'Un REQ fue cerrado expresamente por su jefatura principal.', 'REQ', 7, NULL),
(40, 1, 1795532157771, 'Hay un ingreso pendiente de clasificacion.', 'COM', 12, NULL),
(41, 5, 1795532157779, 'Tienes una SOL pendiente de asumir.', 'SOL', 5, NULL),
(42, 6, 1795532157793, 'Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.', 'REQ', 8, NULL),
(43, 6, 1795532157808, 'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.', 'CAS', 3, NULL),
(44, 7, 1795532157818, 'Tienes una peticion de derivacion.', 'FLUJO', 3, NULL);
INSERT INTO "public"."inbox_notice" VALUES
(45, 8, 1795532157829, 'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.', 'CAS', 4, NULL),
(46, 6, 1795532157831, 'El Caso fue cerrado. El cierre del REQ es una decision separada.', 'CAS', 3, NULL),
(47, 5, 1795532157833, 'Cambio el estado de un Caso del REQ.', 'REQ', 8, NULL),
(48, 5, 1795532157837, 'Se resolvio una peticion de derivacion.', 'FLUJO', 3, NULL),
(49, 1, 1795532157847, 'Hay un ingreso pendiente de clasificacion.', 'COM', 13, NULL),
(50, 4, 1795532157856, 'Una COM fue propuesta como antecedente de tu REQ.', 'REQ', 1, NULL),
(51, 1, 1795532157873, 'Hay un ingreso pendiente de clasificacion.', 'COM', 14, NULL),
(52, 4, 1795532157880, 'Una COM fue propuesta como antecedente de tu REQ.', 'REQ', 1, NULL),
(53, 1, 1795532157896, 'Una COM rechazada como antecedente requiere nueva clasificacion.', 'COM', 14, NULL),
(54, 1, 1795532157908, 'Hay un ingreso pendiente de clasificacion.', 'COM', 15, NULL),
(55, 4, 1795532157922, 'Tienes una SOL pendiente de asumir.', 'SOL', 6, NULL),
(56, 1, 1795532157937, 'Hay un ingreso pendiente de clasificacion.', 'COM', 16, NULL),
(57, 7, 1795532157946, 'Tienes una SOL pendiente de asumir.', 'SOL', 7, NULL),
(58, 1, 1795532157955, 'Hay un ingreso pendiente de clasificacion.', 'COM', 17, NULL),
(59, 4, 1795805923619, 'Tienes una SOL pendiente de asumir.', 'SOL', 8, NULL),
(60, 3, 1795805923631, 'Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.', 'REQ', 9, NULL),
(61, 3, 1795805923651, 'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.', 'CAS', 5, NULL),
(62, 1, 1795805923689, 'Hay un ingreso pendiente de clasificacion.', 'COM', 19, NULL),
(63, 4, 1795805923701, 'Tienes una SOL pendiente de asumir.', 'SOL', 9, NULL),
(64, 3, 1795805923711, 'Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.', 'REQ', 10, NULL),
(65, 3, 1795805923725, 'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.', 'CAS', 6, NULL),
(66, 1, 1795805923736, 'Hay un ingreso pendiente de clasificacion.', 'COM', 20, NULL),
(67, 4, 1795805923747, 'Tienes una SOL pendiente de asumir.', 'SOL', 10, NULL),
(68, 3, 1795805923756, 'Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.', 'REQ', 11, NULL),
(69, 3, 1795805923771, 'Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.', 'CAS', 7, NULL),
(70, 3, 1795805923792, 'El Caso fue cerrado. El cierre del REQ es una decision separada.', 'CAS', 7, NULL),
(71, 4, 1795805923794, 'Cambio el estado de un Caso del REQ.', 'REQ', 11, NULL),
(72, 1, 1795805923805, 'Hay un ingreso pendiente de clasificacion.', 'COM', 21, NULL),
(73, 4, 1795805923813, 'Una COM fue propuesta como antecedente de tu REQ.', 'REQ', 9, NULL),
(74, 3, 1795805923821, 'Un antecedente fue habilitado para tu Caso; no implica reapertura.', 'CAS', 5, NULL);
CREATE INDEX "public"."ix_notice_unread" ON "public"."inbox_notice"("user_id" NULLS FIRST, "read_ms" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."command_log"(
    "command_key" CHARACTER VARYING(36) NOT NULL,
    "actor_id" BIGINT NOT NULL,
    "payload_hash" CHARACTER(64) NOT NULL,
    "result_kind" CHARACTER VARYING(24) NOT NULL,
    "result_id" BIGINT NOT NULL,
    "result_message" CHARACTER VARYING(300) NOT NULL,
    "created_ms" BIGINT NOT NULL
);
ALTER TABLE "public"."command_log" ADD CONSTRAINT "public"."CONSTRAINT_A8" PRIMARY KEY("command_key");
-- 112 +/- SELECT COUNT(*) FROM public.command_log;
INSERT INTO "public"."command_log" VALUES
('024f7feb-02b5-407d-bb93-203f262d5f96', 16, 'b25e72fb3ee145e9161c4ddefd5734a34671c39d78938a0cad7a79a8438647c5', 'CATALOGO_ESTADISTICO', 1, U&'Se import\00f3 la versi\00f3n 1 de Tipo de solicitud como propuesta (5 \00edtems).', 1795805923416),
('0279d0ee-e44d-498b-93b2-72818aa2960d', 4, 'b0e27e18e8d745827b37012d232e731d28edf4341b2fa1a5ef3a348fb3d145b4', 'REQ', 9, 'Operacion registrada.', 1795805923631),
('030ede8a-83f8-471f-b02c-d644b5f1e926', 4, '78c844d46b708c3d84728e9b82cbb0452e4ad4aa2cd9be1bb38b26a76d791ac9', 'REQ', 9, 'Operacion registrada.', 1795805923639),
('060a03fb-a220-447f-ac94-2f55c7a86542', 16, '64094120f4343eb00e3f1576c1d1858aea281d4cf7f6e2213992cb27d84f4e73', 'CATALOGO_ESTADISTICO', 4, U&'La versi\00f3n 4 de Derechos qued\00f3 vigente.', 1795805923499),
('07dea893-6de7-4c23-8dff-c234fbfee4d7', 4, '7883c5d5a30b8aa55f6062ddfd23d77ec584f3fa5abaf6f6c7096f10bb3b0406', 'REQ', 1, 'Operacion registrada.', 1795532157501),
('0d878fad-35b6-4d36-9f6f-d1266c2c9dd2', 16, '77c47fc062ea1106f74fb6096f2926259579d4150a1180b0ab1f4b82fbcf0875', 'CATALOGO_ESTADISTICO', 6, U&'La versi\00f3n 6 de Tipo de actuaci\00f3n qued\00f3 vigente.', 1795805923534),
('0fc93f82-0c5e-4273-a947-a5b7bfeefa97', 4, 'b807d54c5380fae51a588a16d343a21f98455079db4a7f4b96edaaf73ff9fb23', 'CAS', 1, 'Operacion registrada.', 1795532157363),
('10011a09-fa63-40c6-a044-67387020da41', 3, 'fe5663d04e71c85cbe1e4837c5c026cfa234f2cd3e8174fc7d2025a12e1969fb', 'CAS', 1, 'Operacion registrada.', 1795532157409),
('10464cdd-c782-47d6-9999-cc0c3f5de336', 5, 'c5ce5a816a2602bd1f0872269fae840f9eb21cab88dd53c703682e88460d3dde', 'CAS', 3, 'Operacion registrada.', 1795532157808),
('12a60334-14bd-460b-b54d-0c9ba525a8fe', 1, '1387b67dc8e8cd0b6a5009f64359bbb8e623adc37088a4ccca46c2591028731b', 'SOL', 4, 'Operacion registrada.', 1795532157734),
('148026f2-5bf2-40fc-94af-4f5e720e80ed', 4, '451acdec3a908e14804c777643b06e7795c31133d96b7740a47cbbfd53791275', 'REQ', 11, 'Operacion registrada.', 1795805923757),
('1870b404-7b64-4b63-9993-c6d381e656d2', 4, '4a2a9d59f60ddc876f28ad8192a99e98afd006126454bdef63940ad0d81e280e', 'REQ', 9, 'Operacion registrada.', 1795805923821),
('1ad56a5a-1ac1-4b2b-9a5f-0f16bd785d5a', 8, 'd6161f78a017170fad0f049148b9c912b5715c342f66be79643c3a40a41b0d42', 'CAS', 2, 'Operacion registrada.', 1795532157480),
('1d0e3410-beef-4e28-9737-4db1f56eb08a', 4, '593cd4c130e4b8923d841975accd9a594f37d966b76056ef137f4d96785d9c09', 'REQ', 2, 'Operacion registrada.', 1795532157548),
('2016a7a4-ffb8-4ce1-ba77-349b1629dbeb', 4, 'a1a9420e573ed4512e5b874fce25c2e27c3a7ba65b7f720dab2fd9235bce8d88', 'CAS', 5, 'Operacion registrada.', 1795805923651),
('23186cf6-5b02-4035-90b4-c9c912d4339e', 4, '3af77a80e243a587f51cf907cb8ec5460cd2d740a5c5e55797495a53cc8c0f0a', 'CAS', 1, 'Operacion registrada.', 1795532157443),
('23d73b10-46a0-4f3f-a5a1-ab74400c5ae6', 1, 'ffee8d1aa4d2c4d87af7dfac6be49c66c79cdab4211eeefe9af073ca43e8c34b', 'SOL', 6, 'Operacion registrada.', 1795532157922),
('269b9663-282a-45c1-8a80-bcd142f5ef8b', 1, 'cddeccb201329397c5d5100ac2da738ca16f7c089f65b030ee7bf4e235048177', 'COM', 13, 'Operacion registrada.', 1795532157857),
('2af4adb8-4d54-4b15-9a68-63c1fffd1f0d', 16, 'b070372ab9c0eee454267a43701a5317235749869897803d70cd7233f5443826', 'CATALOGO_ESTADISTICO', 3, U&'Se import\00f3 la versi\00f3n 3 de V\00eda de ingreso como propuesta (15 \00edtems).', 1795805923455),
('2b2a4f1f-97e8-482f-adf6-b4c499293d3f', 1, '24281579e1f71e513595ad298adf25e5a64109bd0cd87d21f99c221b3f833091', 'COM', 11, 'Operacion registrada.', 1795532157726),
('2daaa284-4e58-4983-9610-27e9eaf73f7e', 2, '3839e68d845e8d530727f9b8de7d5b6f5cae1f85bcb992f5abff2bffcce3dbb4', 'REQ', 5, 'Ficha guardada con historial. El ingreso original permanece intacto.', 1795532157709),
('2fa58216-2b95-4423-8300-32bc6d5783fa', 4, 'afce3d892ea4b4e2254706b3ba9ed09efd37a6192cc32747e423d28c93988d57', 'REQ', 11, 'Operacion registrada.', 1795805923778),
('30e7e106-ac60-4f02-bc16-2ecd9384d93d', 1, 'c21f8a8ea509be5e411f31856e4a3c20dcedad8da47833331412fca7ed392ba8', 'SOL', 3, 'Operacion registrada.', 1795532157568);
INSERT INTO "public"."command_log" VALUES
('3261fe0a-cf54-4654-8066-1949a142f921', 1, 'a3d1c3d2f426f71ef652642e1bdb736be46e41577855581c318513938b62804e', 'SOL', 5, 'Operacion registrada.', 1795532157779),
('32fabe12-7b08-4bbd-a5f1-bc397fc39d3e', 4, '3e69d38d911bd0ab74ed271dbd721fbf933b44ae69194c6ac1a67a12e450492b', 'REQ', 1, 'Operacion registrada.', 1795532157897),
('36a944d9-2839-4172-a26d-1959b165fc5b', 4, 'bb9c8d1a9e47fd811d533219b44ac721cdc2c6c4a6d6efd2aeda014f7aeb5da2', 'REQ', 11, 'Operacion registrada.', 1795805923762),
('3c1c9ddf-e5bc-454c-be4b-d0a619408048', 4, '884ebd3506518422846a28b4beece3544268c86c3426c7dfd2204ea09f344dca', 'CAS', 1, 'Operacion registrada.', 1795532157489),
('3cd20a45-0f1c-415e-8cc9-0d02b2703dea', 1, 'c7022113a48685d94611a45922507b0a883aaf7bc755a1355ffb7b8ffc55a5fe', 'SOL', 10, 'Operacion registrada.', 1795805923747),
('3d64972e-af0d-4cba-abb1-eceee106c89a', 16, '25ae6f181506da48707dc3211077d416848fe7f8f7d2447930a841d8b38691be', 'AMBITO', 14, 'Operacion registrada.', 1795532157962),
('3daa2f04-1cfd-40d3-8d03-4eecc4acbac2', 4, '253a5f3d936cab2abad58995a50e6b6991309886506ee7cbe6c85091e0149db9', 'CAS', 1, 'Operacion registrada.', 1795532157349),
('3f863650-b575-4cb0-b801-12ff10a54f86', 1, '322126d697774ced228d1aa9368efc3d08b3beb22d0d567b441ed03076337861', 'COM', 13, 'Operacion registrada.', 1795532157847),
('40377bef-f470-43b1-96cd-3fa06d730137', 20, '0ea25c0f5c246692f304b1bb7794cce067939f44ceb3ffc01e5e478508d8fbbd', 'VISTA', 1, 'Operacion registrada.', 1795805923914),
('415a8fac-28a1-4bc9-87d6-5f49fb5d389c', 3, '22db2a769df2c16de087d5b088f10b539c0dab9419c52019857654deff6c22fb', 'CAS', 1, 'Operacion registrada.', 1795532157356),
('41b68b4d-9c89-43dc-bd4a-dd2b1bb774ab', 4, '6ab1a079df50746a542ada180e23d3ccbf16e5d1c4bbd4b1171fd5a39767ca0a', 'SOL', 1, 'Operacion registrada.', 1795532157303),
('4a9a8833-d42f-4ea7-8720-dcc641e38677', 16, '7b2e10289f64aae02eeb064199d78e424bb8b7fb5e854fa377acf1eed2f86cbd', 'CATALOGO_ESTADISTICO', 5, U&'La versi\00f3n 5 de Contexto qued\00f3 vigente.', 1795805923514),
('4ae3c6bd-1101-4b53-873a-22e66a44ec26', 1, '47d223f17c79f6f665dcb19d47295c12bba846c5259ac03c8487aceebf985ab1', 'COM', 15, 'Operacion registrada.', 1795532157909),
('4d4ab18d-33ff-4ffc-9f66-f15e272c2ca5', 2, '0d7cbd7b58d1c0d813b95c9ba2c3a15b5a19fcfc9e3905c854c471ac63998ca4', 'REQ', 4, 'Entrega enviada. Conservas el encargo hasta su recepcion; no se cambia la jefatura principal.', 1795532157610),
('5a2ad789-93fe-44da-944f-d7c4c23c7d1f', 2, 'f34b74126f3f06c62df2583557f80f76e4860e553cfffa317a08c3f3844adc7d', 'COM', 17, 'Operacion registrada.', 1795532157955),
('5eb3dfd7-78b8-4452-9df2-44c3acba3acf', 16, '4fba57af2614f7296eb4c902860dde6dbfa97b18a9614194f2bb765a6661594d', 'CATALOGO_ESTADISTICO', 7, U&'Se import\00f3 la versi\00f3n 7 de Relaci\00f3n con el NNA como propuesta (11 \00edtems).', 1795805923542),
('60851e4e-bf0e-47d2-95eb-c4f27b7108f3', 4, '8ae307778b6a5f1bdf310107b972bdd00da289c65a8137710d0119c318f5cc49', 'REQ', 1, 'Operacion registrada.', 1795532157522),
('630a9be5-b5f4-4060-8888-37f3f31fdf03', 4, 'bd096e00f1ba5ac2ae721a0b018fc9e9a40bd33e82109becbdaeba3b02e5b8bf', 'CAS', 7, 'Operacion registrada.', 1795805923771),
('66fddc61-5000-4595-91c4-9302a16e2316', 5, '4896dd1222d7084ae5c135e7baccc361dcaeac8af5366f50828e693d755c7002', 'REQ', 8, 'Operacion registrada.', 1795532157819),
('68587abc-16d5-4625-9412-69706f7ffeb7', 4, '184263e92c5732aed2e42c14283f4a416af1670267484f7f014e763da5c94153', 'REQ', 1, 'Operacion registrada.', 1795532157887),
('68e55408-0a06-4593-ab7f-e747c6747fa7', 4, '3bddcd9fe1f76eeee30179114ec0e0f2f227b71963a72a1e77ae5615d1796fdd', 'REQ', 1, 'Operacion registrada.', 1795532157331),
('699942a5-be97-43d1-ac12-6bc313479590', 4, '512b7ac7ffda1f8068d917667b89d9ed49eb1ce7146f1eadc053dd7dc4343ff9', 'REQ', 1, 'Operacion registrada.', 1795532157452),
('6b9e42f9-7552-41ab-acbe-2853a2fa1f67', 2, 'fe9e7ad6c89e74d98f8af28a825fa93d5e071d3eb9bda33a5090d3e5e185b67c', 'COM', 1, 'Operacion registrada.', 1795532157278),
('6c64ecf0-4b85-462b-8946-4d48dface85e', 1, '5beba9b7916fe5ba4e9d176f935d56a50952689f20a52df3b6b1722a1ae28d28', 'COM', 16, 'Operacion registrada.', 1795532157937);
INSERT INTO "public"."command_log" VALUES
('6d19e9aa-adca-46f8-b660-3d5874423132', 16, 'd8862ee218f11457156687ca2121ef3a6401e04875be844562ef7550be39060d', 'AMBITO', 0, 'Operacion registrada.', 1795532157977),
('6da6af2e-5a50-4ee2-9f38-f47a58ffe185', 7, '83d7b9fcf4d75996c120d80a775bdf018d575e2727702dab71244da7fae1b48e', 'SOL', 1, 'Operacion registrada.', 1795532157311),
('6fdc6eb8-45ec-442c-80ef-0b5f2b95f480', 16, '733c0c4d0e5e4419e0c9e592c3ad8807eefd6a015c07ebe86890d3aad7192fda', 'CATALOGO_ESTADISTICO', 6, U&'Se import\00f3 la versi\00f3n 6 de Tipo de actuaci\00f3n como propuesta (14 \00edtems).', 1795805923527),
('71cc9299-7f83-4f44-b809-1ef4d0544f33', 3, '10032544eb32bcad8231ee845bc2d51030f980cab4edc8e6e1a0d6894a203037', 'CAS', 1, 'Operacion registrada.', 1795532157417),
('72c5a74d-1e4d-4a74-b567-b9840115d72e', 4, '3c85a61372f04f7b93792d216a9cc02fe35f583adf940526cceff9ab49d407be', 'CAS', 1, 'Operacion registrada.', 1795532157433),
('7695957d-6a8c-487e-8e86-d1706d83ea1d', 7, 'd0a51d461caf4bea115678453a9080a4d248650d9cfb170c8ec6cbd72c90b8dc', 'FLUJO', 2, 'Operacion registrada.', 1795532157757),
('79770f9b-1a71-41c0-a385-9e6972984e53', 3, 'd9331a1e73380031772fb671a205690419df474ecfb97ee00036e176861a67e2', 'CAS', 7, 'Operacion registrada.', 1795805923794),
('7ace1f07-05a9-4f86-92cc-4ad90d920ff8', 4, 'ee1ebc9da532c154e83a5fe6542b935b5f1b7f3bb95b8b47fa7dd21c62981f7a', 'REQ', 10, 'Operacion registrada.', 1795805923711),
('7ad2b424-2b66-4492-b12d-63789fa8fded', 3, 'c70870dfd3f2c07c061f66b7888fc2e30c1cb973765106e70bdc1b2345da5425', 'CAS', 1, 'Operacion registrada.', 1795532157392),
('7b3b1b99-39bc-4712-856b-30c36f2c0b25', 16, '06561a26ebfccf60e1106148a87ba197d077ebe81217528310276b0212fa7200', 'CATALOGO_ESTADISTICO', 2, U&'La versi\00f3n 2 de Tipo de requirente qued\00f3 vigente.', 1795805923443),
('7d6ce668-297c-4aac-b84c-8f6add529170', 1, 'bf693f2d5ed95ce9b43dc32df20443cadc042d4020f4a600c413e82291ec079f', 'SOL', 9, 'Operacion registrada.', 1795805923701),
('7e47abbc-3377-4eb0-ad3a-acc6a2dd2806', 16, 'fab2620ea008debdfe12520f720728d7cc26d2bc3e330e85590eeaa04e8f51d1', 'AMBITO', 15, 'Operacion registrada.', 1795532157982),
('81f5dda2-f253-443e-8ba2-fb21654d1195', 1, '896ac0f4570bcfd058704d16dd59a6fd20647e8e2e9570a5fade05c98464b3e6', 'SOL', 8, 'Operacion registrada.', 1795805923620),
('8257f95f-06c5-4d7d-97d4-8a7a529db07d', 1, '670a3f5b4354b1ff7a40e8f06c9f0d72e37bf1112cd744a0f50752bf0364f450', 'COM', 12, 'Operacion registrada.', 1795532157772),
('87cf83be-7a42-48b6-9dc3-2d1a90fe43ce', 3, '5ff9385f72777b0227f5ddc44709a99c5a19f0c32c897b586fde5e3e0fecdd1b', 'CAS', 1, 'Operacion registrada.', 1795532157386),
('88fa4e35-3aba-4a3e-a098-851e92f28979', 1, '7fef0d2ac9b122105487986550e02ba7c073d78e8baad7531772cd4de907ae20', 'COM', 14, 'Operacion registrada.', 1795532157873),
('8ed102e2-8c10-4fb2-9aab-4d58cd7b085e', 4, '1d2f485df8c3ea3c05df2d112d8c960aeb545bca9af105647b6c6746a43435fe', 'REQ', 1, 'Operacion registrada.', 1795532157509),
('91c3e00b-495a-4ae9-b82e-1f647c8c0b6d', 7, '8045e29a38ac4633d0ecb8bf3a1c92591bd9e85fc8a2a052d33f882e1ba89e50', 'FLUJO', 1, 'Operacion registrada.', 1795532157470),
('92a7756b-3875-4226-857c-ac316ac47e96', 4, '3e9815112788f11315575dbf888f3d4c37bb76fe5de7af8f1e211a0fae5d76ea', 'COM', 19, 'Operacion registrada.', 1795805923690),
('94ca88ac-2bce-47d4-bca8-3a310c4995af', 3, '30957fc1ca0417d79a03b1ad91067bbcf84c38d66a12e6bad7ddc34e58815d00', 'CAS', 7, 'Operacion registrada.', 1795805923783),
('9717355b-de74-43bc-a82a-378067ac0adc', 2, '800e34ae2e9ddea07a17f66bf23f392f8c408bef40104171a8e8455de532477a', 'COM', 3, 'Operacion registrada.', 1795532157560),
('982a155a-3c6d-4259-85ff-46cdf1839a6f', 5, 'e36ff1e9af4263741bfad0ccac39db545b605efb98c92d5068d48ac38f3a078a', 'REQ', 7, 'Operacion registrada.', 1795532157749),
('9b633767-24d7-4828-b924-be6fdd70d7ba', 5, '33b3f79d477adbecec9ce4a2f9b6703b1d1102e4ef50848e17bed55c2ee8abfa', 'REQ', 7, 'Operacion registrada.', 1795532157742),
('9c04f2de-3583-40d1-a3f1-0fec26223b4b', 2, '5f9773114799497e415b556fda59603ed0f0f17f00c3ca32df219dad845e3305', 'REQ', 6, 'Atencion registrada. COM y REQ creados; puedes completar la ficha o entregarla.', 1795532157719);
INSERT INTO "public"."command_log" VALUES
('9d2a4f8a-a286-4a2e-99e3-f3152861f7d9', 3, '51aacb91e16004d650edb619b9f6757a914d142c05798bd7ea2cf5b347b63a94', 'REQ', 4, 'Atencion resuelta y REQ cerrado sin SOL ni Caso. Resultado e historial conservados.', 1795532157628),
('a02f08fa-e748-428d-96b0-a33062e60c14', 1, 'ae0dff1e2520d77f3d1d09a373f1743fbef4e09c3114aaaaee365d0e43a9c411', 'SOL', 1, 'Operacion registrada.', 1795532157320),
('a0e76720-5ae4-4bb3-895f-150b32f003a8', 4, '268fe9ea8ecab3269dfa40086ee7ab6460cc2b01058d46ed16d2c407e2752183', 'SOL', 3, 'Operacion registrada.', 1795532157574),
('a4793d1d-f9c8-4434-b443-dfca3d6a5d4b', 1, 'bc4b833e90ca1a202920758b8a97f3a78903681b6ecad74d665728cb95329be8', 'COM', 21, 'Operacion registrada.', 1795805923814),
('a7c1c7f4-162a-4dfc-9e45-e23159da7c06', 5, 'c2cbcbfcfbfb0e6c57b4578771c2bd8f7b0b2882cad2442aaedc4640298b6952', 'REQ', 8, 'Operacion registrada.', 1795532157799),
('a8017886-4784-42d8-84fd-48ec5209a2e3', 3, '646c05362de2819b174b841a399c603eb1af1f6662b040f8d3cfdda079d73e06', 'CAS', 1, 'Operacion registrada.', 1795532157425),
('a9d4dfc3-4469-40fd-8360-757c948bd441', 4, 'a040aafe877833ff9bb0228ddf4e2b514bfd8cd0a667f162ed4d7f9367bca26e', 'COM', 21, 'Operacion registrada.', 1795805923805),
('aaba4b46-4d61-4d14-8bf1-39ea47511955', 16, '3892e89d099696a7bc761401ac2452e72db4488b312749555bb566ba627ea724', 'CATALOGO_ESTADISTICO', 3, U&'La versi\00f3n 3 de V\00eda de ingreso qued\00f3 vigente.', 1795805923461),
('ae435a6f-1367-4e92-9e06-9f809170b25b', 16, '1cfc6610b05168ecf3e9fbe4cfe412f257681e1a8b6828b64eddb18cdf474612', 'AMBITO', 14, 'Operacion registrada.', 1795532157968),
('b72308d0-c448-4166-b84a-9d32bad2c62d', 3, '0a3570f54a8a7fb5690adab89fd603bea9c88e17c9de2cbe2f7d5764b3bc765d', 'CAS', 1, 'Operacion registrada.', 1795532157377),
('b7ab0a2a-d874-4255-86de-a50b62be441d', 4, 'be62cff0edbc7e359dfc52b39b934381149d1a6d464cdbad08a3edf194e9a292', 'REQ', 10, 'Operacion registrada.', 1795805923716),
('b8c8a3c8-b0b8-469c-bd52-de641f87c058', 1, '6e575f16ec1c809a35fef636b6fd6d9947ca098e58aacbe79b3d0a04ff10dd7f', 'SOL', 1, 'Operacion registrada.', 1795532157292),
('b9648f4a-660f-4d82-8ac4-13b8b8a5116b', 1, '690c404a13e18367880418c632546bcc730faa28e0836ff5bf4bf04d8844f0dd', 'SOL', 7, 'Operacion registrada.', 1795532157946),
('b96af8fd-c84c-48c8-8144-b24d078e698b', 3, '8155a4f0c1c40ae428ddbe972cb0b619f6eef0864cd3c52879655fe04eadadfe', 'CAS', 5, 'Operacion registrada.', 1795805923672),
('c5d9302d-a660-4bce-b246-7418c49d3734', 2, 'fd10d11e4c67eb26fbb67e23b972a92917ba5a150b21582a858f0e5da56cf064', 'REQ', 5, 'Atencion registrada. COM y REQ creados; puedes completar la ficha o entregarla.', 1795532157699),
('ca98d9ee-2d77-4e3a-9e60-5620997bab43', 3, '6d87cd0aef4d0b4a39fbfdfa133f1e38d39e2dd61a4ede7139565771838057b5', 'REQ', 4, 'Entrega registrada. El mismo REQ conserva su folio y antecedentes.', 1795532157619),
('cb239507-24d3-4d53-901d-c75b42d836a0', 16, 'd013cda0d4a9abcc7fa391a51906c7312715752c242960f30e43e6ec9c45bb64', 'CATALOGO_ESTADISTICO', 8, U&'La versi\00f3n 8 de Tipo de instituci\00f3n qued\00f3 vigente.', 1795805923560),
('cbb5225f-069e-475d-b754-d518d886ea52', 4, 'c066830879b2e7ef21ce59bd1727688b7d27e55d0a0a86cf5b1a0f83591e189b', 'REQ', 9, 'Operacion registrada.', 1795805923664),
('cc38a725-c0e3-4daa-8bae-00d374bcd142', 4, '5c804ac6641ce380b8ce350c29b881d293322554bdfa5438e08db10ae20070cd', 'CAS', 6, 'Operacion registrada.', 1795805923725),
('cfcb76f2-3e11-4118-90b0-22a4bd1bb59c', 16, '5c0fb67d08ad7c11c9efedc02c67f16f3bad4a5cfaafe6d74bba0a8bc7fc54d7', 'CATALOGO_ESTADISTICO', 8, U&'Se import\00f3 la versi\00f3n 8 de Tipo de instituci\00f3n como propuesta (5 \00edtems).', 1795805923554),
('d04840e0-634d-4e1d-925f-83656537062f', 16, 'a7d01490a9ace178860a02c388059bae1ea8575570ea4c49eb7c8eeabfb04d03', 'CATALOGO_ESTADISTICO', 5, U&'Se import\00f3 la versi\00f3n 5 de Contexto como propuesta (12 \00edtems).', 1795805923508),
('d2359df6-7354-4c09-a0e5-365407ca4953', 5, '818fcd6901d8890e631fd73b05fb155c82dfe1a0543d6360d2fed6923368adc5', 'REQ', 8, 'Operacion registrada.', 1795532157793),
('d23f89ae-7910-474e-a615-fccea24a209b', 7, '8278c2d31f5baf7b12e12bf90a41c7d6e1b4ac4175b962f24e5f404802e61299', 'REQ', 7, 'Operacion registrada.', 1795532157764);
INSERT INTO "public"."command_log" VALUES
('d6dbbc8b-b31c-4f3f-8cf0-c9fc544ed8df', 16, '6af7faca999f531dde2fc2eb4a6c419849fe47b020ce5e6fe417c52f98e840cf', 'CATALOGO_ESTADISTICO', 1, U&'La versi\00f3n 1 de Tipo de solicitud qued\00f3 vigente.', 1795805923426),
('d7774f66-4c5b-47f2-862a-0fa959e1824c', 4, '3dc8dbdcab0f2c8c6269fd1b04b0b7d416a1237f881f04ae653b8dfa8586db1e', 'REQ', 1, 'Operacion registrada.', 1795532157515),
('e0407525-04b0-4aa1-8613-132652d3f51c', 7, 'c7b6463c9f8bc5b74e18691598940b05df18cbb766617aba8f94c84234894d0c', 'FLUJO', 3, 'Operacion registrada.', 1795532157837),
('e2072d3b-1539-4887-87df-bd0ebf952c15', 2, '5161d82a96101d360b3842634fca5d70ff9b7a484adfd4420076f6a5fb8d9e83', 'REQ', 4, 'Atencion registrada. COM y REQ creados; puedes completar la ficha o entregarla.', 1795532157602),
('e5dfcae1-d1d2-413d-a2e9-f135608f1732', 4, '73e8c7b43d860d89787995bc6f7984e79beb5f44d46b8d6fd65e33f5fc93c24a', 'CAS', 1, 'Operacion registrada.', 1795532157369),
('e70ef061-a347-4390-8aa8-a6f5dba530c3', 4, '07dc8e29fde9870db8f2276911a0c1cb364fceb6fe22b3e8c9293d57ed1ee87d', 'REQ', 1, 'Operacion registrada.', 1795532157863),
('e831c13e-f065-4c92-bbc9-03cc069cbf12', 2, 'afc11784b41e4be5ad9d4ca3d03982ec76e9271eda94cee19e477fc7a02c6e17', 'REQ', 3, 'Operacion registrada.', 1795532157584),
('ea7271a9-fd4a-436b-a1f0-852bae14d5e8', 3, 'cec8b9fe448444bf1006c26082aee414d085f3a1f897a26919181f10d0a0cd31', 'CAS', 1, 'Operacion registrada.', 1795532157398),
('eb416e02-85c9-4eb4-baf1-de86078f494f', 1, '55319de55516789bb775c9122163474be0d952f3593c929b19234440b301e220', 'COM', 14, 'Operacion registrada.', 1795532157881),
('ed85c30a-610b-4c73-bdeb-ee06e9665dc3', 4, 'a1b68a407ae749542280dc24802f1a71788a837f45920b65950983dfb08a59c0', 'SOL', 6, 'Operacion registrada.', 1795532157928),
('f008e4dd-db0a-4b4e-b02f-0a0fa8925a82', 4, '9337d55e1ea3c476a32359d3e04e7e3cbb18f1c7377743d88290c65276c5a16c', 'REQ', 1, 'Operacion registrada.', 1795532157338),
('f4ddf248-e8d5-496c-be84-2423a420973d', 19, '3808cf5d3fead1a56cba44e86c58180117f7aeab58bb8162df1b5ed81d05faa2', 'CORTE', 1, 'Operacion registrada.', 1795805923907),
('f65b9c75-1cc2-40f8-bbd7-b2e6636055a3', 4, '079721893ddb15bfaaf79978d2ba015624a96e0f3c9844c00069c4568697fa5c', 'REQ', 1, 'Operacion registrada.', 1795532157529),
('f7106b05-68cd-4e9f-9151-aaedc1f92b4c', 16, '39f85dc98b7cbfd1d9077f530bce2200b3d0879a0ab8a1331d61c69acde5ac2e', 'CATALOGO_ESTADISTICO', 7, U&'La versi\00f3n 7 de Relaci\00f3n con el NNA qued\00f3 vigente.', 1795805923548),
('fa6b4c98-bdad-4bc9-9282-55c10556a86f', 16, '6e8ea438d7cdbaab899ae8c9caf0979d7bb30f676f8bb3b6cc6d7f3c82988d83', 'CATALOGO_ESTADISTICO', 4, U&'Se import\00f3 la versi\00f3n 4 de Derechos como propuesta (81 \00edtems).', 1795805923492),
('fc33e9ed-4e28-473d-bb7b-0287b9c51773', 16, 'ef0cd779f04aa255d88e0f141feaaf35730c5f0f7e74b32e191eb1c91abfe4e9', 'CATALOGO_ESTADISTICO', 2, U&'Se import\00f3 la versi\00f3n 2 de Tipo de requirente como propuesta (4 \00edtems).', 1795805923436),
('fee7619b-8558-489b-b076-046ebe2c8aeb', 4, '528affaa4510a2f72331ed4f8b2c34d41bec85968bbd1c82b34e1eb60737502d', 'COM', 2, 'Operacion registrada.', 1795532157538),
('ff466adb-6680-459f-8780-777fa6af4601', 4, '2d7891740cef784e120e56fc68b77b008bd81a1ca89bf5554d3c22881c6fb42b', 'COM', 20, 'Operacion registrada.', 1795805923736);
CREATE INDEX "public"."ix_command_log_result" ON "public"."command_log"("result_kind" NULLS FIRST, "result_id" NULLS FIRST, "created_ms" NULLS FIRST);
CREATE MEMORY TABLE "public"."mail_outbox"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) DEFAULT ON NULL NOT NULL,
    "notice_id" BIGINT NOT NULL,
    "recipient_id" BIGINT NOT NULL,
    "correlation_id" CHARACTER(36) NOT NULL,
    "state" CHARACTER VARYING(16) NOT NULL,
    "attempts" INTEGER DEFAULT 0 NOT NULL,
    "next_ms" BIGINT NOT NULL,
    "claim_id" CHARACTER(36),
    "lease_until_ms" BIGINT,
    "last_phase" CHARACTER VARYING(24),
    "last_http" INTEGER,
    "last_request_id" CHARACTER VARYING(36),
    "accepted_ms" BIGINT,
    "resolution" CHARACTER VARYING(16),
    "resolved_by" BIGINT,
    "resolved_ms" BIGINT,
    "created_ms" BIGINT NOT NULL,
    "updated_ms" BIGINT NOT NULL
);
ALTER TABLE "public"."mail_outbox" ADD CONSTRAINT "public"."CONSTRAINT_FD" PRIMARY KEY("id");
-- 0 +/- SELECT COUNT(*) FROM public.mail_outbox;
CREATE INDEX "public"."ix_mail_outbox_due" ON "public"."mail_outbox"("state" NULLS FIRST, "next_ms" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."closure_record"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 9) DEFAULT ON NULL NOT NULL,
    "requirement_id" BIGINT NOT NULL,
    "case_id" BIGINT,
    "actor_id" BIGINT NOT NULL,
    "closed_ms" BIGINT NOT NULL,
    "mode" CHARACTER VARYING(32) NOT NULL,
    "reason" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."closure_record" ADD CONSTRAINT "public"."CONSTRAINT_42" PRIMARY KEY("id");
-- 8 +/- SELECT COUNT(*) FROM public.closure_record;
INSERT INTO "public"."closure_record" VALUES
(1, 1, 2, 8, 1795532157476, 'PROFESIONAL_BAJA', 'Apoyo entregado'),
(2, 1, 1, 4, 1795532157485, 'JEFATURA_PROPIA', 'Cierre confirmado'),
(3, 1, NULL, 4, 1795532157499, 'JEFATURA_PRINCIPAL', 'Motivo de cierre sintetico'),
(4, 3, NULL, 2, 1795531857577, 'PRIMERA_ATENCION', 'Orientacion entregada'),
(5, 4, NULL, 3, 1795532157625, 'PRIMERA_ATENCION', 'Orientacion concluida'),
(6, 7, NULL, 7, 1795532157762, 'JEFATURA_PRINCIPAL', 'Cierre sintetico tras transferencia'),
(7, 8, 3, 7, 1795532157830, 'DERIVACION', 'Aceptada; sucesor 4'),
(8, 11, 7, 3, 1795805923790, 'PROFESIONAL_BAJA', 'Cierre sintetico');
CREATE MEMORY TABLE "public"."communication_person"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) DEFAULT ON NULL NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "person_role" CHARACTER VARYING(24) NOT NULL,
    "name" CHARACTER VARYING(200),
    "identity_document" CHARACTER VARYING(100),
    "relationship" CHARACTER VARYING(200),
    "location_text" CHARACTER VARYING(500)
);
ALTER TABLE "public"."communication_person" ADD CONSTRAINT "public"."CONSTRAINT_46" PRIMARY KEY("id");
-- 0 +/- SELECT COUNT(*) FROM public.communication_person;
CREATE MEMORY TABLE "public"."intake_upload"(
    "token" CHARACTER VARYING(36) NOT NULL,
    "user_id" BIGINT NOT NULL,
    "scope_id" BIGINT NOT NULL,
    "name" CHARACTER VARYING(180) NOT NULL,
    "storage_key" CHARACTER VARYING(80) NOT NULL,
    "sha256" CHARACTER(64) NOT NULL,
    "size_bytes" BIGINT NOT NULL,
    "created_ms" BIGINT NOT NULL,
    "communication_id" BIGINT
);
ALTER TABLE "public"."intake_upload" ADD CONSTRAINT "public"."CONSTRAINT_8D6" PRIMARY KEY("token");
-- 0 +/- SELECT COUNT(*) FROM public.intake_upload;
CREATE INDEX "public"."ix_intake_upload_owner" ON "public"."intake_upload"("user_id" NULLS FIRST, "communication_id" NULLS FIRST, "created_ms" NULLS FIRST);
CREATE MEMORY TABLE "public"."communication_link"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 4) DEFAULT ON NULL NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "requirement_id" BIGINT NOT NULL,
    "reason" CHARACTER VARYING NOT NULL,
    "proposed_by" BIGINT NOT NULL,
    "proposed_ms" BIGINT NOT NULL,
    "state" CHARACTER VARYING(16) DEFAULT 'PROPUESTA' NOT NULL,
    "target_case_id" BIGINT,
    "decided_by" BIGINT,
    "decided_ms" BIGINT,
    "decision_reason" CHARACTER VARYING,
    "revision" BIGINT DEFAULT 0 NOT NULL,
    "returned_ms" BIGINT,
    "returned_by" BIGINT,
    "return_reason" CHARACTER VARYING
);
ALTER TABLE "public"."communication_link" ADD CONSTRAINT "public"."CONSTRAINT_C9" PRIMARY KEY("id");
-- 3 +/- SELECT COUNT(*) FROM public.communication_link;
INSERT INTO "public"."communication_link" VALUES
(1, 13, 1, 'Posible relacion', 1, 1795532157853, 'ADMITIDA', NULL, 4, 1795532157862, 'Mismo asunto', 1, NULL, NULL, NULL),
(2, 14, 1, 'Posible relacion', 1, 1795532157878, 'RECHAZADA', NULL, 4, 1795532157886, 'Asunto distinto', 2, 1795532157893, 4, 'Reclasificar'),
(3, 21, 9, 'Antecedente sintetico', 1, 1795805923810, 'ADMITIDA', 5, 4, 1795805923819, 'Antecedente del Caso', 1, NULL, NULL, NULL);
CREATE MEMORY TABLE "public"."stat_catalog_version"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 9) DEFAULT ON NULL NOT NULL,
    "catalog" CHARACTER VARYING(32) NOT NULL,
    "label" CHARACTER VARYING(200) NOT NULL,
    "state" CHARACTER VARYING(12) NOT NULL,
    "source" CHARACTER VARYING(500) NOT NULL,
    "file_sha256" CHARACTER(64) NOT NULL,
    "loaded_by" BIGINT NOT NULL,
    "loaded_ms" BIGINT NOT NULL,
    "basis" CHARACTER VARYING NOT NULL,
    "activated_by" BIGINT,
    "activated_ms" BIGINT,
    "activation_basis" CHARACTER VARYING,
    "retired_by" BIGINT,
    "retired_ms" BIGINT,
    "current_catalog" CHARACTER VARYING(32),
    "revision" BIGINT DEFAULT 0 NOT NULL
);
ALTER TABLE "public"."stat_catalog_version" ADD CONSTRAINT "public"."CONSTRAINT_54" PRIMARY KEY("id");
-- 8 +/- SELECT COUNT(*) FROM public.stat_catalog_version;
INSERT INTO "public"."stat_catalog_version" VALUES
(1, 'TIPO_SOLICITUD', U&'Tipo de solicitud, propuesta versi\00f3n 1', 'VIGENTE', U&'Propuesta SIGER 0.8.0, versi\00f3n 1 (D29, plan 0.8.0); DI-01: respuesta propuesta del responsable del 2026-11-24 (docs/VALIDACION_CATALOGOS_0_8_0.md), pendiente de conformidad institucional.', '92639c46c37bad08104cfb2b4e8753739ba3d80c720b997f3320be985b722080', 16, 1795805923410, 'Volcado sintetico', 16, 1795805923424, 'Volcado sintetico', NULL, NULL, 'TIPO_SOLICITUD', 1),
(2, 'TIPO_REQUIRENTE', U&'Tipo de requirente, propuesta versi\00f3n 1', 'VIGENTE', U&'Propuesta SIGER 0.8.0, versi\00f3n 1 (D30, plan 0.8.0); DI-01: respuesta propuesta del responsable del 2026-11-24 (docs/VALIDACION_CATALOGOS_0_8_0.md), pendiente de conformidad institucional.', 'af9895fa20c4f68cf2b9d746d1a4286b943e2bc567fbcbd068e40e1f2d9bccdd', 16, 1795805923432, 'Volcado sintetico', 16, 1795805923442, 'Volcado sintetico', NULL, NULL, 'TIPO_REQUIRENTE', 1),
(3, 'VIA_INGRESO', U&'V\00eda de ingreso, propuesta versi\00f3n 1', 'VIGENTE', U&'Propuesta SIGER 0.8.0, versi\00f3n 1 (D31, plan 0.8.0); DI-01: respuesta propuesta del responsable del 2026-11-24 (docs/VALIDACION_CATALOGOS_0_8_0.md), pendiente de conformidad institucional. Agrupaciones estad\00edsticas como \00edtems padre no seleccionables; se conserva la v\00eda original.', 'ad910b8946d768524676d3ce61a2d89c6ae82eabdd47ffa82738482e0dbb4cc6', 16, 1795805923449, 'Volcado sintetico', 16, 1795805923460, 'Volcado sintetico', NULL, NULL, 'VIA_INGRESO', 1),
(4, 'DERECHO', U&'Modelo de derechos versi\00f3n 2.0 (2022)', 'VIGENTE', U&'Defensor\00eda de la Ni\00f1ez, "Modelo de conceptualizaci\00f3n y categorizaci\00f3n de derechos de la ni\00f1ez y adolescencia", versi\00f3n 2.0, 2022 (Observatorio de Derechos). Transcripci\00f3n de la Tabla 2 (p\00e1gs. 9-10); sin el eje VII (cat\00e1logo Rol del Estado); DI-04: respuesta propuesta del responsable del 2026-11-24 (docs/VALIDACION_CATALOGOS_0_8_0.md), pendiente de conformidad institucional.', 'd645dfe3c5d3717725424f2aa53b64894ad1d277fdb5fb520b20b61218311066', 16, 1795805923468, 'Volcado sintetico', 16, 1795805923498, 'Volcado sintetico', NULL, NULL, 'DERECHO', 1),
(5, 'CONTEXTO', U&'Contexto, propuesta versi\00f3n 1', 'VIGENTE', U&'Propuesta SIGER 0.8.0, versi\00f3n 1 (D34); DI-02: respuesta propuesta del responsable del 2026-11-24 (docs/VALIDACION_CATALOGOS_0_8_0.md), pendiente de conformidad institucional, incluidas sus definiciones operativas.', 'a5d08c9fcc76f137572218f6a87de3ffd54003c499719acb465d302acdc5adba', 16, 1795805923504, 'Volcado sintetico', 16, 1795805923513, 'Volcado sintetico', NULL, NULL, 'CONTEXTO', 1),
(6, 'TIPO_ACTUACION', U&'Tipo de actuaci\00f3n, propuesta versi\00f3n 1', 'VIGENTE', U&'Propuesta SIGER 0.8.0, versi\00f3n 1 (D36, plan 0.8.0); DI-03: respuesta propuesta del responsable del 2026-11-24 (docs/VALIDACION_CATALOGOS_0_8_0.md), pendiente de conformidad institucional. Una actuaci\00f3n tiene un solo tipo y se clasifica por su prop\00f3sito; solo cuentan las actuaciones efectivamente realizadas.', '72bfcbd3fd7436cd6c14e7b6e41ee906cf74d033a6c93b5168a27daa51d8e24a', 16, 1795805923520, 'Volcado sintetico', 16, 1795805923533, 'Volcado sintetico', NULL, NULL, 'TIPO_ACTUACION', 1),
(7, 'RELACION', U&'Relaci\00f3n con el NNA, propuesta versi\00f3n 1', 'VIGENTE', U&'Propuesta SIGER 0.8.0, versi\00f3n 1 (DISE\00d1O 2.2): valores del campo relacion del Formulario P\00fablico (formulario-web 6f886eb) y sus r\00f3tulos, m\00e1s dos valores exclusivos de SIGER; DI-03: respuesta propuesta del responsable del 2026-11-24 (docs/VALIDACION_CATALOGOS_0_8_0.md), pendiente de conformidad institucional.', '9d043acbe5e6d11c2e6ebb9aee50e939974d902f4492f3a3bc01c8c677775fb1', 16, 1795805923539, 'Volcado sintetico', 16, 1795805923547, 'Volcado sintetico', NULL, NULL, 'RELACION', 1),
(8, 'TIPO_INSTITUCION', U&'Tipo de instituci\00f3n, propuesta versi\00f3n 1', 'VIGENTE', U&'Propuesta SIGER 0.8.0, versi\00f3n 1 (DISE\00d1O 2.2): valores de instituciones[].tipo del Formulario P\00fablico (formulario-web 6f886eb); DI-03: respuesta propuesta del responsable del 2026-11-24 (docs/VALIDACION_CATALOGOS_0_8_0.md), pendiente de conformidad institucional.', 'ba3eacb3ead8b8ed9f0dac1905dbdf38f6a027c001bd81a8f6fe9def834ddb22', 16, 1795805923553, 'Volcado sintetico', 16, 1795805923560, 'Volcado sintetico', NULL, NULL, 'TIPO_INSTITUCION', 1);
CREATE UNIQUE NULLS DISTINCT INDEX "public"."ux_stat_catalog_current" ON "public"."stat_catalog_version"("current_catalog" NULLS FIRST);
CREATE INDEX "public"."ix_stat_catalog_version_catalog" ON "public"."stat_catalog_version"("catalog" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."attention_handoff"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 2) DEFAULT ON NULL NOT NULL,
    "requirement_id" BIGINT NOT NULL,
    "from_user" BIGINT NOT NULL,
    "from_scope" BIGINT NOT NULL,
    "sent_by" BIGINT NOT NULL,
    "sent_ms" BIGINT NOT NULL,
    "target_scope" BIGINT NOT NULL,
    "target_user" BIGINT,
    "recipient_kind" CHARACTER VARYING(16) NOT NULL,
    "state" CHARACTER VARYING(16) DEFAULT 'PENDIENTE' NOT NULL,
    "reason" CHARACTER VARYING(2000) NOT NULL,
    "decided_by" BIGINT,
    "decided_ms" BIGINT,
    "decision_reason" CHARACTER VARYING(2000)
);
ALTER TABLE "public"."attention_handoff" ADD CONSTRAINT "public"."CONSTRAINT_FC" PRIMARY KEY("id");
-- 1 +/- SELECT COUNT(*) FROM public.attention_handoff;
INSERT INTO "public"."attention_handoff" VALUES
(1, 4, 2, 4, 2, 1795532157609, 4, 3, 'PROFESIONAL', 'RECIBIDA', 'Continuar la atencion', 3, 1795532157616, 'Recibo la atencion');
CREATE INDEX "public"."ix_attention_pending" ON "public"."attention_handoff"("requirement_id" NULLS FIRST, "state" NULLS FIRST);
CREATE INDEX "public"."ix_attention_recipient" ON "public"."attention_handoff"("target_user" NULLS FIRST, "state" NULLS FIRST, "target_scope" NULLS FIRST);
CREATE MEMORY TABLE "public"."communication_original"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 5) DEFAULT ON NULL NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "source" CHARACTER VARYING(48) NOT NULL,
    "external_id" CHARACTER VARYING(100) NOT NULL,
    "document_version" CHARACTER VARYING(8) NOT NULL,
    "document_kind" CHARACTER VARYING(24) NOT NULL,
    "received_ms" BIGINT NOT NULL,
    "content_hash" CHARACTER(64) NOT NULL,
    "provenance_hash" CHARACTER(64) NOT NULL,
    "custody_key_id" CHARACTER VARYING(64) NOT NULL,
    "document" CHARACTER VARYING NOT NULL,
    "document_sha256" CHARACTER(64) NOT NULL,
    "document_bytes" BIGINT NOT NULL,
    "stored_ms" BIGINT NOT NULL,
    "stored_by" BIGINT NOT NULL
);
ALTER TABLE "public"."communication_original" ADD CONSTRAINT "public"."CONSTRAINT_206" PRIMARY KEY("id");
-- 4 +/- SELECT COUNT(*) FROM public.communication_original;
INSERT INTO "public"."communication_original" VALUES
(1, 6, 'FORMULARIO_PUBLICO_DDN', 'd76662e1-7441-4b6b-a731-e2bfb1d3c0bf', '1.0', 'ADULTO_CU', 1793793600000, 'dcb39241aca24c2969d406991108860c2149e04889fcc60aa39b21dae5537aab', '380e9ca0e42b7f953bedb5e5d128b8243e15a67288dd3bf052db7ea5933e1183', 'PRUEBA_SIGER', U&'{"comunicacion":{"version":"1.0","id":"d76662e1-7441-4b6b-a731-e2bfb1d3c0bf","recorridoId":"ab923a06-2bb4-4c2c-8a80-3459262083f2","tipo":"ADULTO_CU","recibidaEn":"2026-11-04T12:00:00Z","avisoVersion":"LAB_1","aceptaCanalDigital":true,"datos":{"campos":{"motivo":"vulneracion","adult-region":"13","adult-commune":"\00d1u\00f1oa","adult-address":"Avenida Irarr\00e1zaval 1234, depto. 5","email":"maria.jose@example.cl","telefono":"+56912345678","relato":"Relato sint\00e9tico con tildes: ni\00f1o, se\00f1al, acci\00f3n.","riesgo":"si","relacion":"madre","identifica-responsables":"true"},"personas":[{"personaId":"afectado-1","detalles":{"region":"13","comuna":"\00d1u\00f1oa"}},{"personaId":"relacionado-1","detalles":{"relacion":"Familiar"}},{"personaId":"senalado-1","detalles":{"alias":"El vecino"}}],"instituciones":[{"nombre":"Escuela B\00e1sica Sint\00e9tica","comuna":"\00d1u\00f1oa","region":"13","tipo":"Establecimiento educativo"}],"adjuntos":[]},"personas":[{"id":"presentador-adulto","rol":"PRESENTADOR","runNumero":"12345678","runDv":"5","modulo11":false,"autenticada":true,"estadoConsulta":"NO_HABILITADO","consultadaEn":null,"consultaRef":null,"atributos":{"runNumero":{"valor":"12345678","codigo":null,"fuente":"CLAVE_UNICA_USERINFO","nivel":"IDENTIDAD_AUTENTICADA","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"},"runDv":{"valor":"5","codigo":null,"fuente":"CLAVE_UNICA_USERINFO","nivel":"IDENTIDAD_AUTENTICADA","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"},"nombres":{"valor":"Mar\00eda Jos\00e9 De los \00c1ngeles","codigo":null,"fuente":"CLAVE_UNICA_USERINFO","nivel":"IDENTIDAD_AUTENTICADA","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"},"apellidos":{"valor":"Del R\00edo Gonz\00e1lez","codigo":null,"fuente":"CLAVE_UNICA_USERINFO","nivel":"IDENTIDAD_AUTENTICADA","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"},"primerNombre":{"valor":"Mar\00eda Jos\00e9","codigo":null,"fuente":"CLAVE_UNICA_USERINFO","nivel":"IDENTIDAD_AUTENTICADA","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"},"segundoNombre":{"valor":"De los \00c1ngeles","codigo":null,"fuente":"CLAVE_UNICA_USERINFO","nivel":"IDENTIDAD_AUTENTICADA","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"}},"discrepancias":{}},{"id":"afectado-1","rol":"AFECTADO_NNA","runNumero":null,"runDv":null,"modulo11":false,"autenticada":false,"estadoConsulta":"NO_HABILITADO","consultadaEn":null,"consultaRef":null,"atributos":{"primerNombre":{"valor":"Tom\00e1s","codigo":null,"fuente":"DECLARACION_CIUDADANA","nivel":"DECLARADO_NO_CONTRASTADO","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"},"edad":{"valor":"9","codigo":null,"fuente":"DECLARACION_CIUDADANA","nivel":"DECLARADO_NO_CONTRASTADO","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"}},"discrepancias":{}},{"id":"relacionado-1","rol":"ADULTO_RELACIONADO","runNumero":null,"runDv":null,"modulo11":false,"autenticada":false,"estadoConsulta":"NO_HABILITADO","consultadaEn":null,"consultaRef":null,"atributos":{"primerNombre":{"valor":"Rosa","codigo":null,"fuente":"DECLARACION_CIUDADANA","nivel":"DECLARADO_NO_CONTRASTADO","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"}},"discrepancias":{}},{"id":"senalado-1","rol":"PERSONA_SENALADA","runNumero":null,"runDv":null,"modulo11":false,"autenticada":false,"estadoConsulta":"NO_HABILITADO","consultadaEn":null,"consultaRef":null,"atributos":{},"discrepancias":{}}],"adjuntos":[]},"sha256":"dcb39241aca24c2969d406991108860c2149e04889fcc60aa39b21dae5537aab","huellaProcedencia":"380e9ca0e42b7f953bedb5e5d128b8243e15a67288dd3bf052db7ea5933e1183","custodia":{"algoritmo":"Ed25519","keyId":"PRUEBA_SIGER","payload":"eyJ2ZXJzaW9uIjoiMS4wIiwiaWQiOiJkNzY2NjJlMS03NDQxLTRiNmItYTczMS1lMmJmYjFkM2MwYmYiLCJyZWNpYmlkYUVuIjoiMjAyNi0xMS0wNFQxMjowMDowMFoiLCJ0aXBvUmVjZXBjaW9uIjoiQ1VTVE9ESUFfUFVCTElDQSIsImh1ZWxsYUNvbnRlbmlkbyI6ImRjYjM5MjQxYWNhMjRjMjk2OWQ0MDY5OTExMDg4NjBjMjE0OWUwNDg4OWZjYzYwYWEzOWIyMWRhZTU1MzdhYWIiLCJodWVsbGFQcm9jZWRlbmNpYSI6IjM4MGU5Y2EwZTQyYjdmOTUzYmVkYjVlNWQxMjhiODI0M2UxNWE2NzI4OGRkM2JmMDUyZGI3ZWE1OTMzZTExODMiLCJlbWlzb3IiOiJGT1JNVUxBUklPX1BVQkxJQ09fREROIiwiYXZpc29WZXJzaW9uIjoiTEFCXzEifQ","firma":"usDyHqmt8GYaX0kMZ7Q8kPh1wcEIpZRlYmn6nRlN6BozZHFh6sT5LguReoO-aKgINLZz0UmhzmQp8djBQPQ9CQ"}}', '7b88b80be9befd2423fde677fab79062bf48759bd966ab5a992a1142c6ce5980', 4462, 1795532157649, 2);
INSERT INTO "public"."communication_original" VALUES
(2, 7, 'FORMULARIO_PUBLICO_DDN', '66666666-6666-4666-8666-666666666670', '1.0', 'NNA', 1794744000000, '277f40bf49f67c6ffecdcd31ee8de29d3c612f5821533020441f72c83840ec56', 'dfa631787f07fee7d3b6e80d615f4086a214986f8192687ea29a8929cadcf91f', 'nna-srcei-public-test', U&'{\000a  "comunicacion" : {\000a    "version" : "1.0",\000a    "id" : "66666666-6666-4666-8666-666666666670",\000a    "recorridoId" : "66666666-6666-4666-8666-666666666667",\000a    "tipo" : "NNA",\000a    "recibidaEn" : "2026-11-15T12:00:00Z",\000a    "avisoVersion" : "NNA_SRCEI_SINTETICO_1",\000a    "aceptaCanalDigital" : false,\000a    "datos" : {\000a      "campos" : {\000a        "nna-primary-method" : "unsafe",\000a        "nna-region" : "13",\000a        "nna-story" : "Relato exclusivamente sint\00e9tico para el contrato NNA SRCeI."\000a      },\000a      "personas" : [ ],\000a      "instituciones" : [ ],\000a      "adjuntos" : [ ]\000a    },\000a    "personas" : [ {\000a      "id" : "presentador-nna",\000a      "rol" : "PRESENTADOR",\000a      "runNumero" : "99999999",\000a      "runDv" : "9",\000a      "modulo11" : true,\000a      "autenticada" : false,\000a      "estadoConsulta" : "PARCIAL",\000a      "consultadaEn" : "2026-11-15T12:00:00Z",\000a      "consultaRef" : "66666666-6666-4666-8666-666666666666",\000a      "atributos" : {\000a        "primerApellido" : {\000a          "valor" : "APELLIDO SINTETICO",\000a          "codigo" : null,\000a          "fuente" : "SRCEI",\000a          "nivel" : "CONTRASTADO_EN_FUENTE",\000a          "observadoEn" : "2026-11-15T12:00:00Z",\000a          "evidencia" : "66666666-6666-4666-8666-666666666666"\000a        },\000a        "fechaNacimiento" : {\000a          "valor" : "2012-03-04",\000a          "codigo" : null,\000a          "fuente" : "SRCEI",\000a          "nivel" : "CONTRASTADO_EN_FUENTE",\000a          "observadoEn" : "2026-11-15T12:00:00Z",\000a          "evidencia" : "66666666-6666-4666-8666-666666666666"\000a        },\000a        "runDv" : {\000a          "valor" : "9",\000a          "codigo" : null,\000a          "fuente" : "SRCEI",\000a          "nivel" : "CONTRASTADO_EN_FUENTE",\000a          "observadoEn" : "2026-11-15T12:00:00Z",\000a          "evidencia" : "66666666-6666-4666-8666-666666666666"\000a        },\000a        "runNumero" : {\000a          "valor" : "99999999",\000a          "codigo" : null,\000a          "fuente" : "SRCEI",\000a          "nivel" : "CONTRASTADO_EN_FUENTE",\000a          "observadoEn" : "2026-11-15T12:00:00Z",\000a          "evidencia" : "66666666-6666-4666-8666-666666666666"\000a        },\000a        "nombres" : {\000a          "valor" : "NOMBRE SINTETICO",\000a          "codigo" : null,\000a          "fuente" : "SRCEI",\000a          "nivel" : "CONTRASTADO_EN_FUENTE",\000a          "observadoEn" : "2026-11-15T12:00:00Z",\000a          "evidencia" : "66666666-6666-4666-8666-666666666666"\000a        }\000a      },\000a      "discrepancias" : { }\000a    } ],\000a    "adjuntos" : [ ]\000a  },\000a  "sha256" : "277f40bf49f67c6ffecdcd31ee8de29d3c612f5821533020441f72c83840ec56",\000a  "huellaProcedencia" : "dfa631787f07fee7d3b6e80d615f4086a214986f8192687ea29a8929cadcf91f",\000a  "custodia" : {\000a    "algoritmo" : "Ed25519",\000a    "keyId" : "nna-srcei-public-test",\000a    "payload" : "eyJ2ZXJzaW9uIjoiMS4wIiwiaWQiOiI2NjY2NjY2Ni02NjY2LTQ2NjYtODY2Ni02NjY2NjY2NjY2NzAiLCJyZWNpYmlkYUVuIjoiMjAyNi0xMS0xNVQxMjowMDowMFoiLCJ0aXBvUmVjZXBjaW9uIjoiQ1VTVE9ESUFfUFVCTElDQSIsImh1ZWxsYUNvbnRlbmlkbyI6IjI3N2Y0MGJmNDlmNjdjNmZmZWNkY2QzMWVlOGRlMjlkM2M2MTJmNTgyMTUzMzAyMDQ0MWY3MmM4Mzg0MGVjNTYiLCJodWVsbGFQcm9jZWRlbmNpYSI6ImRmYTYzMTc4N2YwN2ZlZTdkM2I2ZTgwZDYxNWY0MDg2YTIxNDk4NmY4MTkyNjg3ZWEyOWE4OTI5Y2FkY2Y5MWYiLCJlbWlzb3IiOiJGT1JNVUxBUklPX1BVQkxJQ09fREROIiwiYXZpc29WZXJzaW9uIjoiTk5BX1NSQ0VJX1NJTlRFVElDT18xIn0",\000a    "firma" : "HzPqujLt3G17ottAbAPBp4gDMhmU0REu9w_Wp2gMMwCS1KL1WWEz51-9_zZ2EXqMhfZhFb_tn2CqWz8GB239Cw"\000a  }\000a}\000a', 'f7cbc491ad0426ab7afcccd898f0854bf8aa28cf17d12466ae28e38d95ee9380', 3327, 1795532157670, 2),
(3, 8, 'FORMULARIO_PUBLICO_DDN', '66666666-6666-4666-8666-666666666671', '1.1', 'NNA', 1794744000000, 'e547458b2fd6c49a8a824c6e4c6545136d564d9c77892397106663a7dea91ecb', 'dfa631787f07fee7d3b6e80d615f4086a214986f8192687ea29a8929cadcf91f', 'nna-srcei-public-test', U&'{\000a  "comunicacion" : {\000a    "version" : "1.1",\000a    "id" : "66666666-6666-4666-8666-666666666671",\000a    "recorridoId" : "66666666-6666-4666-8666-666666666667",\000a    "tipo" : "NNA",\000a    "recibidaEn" : "2026-11-15T12:00:00Z",\000a    "avisoVersion" : "NNA_SRCEI_SINTETICO_1",\000a    "aceptaCanalDigital" : false,\000a    "datos" : {\000a      "campos" : {\000a        "nna-primary-method" : "unsafe",\000a        "nna-region" : "13",\000a        "nna-story" : "Relato exclusivamente sint\00e9tico para el contrato NNA SRCeI."\000a      },\000a      "personas" : [ ],\000a      "instituciones" : [ ],\000a      "adjuntos" : [ "66666666-6666-4666-8666-666666666668" ]\000a    },\000a    "personas" : [ {\000a      "id" : "presentador-nna",\000a      "rol" : "PRESENTADOR",\000a      "runNumero" : "99999999",\000a      "runDv" : "9",\000a      "modulo11" : true,\000a      "autenticada" : false,\000a      "estadoConsulta" : "PARCIAL",\000a      "consultadaEn" : "2026-11-15T12:00:00Z",\000a      "consultaRef" : "66666666-6666-4666-8666-666666666666",\000a      "atributos" : {\000a        "primerApellido" : {\000a          "valor" : "APELLIDO SINTETICO",\000a          "codigo" : null,\000a          "fuente" : "SRCEI",\000a          "nivel" : "CONTRASTADO_EN_FUENTE",\000a          "observadoEn" : "2026-11-15T12:00:00Z",\000a          "evidencia" : "66666666-6666-4666-8666-666666666666"\000a        },\000a        "fechaNacimiento" : {\000a          "valor" : "2012-03-04",\000a          "codigo" : null,\000a          "fuente" : "SRCEI",\000a          "nivel" : "CONTRASTADO_EN_FUENTE",\000a          "observadoEn" : "2026-11-15T12:00:00Z",\000a          "evidencia" : "66666666-6666-4666-8666-666666666666"\000a        },\000a        "runDv" : {\000a          "valor" : "9",\000a          "codigo" : null,\000a          "fuente" : "SRCEI",\000a          "nivel" : "CONTRASTADO_EN_FUENTE",\000a          "observadoEn" : "2026-11-15T12:00:00Z",\000a          "evidencia" : "66666666-6666-4666-8666-666666666666"\000a        },\000a        "runNumero" : {\000a          "valor" : "99999999",\000a          "codigo" : null,\000a          "fuente" : "SRCEI",\000a          "nivel" : "CONTRASTADO_EN_FUENTE",\000a          "observadoEn" : "2026-11-15T12:00:00Z",\000a          "evidencia" : "66666666-6666-4666-8666-666666666666"\000a        },\000a        "nombres" : {\000a          "valor" : "NOMBRE SINTETICO",\000a          "codigo" : null,\000a          "fuente" : "SRCEI",\000a          "nivel" : "CONTRASTADO_EN_FUENTE",\000a          "observadoEn" : "2026-11-15T12:00:00Z",\000a          "evidencia" : "66666666-6666-4666-8666-666666666666"\000a        }\000a      },\000a      "discrepancias" : { }\000a    } ],\000a    "adjuntos" : [ {\000a      "id" : "66666666-6666-4666-8666-666666666668",\000a      "nombre" : "sintetico.txt",\000a      "tamanio" : 45,\000a      "sha256" : "b5a546c94e508a25afa40d7b4f4e2a7e43c51eedfb477bc6816f14a5d10ab8bb",\000a      "tipo" : "text/plain",\000a      "estado" : "VALIDADO"\000a    } ]\000a  },\000a  "sha256" : "e547458b2fd6c49a8a824c6e4c6545136d564d9c77892397106663a7dea91ecb",\000a  "huellaProcedencia" : "dfa631787f07fee7d3b6e80d615f4086a214986f8192687ea29a8929cadcf91f",\000a  "custodia" : {\000a    "algoritmo" : "Ed25519",\000a    "keyId" : "nna-srcei-public-test",\000a    "payload" : "eyJ2ZXJzaW9uIjoiMS4wIiwiaWQiOiI2NjY2NjY2Ni02NjY2LTQ2NjYtODY2Ni02NjY2NjY2NjY2NzEiLCJyZWNpYmlkYUVuIjoiMjAyNi0xMS0xNVQxMjowMDowMFoiLCJ0aXBvUmVjZXBjaW9uIjoiQ1VTVE9ESUFfUFVCTElDQSIsImh1ZWxsYUNvbnRlbmlkbyI6ImU1NDc0NThiMmZkNmM0OWE4YTgyNGM2ZTRjNjU0NTEzNmQ1NjRkOWM3Nzg5MjM5NzEwNjY2M2E3ZGVhOTFlY2IiLCJodWVsbGFQcm9jZWRlbmNpYSI6ImRmYTYzMTc4N2YwN2ZlZTdkM2I2ZTgwZDYxNWY0MDg2YTIxNDk4NmY4MTkyNjg3ZWEyOWE4OTI5Y2FkY2Y5MWYiLCJlbWlzb3IiOiJGT1JNVUxBUklPX1BVQkxJQ09fREROIiwiYXZpc29WZXJzaW9uIjoiTk5BX1NSQ0VJX1NJTlRFVElDT18xIn0",\000a    "firma" : "EiQlGccijh27-_gRkpuQvy8zSJqMl3SOWg-ecfs2Rjt8ij1XWLNMF4gpaO5FtPmD_DTdS8izbcug04qxwryEDg"\000a  }\000a}\000a', '3ae1f376e5b980260ff02e5ddabf571cdfbbb52edeb2b9fb7c1537ee68d4ff87', 3625, 1795532157685, 2);
INSERT INTO "public"."communication_original" VALUES
(4, 18, 'FORMULARIO_PUBLICO_DDN', '3433d3bb-3619-486d-9ae2-b952b71ee3c9', '1.0', 'ADULTO_CU', 1793793600000, 'bdeeb3a14421f45b2291ac58efeb07bf230d4ecfa711a204a63f827e23ec06af', '380e9ca0e42b7f953bedb5e5d128b8243e15a67288dd3bf052db7ea5933e1183', 'PRUEBA_SIGER', U&'{"comunicacion":{"version":"1.0","id":"3433d3bb-3619-486d-9ae2-b952b71ee3c9","recorridoId":"060c0bba-76fd-48e8-9671-4a0fc619827f","tipo":"ADULTO_CU","recibidaEn":"2026-11-04T12:00:00Z","avisoVersion":"LAB_1","aceptaCanalDigital":true,"datos":{"campos":{"motivo":"vulneracion","adult-region":"13","adult-commune":"\00d1u\00f1oa","adult-address":"Avenida Irarr\00e1zaval 1234, depto. 5","email":"maria.jose@example.cl","telefono":"+56912345678","relato":"Relato sint\00e9tico con tildes: ni\00f1o, se\00f1al, acci\00f3n.","riesgo":"si","relacion":"madre","identifica-responsables":"true"},"personas":[{"personaId":"afectado-1","detalles":{"comuna":"\00d1u\00f1oa","region":"13"}},{"personaId":"relacionado-1","detalles":{"relacion":"Familiar"}},{"personaId":"senalado-1","detalles":{"alias":"El vecino"}}],"instituciones":[{"tipo":"Establecimiento educativo","region":"13","comuna":"\00d1u\00f1oa","nombre":"Escuela B\00e1sica Sint\00e9tica"}],"adjuntos":[]},"personas":[{"id":"presentador-adulto","rol":"PRESENTADOR","runNumero":"12345678","runDv":"5","modulo11":false,"autenticada":true,"estadoConsulta":"NO_HABILITADO","consultadaEn":null,"consultaRef":null,"atributos":{"runNumero":{"valor":"12345678","codigo":null,"fuente":"CLAVE_UNICA_USERINFO","nivel":"IDENTIDAD_AUTENTICADA","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"},"runDv":{"valor":"5","codigo":null,"fuente":"CLAVE_UNICA_USERINFO","nivel":"IDENTIDAD_AUTENTICADA","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"},"nombres":{"valor":"Mar\00eda Jos\00e9 De los \00c1ngeles","codigo":null,"fuente":"CLAVE_UNICA_USERINFO","nivel":"IDENTIDAD_AUTENTICADA","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"},"apellidos":{"valor":"Del R\00edo Gonz\00e1lez","codigo":null,"fuente":"CLAVE_UNICA_USERINFO","nivel":"IDENTIDAD_AUTENTICADA","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"},"primerNombre":{"valor":"Mar\00eda Jos\00e9","codigo":null,"fuente":"CLAVE_UNICA_USERINFO","nivel":"IDENTIDAD_AUTENTICADA","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"},"segundoNombre":{"valor":"De los \00c1ngeles","codigo":null,"fuente":"CLAVE_UNICA_USERINFO","nivel":"IDENTIDAD_AUTENTICADA","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"}},"discrepancias":{}},{"id":"afectado-1","rol":"AFECTADO_NNA","runNumero":null,"runDv":null,"modulo11":false,"autenticada":false,"estadoConsulta":"NO_HABILITADO","consultadaEn":null,"consultaRef":null,"atributos":{"primerNombre":{"valor":"Tom\00e1s","codigo":null,"fuente":"DECLARACION_CIUDADANA","nivel":"DECLARADO_NO_CONTRASTADO","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"},"edad":{"valor":"9","codigo":null,"fuente":"DECLARACION_CIUDADANA","nivel":"DECLARADO_NO_CONTRASTADO","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"}},"discrepancias":{}},{"id":"relacionado-1","rol":"ADULTO_RELACIONADO","runNumero":null,"runDv":null,"modulo11":false,"autenticada":false,"estadoConsulta":"NO_HABILITADO","consultadaEn":null,"consultaRef":null,"atributos":{"primerNombre":{"valor":"Rosa","codigo":null,"fuente":"DECLARACION_CIUDADANA","nivel":"DECLARADO_NO_CONTRASTADO","observadoEn":"2026-11-04T12:00:00Z","evidencia":"50000000-0000-4000-8000-000000000001"}},"discrepancias":{}},{"id":"senalado-1","rol":"PERSONA_SENALADA","runNumero":null,"runDv":null,"modulo11":false,"autenticada":false,"estadoConsulta":"NO_HABILITADO","consultadaEn":null,"consultaRef":null,"atributos":{},"discrepancias":{}}],"adjuntos":[]},"sha256":"bdeeb3a14421f45b2291ac58efeb07bf230d4ecfa711a204a63f827e23ec06af","huellaProcedencia":"380e9ca0e42b7f953bedb5e5d128b8243e15a67288dd3bf052db7ea5933e1183","custodia":{"algoritmo":"Ed25519","keyId":"PRUEBA_SIGER","payload":"eyJ2ZXJzaW9uIjoiMS4wIiwiaWQiOiIzNDMzZDNiYi0zNjE5LTQ4NmQtOWFlMi1iOTUyYjcxZWUzYzkiLCJyZWNpYmlkYUVuIjoiMjAyNi0xMS0wNFQxMjowMDowMFoiLCJ0aXBvUmVjZXBjaW9uIjoiQ1VTVE9ESUFfUFVCTElDQSIsImh1ZWxsYUNvbnRlbmlkbyI6ImJkZWViM2ExNDQyMWY0NWIyMjkxYWM1OGVmZWIwN2JmMjMwZDRlY2ZhNzExYTIwNGE2M2Y4MjdlMjNlYzA2YWYiLCJodWVsbGFQcm9jZWRlbmNpYSI6IjM4MGU5Y2EwZTQyYjdmOTUzYmVkYjVlNWQxMjhiODI0M2UxNWE2NzI4OGRkM2JmMDUyZGI3ZWE1OTMzZTExODMiLCJlbWlzb3IiOiJGT1JNVUxBUklPX1BVQkxJQ09fREROIiwiYXZpc29WZXJzaW9uIjoiTEFCXzEifQ","firma":"VkdX957pmFia2iCtzcl-MAtHx3N_MfeqNA80vhPRPd85FARrGk5Fcw4Cm-nefAliP7NSrv1gSJ3lAPsxqA8_DA"}}', '3caf390c806820ec8a4e8d3da14a661a89895045db949c67de1320ad07cd79fb', 4462, 1795805923583, 2);
CREATE MEMORY TABLE "public"."external_person"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 11) DEFAULT ON NULL NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "person_key" CHARACTER VARYING(64) NOT NULL,
    "person_index" INTEGER NOT NULL,
    "person_role" CHARACTER VARYING(24) NOT NULL,
    "authenticated" BOOLEAN NOT NULL,
    "given_names" CHARACTER VARYING,
    "given_names_source" CHARACTER VARYING(32),
    "surnames" CHARACTER VARYING,
    "surnames_source" CHARACTER VARYING(32),
    "run_number" CHARACTER VARYING(9),
    "run_dv" CHARACTER(1),
    "run_source" CHARACTER VARYING(32),
    "run_valid" BOOLEAN,
    "identification_type" CHARACTER VARYING(40),
    "foreign_document_type" CHARACTER VARYING(255),
    "foreign_document_number" CHARACTER VARYING(255),
    "birth_date" CHARACTER(10),
    "birth_date_source" CHARACTER VARYING(32),
    "age_at_intake" INTEGER,
    "declared_age" INTEGER,
    "registry_state" CHARACTER VARYING(24),
    "registry_checked_ms" BIGINT
);
ALTER TABLE "public"."external_person" ADD CONSTRAINT "public"."CONSTRAINT_1CA" PRIMARY KEY("id");
-- 10 +/- SELECT COUNT(*) FROM public.external_person;
INSERT INTO "public"."external_person" VALUES
(1, 6, 'presentador-adulto', 0, 'PRESENTADOR', TRUE, U&'Mar\00eda Jos\00e9 De los \00c1ngeles', 'CLAVE_UNICA_USERINFO', U&'Del R\00edo Gonz\00e1lez', 'CLAVE_UNICA_USERINFO', '12345678', '5', 'CLAVE_UNICA_USERINFO', TRUE, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'NO_HABILITADO', NULL),
(2, 6, 'afectado-1', 1, 'AFECTADO_NNA', FALSE, U&'Tom\00e1s', 'DECLARACION_CIUDADANA', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 9, 'NO_HABILITADO', NULL),
(3, 6, 'relacionado-1', 2, 'ADULTO_RELACIONADO', FALSE, 'Rosa', 'DECLARACION_CIUDADANA', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'NO_HABILITADO', NULL),
(4, 6, 'senalado-1', 3, 'PERSONA_SENALADA', FALSE, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'NO_HABILITADO', NULL),
(5, 7, 'presentador-nna', 0, 'PRESENTADOR', FALSE, 'NOMBRE SINTETICO', 'SRCEI', 'APELLIDO SINTETICO', 'SRCEI', '99999999', '9', 'SRCEI', TRUE, NULL, NULL, NULL, '2012-03-04', 'SRCEI', 14, NULL, 'PARCIAL', 1794744000000),
(6, 8, 'presentador-nna', 0, 'PRESENTADOR', FALSE, 'NOMBRE SINTETICO', 'SRCEI', 'APELLIDO SINTETICO', 'SRCEI', '99999999', '9', 'SRCEI', TRUE, NULL, NULL, NULL, '2012-03-04', 'SRCEI', 14, NULL, 'PARCIAL', 1794744000000),
(7, 18, 'presentador-adulto', 0, 'PRESENTADOR', TRUE, U&'Mar\00eda Jos\00e9 De los \00c1ngeles', 'CLAVE_UNICA_USERINFO', U&'Del R\00edo Gonz\00e1lez', 'CLAVE_UNICA_USERINFO', '12345678', '5', 'CLAVE_UNICA_USERINFO', TRUE, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'NO_HABILITADO', NULL),
(8, 18, 'afectado-1', 1, 'AFECTADO_NNA', FALSE, U&'Tom\00e1s', 'DECLARACION_CIUDADANA', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 9, 'NO_HABILITADO', NULL),
(9, 18, 'relacionado-1', 2, 'ADULTO_RELACIONADO', FALSE, 'Rosa', 'DECLARACION_CIUDADANA', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'NO_HABILITADO', NULL),
(10, 18, 'senalado-1', 3, 'PERSONA_SENALADA', FALSE, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'NO_HABILITADO', NULL);
CREATE MEMORY TABLE "public"."external_attribute"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 29) DEFAULT ON NULL NOT NULL,
    "person_id" BIGINT NOT NULL,
    "attr_key" CHARACTER VARYING(255) NOT NULL,
    "discrepancy" BOOLEAN NOT NULL,
    "value_text" CHARACTER VARYING,
    "code_text" CHARACTER VARYING(255),
    "meaning" CHARACTER VARYING(120),
    "source" CHARACTER VARYING(32) NOT NULL,
    "level_code" CHARACTER VARYING(64) NOT NULL,
    "observed_ms" BIGINT NOT NULL,
    "evidence" CHARACTER VARYING(36) NOT NULL
);
ALTER TABLE "public"."external_attribute" ADD CONSTRAINT "public"."CONSTRAINT_7A" PRIMARY KEY("id");
-- 28 +/- SELECT COUNT(*) FROM public.external_attribute;
INSERT INTO "public"."external_attribute" VALUES
(1, 1, 'runNumero', FALSE, '12345678', NULL, NULL, 'CLAVE_UNICA_USERINFO', 'IDENTIDAD_AUTENTICADA', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(2, 1, 'runDv', FALSE, '5', NULL, NULL, 'CLAVE_UNICA_USERINFO', 'IDENTIDAD_AUTENTICADA', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(3, 1, 'nombres', FALSE, U&'Mar\00eda Jos\00e9 De los \00c1ngeles', NULL, NULL, 'CLAVE_UNICA_USERINFO', 'IDENTIDAD_AUTENTICADA', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(4, 1, 'apellidos', FALSE, U&'Del R\00edo Gonz\00e1lez', NULL, NULL, 'CLAVE_UNICA_USERINFO', 'IDENTIDAD_AUTENTICADA', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(5, 1, 'primerNombre', FALSE, U&'Mar\00eda Jos\00e9', NULL, NULL, 'CLAVE_UNICA_USERINFO', 'IDENTIDAD_AUTENTICADA', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(6, 1, 'segundoNombre', FALSE, U&'De los \00c1ngeles', NULL, NULL, 'CLAVE_UNICA_USERINFO', 'IDENTIDAD_AUTENTICADA', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(7, 2, 'primerNombre', FALSE, U&'Tom\00e1s', NULL, NULL, 'DECLARACION_CIUDADANA', 'DECLARADO_NO_CONTRASTADO', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(8, 2, 'edad', FALSE, '9', NULL, NULL, 'DECLARACION_CIUDADANA', 'DECLARADO_NO_CONTRASTADO', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(9, 3, 'primerNombre', FALSE, 'Rosa', NULL, NULL, 'DECLARACION_CIUDADANA', 'DECLARADO_NO_CONTRASTADO', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(10, 5, 'primerApellido', FALSE, 'APELLIDO SINTETICO', NULL, NULL, 'SRCEI', 'CONTRASTADO_EN_FUENTE', 1794744000000, '66666666-6666-4666-8666-666666666666'),
(11, 5, 'fechaNacimiento', FALSE, '2012-03-04', NULL, NULL, 'SRCEI', 'CONTRASTADO_EN_FUENTE', 1794744000000, '66666666-6666-4666-8666-666666666666'),
(12, 5, 'runDv', FALSE, '9', NULL, NULL, 'SRCEI', 'CONTRASTADO_EN_FUENTE', 1794744000000, '66666666-6666-4666-8666-666666666666'),
(13, 5, 'runNumero', FALSE, '99999999', NULL, NULL, 'SRCEI', 'CONTRASTADO_EN_FUENTE', 1794744000000, '66666666-6666-4666-8666-666666666666'),
(14, 5, 'nombres', FALSE, 'NOMBRE SINTETICO', NULL, NULL, 'SRCEI', 'CONTRASTADO_EN_FUENTE', 1794744000000, '66666666-6666-4666-8666-666666666666'),
(15, 6, 'primerApellido', FALSE, 'APELLIDO SINTETICO', NULL, NULL, 'SRCEI', 'CONTRASTADO_EN_FUENTE', 1794744000000, '66666666-6666-4666-8666-666666666666'),
(16, 6, 'fechaNacimiento', FALSE, '2012-03-04', NULL, NULL, 'SRCEI', 'CONTRASTADO_EN_FUENTE', 1794744000000, '66666666-6666-4666-8666-666666666666'),
(17, 6, 'runDv', FALSE, '9', NULL, NULL, 'SRCEI', 'CONTRASTADO_EN_FUENTE', 1794744000000, '66666666-6666-4666-8666-666666666666'),
(18, 6, 'runNumero', FALSE, '99999999', NULL, NULL, 'SRCEI', 'CONTRASTADO_EN_FUENTE', 1794744000000, '66666666-6666-4666-8666-666666666666'),
(19, 6, 'nombres', FALSE, 'NOMBRE SINTETICO', NULL, NULL, 'SRCEI', 'CONTRASTADO_EN_FUENTE', 1794744000000, '66666666-6666-4666-8666-666666666666'),
(20, 7, 'runNumero', FALSE, '12345678', NULL, NULL, 'CLAVE_UNICA_USERINFO', 'IDENTIDAD_AUTENTICADA', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(21, 7, 'runDv', FALSE, '5', NULL, NULL, 'CLAVE_UNICA_USERINFO', 'IDENTIDAD_AUTENTICADA', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(22, 7, 'nombres', FALSE, U&'Mar\00eda Jos\00e9 De los \00c1ngeles', NULL, NULL, 'CLAVE_UNICA_USERINFO', 'IDENTIDAD_AUTENTICADA', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(23, 7, 'apellidos', FALSE, U&'Del R\00edo Gonz\00e1lez', NULL, NULL, 'CLAVE_UNICA_USERINFO', 'IDENTIDAD_AUTENTICADA', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(24, 7, 'primerNombre', FALSE, U&'Mar\00eda Jos\00e9', NULL, NULL, 'CLAVE_UNICA_USERINFO', 'IDENTIDAD_AUTENTICADA', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(25, 7, 'segundoNombre', FALSE, U&'De los \00c1ngeles', NULL, NULL, 'CLAVE_UNICA_USERINFO', 'IDENTIDAD_AUTENTICADA', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(26, 8, 'primerNombre', FALSE, U&'Tom\00e1s', NULL, NULL, 'DECLARACION_CIUDADANA', 'DECLARADO_NO_CONTRASTADO', 1793793600000, '50000000-0000-4000-8000-000000000001');
INSERT INTO "public"."external_attribute" VALUES
(27, 8, 'edad', FALSE, '9', NULL, NULL, 'DECLARACION_CIUDADANA', 'DECLARADO_NO_CONTRASTADO', 1793793600000, '50000000-0000-4000-8000-000000000001'),
(28, 9, 'primerNombre', FALSE, 'Rosa', NULL, NULL, 'DECLARACION_CIUDADANA', 'DECLARADO_NO_CONTRASTADO', 1793793600000, '50000000-0000-4000-8000-000000000001');
CREATE INDEX "public"."ix_external_attribute_person" ON "public"."external_attribute"("person_id" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."external_field"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 43) DEFAULT ON NULL NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "field_group" CHARACTER VARYING(12) NOT NULL,
    "person_key" CHARACTER VARYING(64),
    "item" INTEGER NOT NULL,
    "field_key" CHARACTER VARYING(255) NOT NULL,
    "value_text" CHARACTER VARYING
);
ALTER TABLE "public"."external_field" ADD CONSTRAINT "public"."CONSTRAINT_F8" PRIMARY KEY("id");
-- 42 +/- SELECT COUNT(*) FROM public.external_field;
INSERT INTO "public"."external_field" VALUES
(1, 6, 'CAMPO', NULL, 0, 'motivo', 'vulneracion'),
(2, 6, 'CAMPO', NULL, 0, 'adult-region', '13'),
(3, 6, 'CAMPO', NULL, 0, 'adult-commune', U&'\00d1u\00f1oa'),
(4, 6, 'CAMPO', NULL, 0, 'adult-address', U&'Avenida Irarr\00e1zaval 1234, depto. 5'),
(5, 6, 'CAMPO', NULL, 0, 'email', 'maria.jose@example.cl'),
(6, 6, 'CAMPO', NULL, 0, 'telefono', '+56912345678'),
(7, 6, 'CAMPO', NULL, 0, 'relato', U&'Relato sint\00e9tico con tildes: ni\00f1o, se\00f1al, acci\00f3n.'),
(8, 6, 'CAMPO', NULL, 0, 'riesgo', 'si'),
(9, 6, 'CAMPO', NULL, 0, 'relacion', 'madre'),
(10, 6, 'CAMPO', NULL, 0, 'identifica-responsables', 'true'),
(11, 6, 'PERSONA', 'afectado-1', 0, 'region', '13'),
(12, 6, 'PERSONA', 'afectado-1', 0, 'comuna', U&'\00d1u\00f1oa'),
(13, 6, 'PERSONA', 'relacionado-1', 1, 'relacion', 'Familiar'),
(14, 6, 'PERSONA', 'senalado-1', 2, 'alias', 'El vecino'),
(15, 6, 'INSTITUCION', NULL, 0, 'nombre', U&'Escuela B\00e1sica Sint\00e9tica'),
(16, 6, 'INSTITUCION', NULL, 0, 'comuna', U&'\00d1u\00f1oa'),
(17, 6, 'INSTITUCION', NULL, 0, 'region', '13'),
(18, 6, 'INSTITUCION', NULL, 0, 'tipo', 'Establecimiento educativo'),
(19, 7, 'CAMPO', NULL, 0, 'nna-primary-method', 'unsafe'),
(20, 7, 'CAMPO', NULL, 0, 'nna-region', '13'),
(21, 7, 'CAMPO', NULL, 0, 'nna-story', U&'Relato exclusivamente sint\00e9tico para el contrato NNA SRCeI.'),
(22, 8, 'CAMPO', NULL, 0, 'nna-primary-method', 'unsafe'),
(23, 8, 'CAMPO', NULL, 0, 'nna-region', '13'),
(24, 8, 'CAMPO', NULL, 0, 'nna-story', U&'Relato exclusivamente sint\00e9tico para el contrato NNA SRCeI.'),
(25, 18, 'CAMPO', NULL, 0, 'motivo', 'vulneracion'),
(26, 18, 'CAMPO', NULL, 0, 'adult-region', '13'),
(27, 18, 'CAMPO', NULL, 0, 'adult-commune', U&'\00d1u\00f1oa'),
(28, 18, 'CAMPO', NULL, 0, 'adult-address', U&'Avenida Irarr\00e1zaval 1234, depto. 5'),
(29, 18, 'CAMPO', NULL, 0, 'email', 'maria.jose@example.cl'),
(30, 18, 'CAMPO', NULL, 0, 'telefono', '+56912345678'),
(31, 18, 'CAMPO', NULL, 0, 'relato', U&'Relato sint\00e9tico con tildes: ni\00f1o, se\00f1al, acci\00f3n.'),
(32, 18, 'CAMPO', NULL, 0, 'riesgo', 'si'),
(33, 18, 'CAMPO', NULL, 0, 'relacion', 'madre'),
(34, 18, 'CAMPO', NULL, 0, 'identifica-responsables', 'true'),
(35, 18, 'PERSONA', 'afectado-1', 0, 'comuna', U&'\00d1u\00f1oa'),
(36, 18, 'PERSONA', 'afectado-1', 0, 'region', '13'),
(37, 18, 'PERSONA', 'relacionado-1', 1, 'relacion', 'Familiar'),
(38, 18, 'PERSONA', 'senalado-1', 2, 'alias', 'El vecino'),
(39, 18, 'INSTITUCION', NULL, 0, 'tipo', 'Establecimiento educativo'),
(40, 18, 'INSTITUCION', NULL, 0, 'region', '13'),
(41, 18, 'INSTITUCION', NULL, 0, 'comuna', U&'\00d1u\00f1oa'),
(42, 18, 'INSTITUCION', NULL, 0, 'nombre', U&'Escuela B\00e1sica Sint\00e9tica');
CREATE INDEX "public"."ix_external_field_com" ON "public"."external_field"("communication_id" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."staff"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 21) DEFAULT ON NULL NOT NULL,
    "username" CHARACTER VARYING(64) NOT NULL,
    "name" CHARACTER VARYING(160) NOT NULL,
    "email" CHARACTER VARYING(200),
    "password_hash" CHARACTER VARYING(100) NOT NULL,
    "enabled" BOOLEAN DEFAULT TRUE NOT NULL,
    "must_change" BOOLEAN DEFAULT TRUE NOT NULL,
    "security_version" BIGINT DEFAULT 0 NOT NULL,
    "home_scope" BIGINT NOT NULL,
    "technical" BOOLEAN DEFAULT FALSE NOT NULL
);
ALTER TABLE "public"."staff" ADD CONSTRAINT "public"."CONSTRAINT_6" PRIMARY KEY("id");
-- 20 +/- SELECT COUNT(*) FROM public.staff;
INSERT INTO "public"."staff" VALUES
(1, 'distribucion', 'Distribuidor sintetico', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 4, FALSE),
(2, 'administrativo', 'Primera atencion sintetica', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 4, FALSE),
(3, 'profesional', 'Profesional Requerimientos sintetico', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 4, FALSE),
(4, 'coordinacion.requerimientos', 'Coordinadora de Requerimientos sintetica', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 4, FALSE),
(5, 'direccion.upr', 'Direccion UPR sintetica', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 1, FALSE),
(6, 'profesional.proteccion', 'Profesional Proteccion sintetico', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 7, FALSE),
(7, 'direccion.upd', 'Direccion UPD sintetica', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 2, FALSE),
(8, 'profesional.upd', 'Profesional Educacion UPD sintetico', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 8, FALSE),
(9, 'profesional.difusion', 'Profesional Difusion UPD sintetico', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 9, FALSE),
(10, 'direccion.uep', 'Direccion UEP sintetica', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 3, FALSE),
(11, 'profesional.uep', 'Profesional Estudios sintetico', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 11, FALSE),
(12, 'direccion.ugi', 'Direccion UGI sintetica', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 13, FALSE),
(13, 'profesional.ugi', 'Profesional UGI sintetico', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 13, FALSE),
(14, 'coordinacion.regional', 'Coordinacion regional sintetica', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 14, FALSE),
(15, 'profesional.regional', 'Profesional regional sintetico', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 14, FALSE),
(16, 'ti', 'Administracion tecnica sintetica', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 13, FALSE),
(17, 'auditoria', 'Auditoria de prueba', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 4, FALSE),
(18, 'gabinete', 'Consulta autorizada de prueba', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 4, FALSE),
(19, 'gestion.sintetica', 'Cuenta sintetica gestion.sintetica', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 13, FALSE),
(20, 'reportes.sinteticos', 'Cuenta sintetica reportes.sinteticos', NULL, 'volcado-sintetico-sin-acceso', TRUE, FALSE, 0, 13, FALSE);
CREATE MEMORY TABLE "public"."mail_attempt"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) DEFAULT ON NULL NOT NULL,
    "outbox_id" BIGINT NOT NULL,
    "claim_id" CHARACTER(36) NOT NULL,
    "address" CHARACTER VARYING(200) NOT NULL,
    "started_ms" BIGINT NOT NULL,
    "finished_ms" BIGINT,
    "state" CHARACTER VARYING(16) NOT NULL,
    "phase" CHARACTER VARYING(24) NOT NULL,
    "http_status" INTEGER,
    "request_id" CHARACTER VARYING(36)
);
ALTER TABLE "public"."mail_attempt" ADD CONSTRAINT "public"."CONSTRAINT_D4" PRIMARY KEY("id");
-- 0 +/- SELECT COUNT(*) FROM public.mail_attempt;
CREATE INDEX "public"."ix_mail_attempt_outbox" ON "public"."mail_attempt"("outbox_id" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."document_scan"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) DEFAULT ON NULL NOT NULL,
    "document_id" BIGINT NOT NULL,
    "sha256" CHARACTER(64) NOT NULL,
    "engine" CHARACTER VARYING(16) NOT NULL,
    "engine_version" CHARACTER VARYING(120),
    "verdict" CHARACTER VARYING(12) NOT NULL,
    "exit_code" INTEGER,
    "detail" CHARACTER VARYING(64),
    "requested_by" BIGINT NOT NULL,
    "started_ms" BIGINT NOT NULL,
    "finished_ms" BIGINT NOT NULL
);
ALTER TABLE "public"."document_scan" ADD CONSTRAINT "public"."CONSTRAINT_5E8" PRIMARY KEY("id");
-- 0 +/- SELECT COUNT(*) FROM public.document_scan;
CREATE INDEX "public"."ix_document_scan_document" ON "public"."document_scan"("document_id" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."solicitation"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 11) DEFAULT ON NULL NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "destination_scope" BIGINT NOT NULL,
    "state" CHARACTER VARYING(24) DEFAULT 'PENDIENTE' NOT NULL,
    "classification" CHARACTER VARYING(2000) NOT NULL,
    "created_by" BIGINT NOT NULL,
    "created_ms" BIGINT NOT NULL,
    "revision" BIGINT DEFAULT 0 NOT NULL,
    "requested_area_scope" BIGINT,
    "suggested_matter" CHARACTER VARYING(8)
);
ALTER TABLE "public"."solicitation" ADD CONSTRAINT "public"."CONSTRAINT_8" PRIMARY KEY("id");
-- 10 +/- SELECT COUNT(*) FROM public.solicitation;
INSERT INTO "public"."solicitation" VALUES
(1, 1, 4, 'ASUMIDA', 'Redistribucion', 1, 1795532157286, 4, 4, NULL),
(2, 2, 4, 'ASUMIDA', 'Ingreso propio', 4, 1795532157545, 0, NULL, NULL),
(3, 3, 4, 'DESCARTADA', 'Clasificacion sintetica', 1, 1795532157565, 1, 4, NULL),
(4, 11, 1, 'ASUMIDA', 'Competencia UPR', 1, 1795532157731, 1, NULL, NULL),
(5, 12, 1, 'ASUMIDA', 'Competencia UPR', 1, 1795532157777, 1, NULL, NULL),
(6, 15, 4, 'DESCARTADA', 'Clasificacion sintetica', 1, 1795532157919, 1, 4, NULL),
(7, 16, 2, 'PENDIENTE', 'Competencia UPD', 1, 1795532157943, 0, NULL, NULL),
(8, 18, 4, 'ASUMIDA', 'Distribucion sintetica', 1, 1795805923614, 1, 4, NULL),
(9, 19, 4, 'ASUMIDA', 'Distribucion sintetica', 1, 1795805923698, 1, 4, NULL),
(10, 20, 4, 'ASUMIDA', 'Distribucion sintetica', 1, 1795805923745, 1, 4, NULL);
CREATE INDEX "public"."ix_sol_queue" ON "public"."solicitation"("state" NULLS FIRST, "destination_scope" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."application_marker"(
    "code" CHARACTER VARYING(80) NOT NULL,
    "applied_ms" BIGINT NOT NULL,
    "amount" BIGINT
);
ALTER TABLE "public"."application_marker" ADD CONSTRAINT "public"."CONSTRAINT_20" PRIMARY KEY("code");
-- 9 +/- SELECT COUNT(*) FROM public.application_marker;
INSERT INTO "public"."application_marker" VALUES
('COORDINACION_REGIONAL_050', 1795532157221, NULL),
('DEMO_ORGANIZATION_030', 1795532157201, NULL),
('DIMENSIONES_V21', 1795805922966, NULL),
('MILESTONES_V14', 1795532157234, 0),
('PERSON_INDEX_V12B', 1795532157227, NULL),
('REGION_FICHA_V16', 1795532157239, NULL),
('RESULTADO_INMEDIATO_V20', 1795805922988, 0),
('RESUMEN_CAMBIOS_V18', 1795805922917, NULL),
('ROL_AUTORIDAD_SUPRIMIDO_050', 1795532157218, NULL);
CREATE MEMORY TABLE "public"."intake_record"(
    "communication_id" BIGINT NOT NULL,
    "data_json" CHARACTER VARYING NOT NULL,
    "updated_by" BIGINT NOT NULL,
    "updated_ms" BIGINT NOT NULL
);
ALTER TABLE "public"."intake_record" ADD CONSTRAINT "public"."CONSTRAINT_87A" PRIMARY KEY("communication_id");
-- 3 +/- SELECT COUNT(*) FROM public.intake_record;
INSERT INTO "public"."intake_record" VALUES
(19, U&'{"schema_version":"1.0","presenter":{"role":"REQUIRENTE","first_name":"Persona","middle_name":"","last_name":"Sintetica","second_last_name":"","legacy_name":"","identity_type":"RUN","run_number":"11111111","run_dv":"1","document_kind":"","document_number":"","legacy_document":"","nationality":"","legacy_nationality":"","birth_date":"","age_years":"","legacy_age":"","region_code":"13","commune":"\00d1u\00f1oa","address":"","email":"persona.sintetica@example.cl","mobile":"","other_contact":"","preferred_name":"","treatment":"","relationship_code":"","relationship_text":"","schooling":"","location_text":"","relationship_version":""},"persons":[],"contact_restrictions":"Contactar solo por correo electronico; no llamar al domicilio.","facts_region_code":"","facts_commune":"","facts_place":"","institutions_list":[]}', 4, 1795805923682),
(20, '{"schema_version":"1.0","presenter":{"role":"REQUIRENTE","first_name":"Otra","middle_name":"","last_name":"Persona","second_last_name":"","legacy_name":"","identity_type":"","run_number":"","run_dv":"","document_kind":"","document_number":"","legacy_document":"","nationality":"","legacy_nationality":"","birth_date":"","age_years":"","legacy_age":"","region_code":"","commune":"","address":"","email":"otra.persona@example.cl","mobile":"","other_contact":"","preferred_name":"","treatment":"","relationship_code":"","relationship_text":"","schooling":"","location_text":"","relationship_version":""},"persons":[],"contact_restrictions":"","facts_region_code":"","facts_commune":"","facts_place":"","institutions_list":[]}', 4, 1795805923732),
(21, '{"schema_version":"1.0","presenter":{},"persons":[],"contact_restrictions":"","facts_region_code":"","facts_commune":"","facts_place":"","institutions_list":[]}', 4, 1795805923801);
CREATE MEMORY TABLE "public"."intake_revision"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 4) DEFAULT ON NULL NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "revision" BIGINT NOT NULL,
    "author_id" BIGINT NOT NULL,
    "registered_ms" BIGINT NOT NULL,
    "reason" CHARACTER VARYING(2000) NOT NULL,
    "data_json" CHARACTER VARYING NOT NULL,
    "region_code" CHARACTER VARYING(4),
    "change_summary" CHARACTER VARYING
);
ALTER TABLE "public"."intake_revision" ADD CONSTRAINT "public"."CONSTRAINT_78" PRIMARY KEY("id");
-- 3 +/- SELECT COUNT(*) FROM public.intake_revision;
INSERT INTO "public"."intake_revision" VALUES
(1, 19, 0, 4, 1795805923682, 'Registro del ingreso', U&'{"schema_version":"1.0","presenter":{"role":"REQUIRENTE","first_name":"Persona","middle_name":"","last_name":"Sintetica","second_last_name":"","legacy_name":"","identity_type":"RUN","run_number":"11111111","run_dv":"1","document_kind":"","document_number":"","legacy_document":"","nationality":"","legacy_nationality":"","birth_date":"","age_years":"","legacy_age":"","region_code":"13","commune":"\00d1u\00f1oa","address":"","email":"persona.sintetica@example.cl","mobile":"","other_contact":"","preferred_name":"","treatment":"","relationship_code":"","relationship_text":"","schooling":"","location_text":"","relationship_version":""},"persons":[],"contact_restrictions":"Contactar solo por correo electronico; no llamar al domicilio.","facts_region_code":"","facts_commune":"","facts_place":"","institutions_list":[]}', '13', NULL),
(2, 20, 0, 4, 1795805923732, 'Registro del ingreso', '{"schema_version":"1.0","presenter":{"role":"REQUIRENTE","first_name":"Otra","middle_name":"","last_name":"Persona","second_last_name":"","legacy_name":"","identity_type":"","run_number":"","run_dv":"","document_kind":"","document_number":"","legacy_document":"","nationality":"","legacy_nationality":"","birth_date":"","age_years":"","legacy_age":"","region_code":"","commune":"","address":"","email":"otra.persona@example.cl","mobile":"","other_contact":"","preferred_name":"","treatment":"","relationship_code":"","relationship_text":"","schooling":"","location_text":"","relationship_version":""},"persons":[],"contact_restrictions":"","facts_region_code":"","facts_commune":"","facts_place":"","institutions_list":[]}', NULL, NULL),
(3, 21, 0, 4, 1795805923801, 'Registro del ingreso', '{"schema_version":"1.0","presenter":{},"persons":[],"contact_restrictions":"","facts_region_code":"","facts_commune":"","facts_place":"","institutions_list":[]}', NULL, NULL);
CREATE INDEX "public"."ix_intake_revisions" ON "public"."intake_revision"("communication_id" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."note_institution"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 2) DEFAULT ON NULL NOT NULL,
    "note_id" BIGINT NOT NULL,
    "sort" INTEGER NOT NULL,
    "name" CHARACTER VARYING(200) NOT NULL,
    "type_code" CHARACTER VARYING(40),
    "catalog_version_id" BIGINT,
    "role" CHARACTER VARYING(300),
    "region_code" CHARACTER VARYING(4),
    "commune" CHARACTER VARYING(100)
);
ALTER TABLE "public"."note_institution" ADD CONSTRAINT "public"."CONSTRAINT_65" PRIMARY KEY("id");
-- 1 +/- SELECT COUNT(*) FROM public.note_institution;
INSERT INTO "public"."note_institution" VALUES
(1, 3, 1, 'Escuela Basica Sintetica', 'ESTABLECIMIENTO_EDUCATIVO', 8, 'Destinataria del oficio', NULL, NULL);
CREATE INDEX "public"."ix_note_institution_note" ON "public"."note_institution"("note_id" NULLS FIRST, "sort" NULLS FIRST);
CREATE MEMORY TABLE "public"."stat_catalog_mapping"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) DEFAULT ON NULL NOT NULL,
    "from_version" BIGINT NOT NULL,
    "from_code" CHARACTER VARYING(40) NOT NULL,
    "to_version" BIGINT NOT NULL,
    "to_code" CHARACTER VARYING(40) NOT NULL,
    "loaded_by" BIGINT NOT NULL,
    "loaded_ms" BIGINT NOT NULL,
    "basis" CHARACTER VARYING NOT NULL
);
ALTER TABLE "public"."stat_catalog_mapping" ADD CONSTRAINT "public"."CONSTRAINT_71" PRIMARY KEY("id");
-- 0 +/- SELECT COUNT(*) FROM public.stat_catalog_mapping;
CREATE UNIQUE NULLS DISTINCT INDEX "public"."ux_stat_catalog_mapping" ON "public"."stat_catalog_mapping"("from_version" NULLS FIRST, "from_code" NULLS FIRST, "to_version" NULLS FIRST, "to_code" NULLS FIRST);
CREATE INDEX "public"."ix_stat_catalog_mapping_to" ON "public"."stat_catalog_mapping"("to_version" NULLS FIRST, "to_code" NULLS FIRST);
CREATE MEMORY TABLE "public"."attention_revision"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 5) DEFAULT ON NULL NOT NULL,
    "requirement_id" BIGINT NOT NULL,
    "requirement_revision" BIGINT NOT NULL,
    "author_id" BIGINT NOT NULL,
    "registered_ms" BIGINT NOT NULL,
    "reason" CHARACTER VARYING(2000) NOT NULL,
    "data_json" CHARACTER VARYING NOT NULL,
    "region_code" CHARACTER VARYING(4),
    "change_summary" CHARACTER VARYING
);
ALTER TABLE "public"."attention_revision" ADD CONSTRAINT "public"."CONSTRAINT_77" PRIMARY KEY("id");
-- 4 +/- SELECT COUNT(*) FROM public.attention_revision;
INSERT INTO "public"."attention_revision" VALUES
(1, 4, 0, 2, 1795532157601, 'Primer registro con antecedentes disponibles', '{"presenter_name":"","identity_document":"","identity_type":"","relationship":"","contact_email":"","contact_phone":"","presenter_domicile":"","region":"","commune":"","preferred_name":"","pronouns":"","supports":"","court":"","rit":"","ruc":"","guardian_name":"","nationality":"","group_description":"","approximate_count":"","motive":"Contacto sintetico NNA","story":"","request_text":"","observations":"","orientation":"","actions":"","commitments":"","institutions":"","safety_note":"","participation_note":"","child_message":"","identity_note":"","priority_note":"","safe_contact":"","judicial_status":"","guardian_status":"","participation":"NO","participation_mode":"","role_explained":"","priority":"","children_scope":"","modality":"NNA","schema_version":"1.0","course":"EN_ATENCION","people":[{"role":"NNA","preferred_name":"","nationality":"","relationship":"","name":"Nombre NNA sintetico","birth_date":"","schooling":"","location":"","identity_document":"","age":""}]}', NULL, NULL),
(2, 5, 0, 2, 1795532157699, 'Primer registro con antecedentes disponibles', '{"presenter_first_name":"Persona","presenter_middle_name":"","presenter_last_name":"Sintetica","presenter_second_last_name":"","presenter_run_number":"11111111","presenter_run_dv":"1","presenter_birth_date":"","presenter_document_kind":"","presenter_document_number":"","presenter_name":"Persona Sintetica","identity_document":"11.111.111-1","identity_type":"RUN","relationship":"","contact_email":"persona@ejemplo.cl","contact_phone":"+56900000000","presenter_domicile":"","region":"07","commune":"Talca","preferred_name":"","pronouns":"","supports":"","court":"","rit":"","ruc":"","guardian_name":"","nationality":"Chile","group_description":"","approximate_count":"","motive":"Consulta sintetica","story":"","request_text":"","observations":"","orientation":"","actions":"","commitments":"","institutions":"","safety_note":"","participation_note":"","child_message":"","identity_note":"","priority_note":"","safe_contact":"","judicial_status":"","guardian_status":"","participation":"","participation_mode":"","role_explained":"","priority":"","children_scope":"","modality":"GENERAL","schema_version":"1.1","course":"EN_ATENCION","people":[]}', '07', NULL),
(3, 5, 1, 2, 1795532157708, 'Complementacion sintetica', '{"presenter_first_name":"Persona","presenter_middle_name":"","presenter_last_name":"Sintetica","presenter_second_last_name":"","presenter_run_number":"11111111","presenter_run_dv":"1","presenter_birth_date":"","presenter_document_kind":"","presenter_document_number":"","presenter_name":"Persona Sintetica","identity_document":"11.111.111-1","identity_type":"RUN","relationship":"","contact_email":"persona@ejemplo.cl","contact_phone":"+56900000000","presenter_domicile":"","region":"07","commune":"Talca","preferred_name":"","pronouns":"","supports":"","court":"","rit":"","ruc":"","guardian_name":"","nationality":"Chile","group_description":"","approximate_count":"","motive":"Consulta sintetica","story":"","request_text":"","observations":"","orientation":"","actions":"","commitments":"","institutions":"","safety_note":"","participation_note":"","child_message":"","identity_note":"","priority_note":"","safe_contact":"","judicial_status":"","guardian_status":"","participation":"","participation_mode":"","role_explained":"","priority":"","children_scope":"","modality":"GENERAL","schema_version":"1.1","course":"EN_ATENCION","people":[{"role":"NNA","preferred_name":"","nationality":"","relationship":"","name":"Nino sintetico","birth_date":"","schooling":"","location":"","identity_document":"","age":"9"}]}', '07', '{"COMPLEMENTO":["people[0].legacy_age","people[0].legacy_name","people[0].role"],"RECTIFICACION":[]}'),
(4, 6, 0, 2, 1795532157718, 'Primer registro con antecedentes disponibles', '{"presenter_first_name":"","presenter_middle_name":"","presenter_last_name":"","presenter_second_last_name":"","presenter_run_number":"","presenter_run_dv":"","presenter_birth_date":"","presenter_document_kind":"","presenter_document_number":"","presenter_name":"","identity_document":"","identity_type":"","relationship":"","contact_email":"","contact_phone":"","presenter_domicile":"","region":"","commune":"","preferred_name":"","pronouns":"","supports":"","court":"","rit":"","ruc":"","guardian_name":"","nationality":"","group_description":"","approximate_count":"","motive":"Contacto sintetico NNA 1.1","story":"","request_text":"","observations":"","orientation":"","actions":"","commitments":"","institutions":"","safety_note":"","participation_note":"","child_message":"","identity_note":"","priority_note":"","safe_contact":"","judicial_status":"","guardian_status":"","participation":"NO","participation_mode":"","role_explained":"","priority":"","children_scope":"","modality":"NNA","schema_version":"1.1","course":"EN_ATENCION","people":[{"role":"NNA","preferred_name":"","nationality":"","relationship":"","name":"Nina sintetica","birth_date":"","schooling":"","location":"","identity_document":"","age":"doce"}]}', NULL, NULL);
CREATE INDEX "public"."ix_attention_revisions" ON "public"."attention_revision"("requirement_id" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."stat_event"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 11) DEFAULT ON NULL NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "record_type" CHARACTER VARYING(8) NOT NULL,
    "record_id" BIGINT NOT NULL,
    "variable" CHARACTER VARYING(32) NOT NULL,
    "event" CHARACTER VARYING(8) NOT NULL,
    "status" CHARACTER VARYING(20) NOT NULL,
    "catalog_version_id" BIGINT,
    "code" CHARACTER VARYING(40),
    "origin" CHARACTER VARYING(16) NOT NULL,
    "author_id" BIGINT NOT NULL,
    "recorded_ms" BIGINT NOT NULL,
    "basis" CHARACTER VARYING,
    "reopening_id" BIGINT
);
ALTER TABLE "public"."stat_event" ADD CONSTRAINT "public"."CONSTRAINT_E8" PRIMARY KEY("id");
-- 10 +/- SELECT COUNT(*) FROM public.stat_event;
INSERT INTO "public"."stat_event" VALUES
(1, 18, 'COM', 18, 'TIPO_SOLICITUD', 'SET', 'INFORMADO', 1, 'VULNERACION', 'FUNCIONARIO', 1, 1795805923609, NULL, NULL),
(2, 18, 'COM', 18, 'TIPO_REQUIRENTE', 'SET', 'INFORMADO', 2, 'PERSONA_NATURAL_ADULTA', 'FUNCIONARIO', 1, 1795805923609, NULL, NULL),
(3, 18, 'REQ', 9, 'DERECHO', 'ADD', 'INFORMADO', 4, '3.1.01', 'FUNCIONARIO', 4, 1795805923660, 'Clasificacion sintetica', NULL),
(4, 18, 'REQ', 9, 'CONTEXTO', 'ADD', 'INFORMADO', 5, 'FAMILIAR', 'FUNCIONARIO', 4, 1795805923660, 'Clasificacion sintetica', NULL),
(5, 19, 'COM', 19, 'TIPO_SOLICITUD', 'SET', 'INFORMADO', 1, 'VULNERACION', 'FUNCIONARIO', 1, 1795805923696, NULL, NULL),
(6, 19, 'COM', 19, 'TIPO_REQUIRENTE', 'SET', 'INFORMADO', 2, 'PERSONA_NATURAL_ADULTA', 'FUNCIONARIO', 1, 1795805923696, NULL, NULL),
(7, 20, 'COM', 20, 'TIPO_SOLICITUD', 'SET', 'INFORMADO', 1, 'VULNERACION', 'FUNCIONARIO', 1, 1795805923742, NULL, NULL),
(8, 20, 'COM', 20, 'TIPO_REQUIRENTE', 'SET', 'INFORMADO', 2, 'PERSONA_NATURAL_ADULTA', 'FUNCIONARIO', 1, 1795805923742, NULL, NULL),
(9, 20, 'REQ', 11, 'DERECHO', 'STATUS', 'SIN_INFORMACION', 4, NULL, 'FUNCIONARIO', 4, 1795805923776, 'Clasificacion sintetica', NULL),
(10, 20, 'REQ', 11, 'CONTEXTO', 'STATUS', 'SIN_INFORMACION', 5, NULL, 'FUNCIONARIO', 4, 1795805923776, 'Clasificacion sintetica', NULL);
CREATE INDEX "public"."ix_stat_event_com" ON "public"."stat_event"("communication_id" NULLS FIRST, "variable" NULLS FIRST, "id" NULLS FIRST);
CREATE INDEX "public"."ix_stat_event_record" ON "public"."stat_event"("record_type" NULLS FIRST, "record_id" NULLS FIRST, "variable" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."stat_catalog_item"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 148) DEFAULT ON NULL NOT NULL,
    "version_id" BIGINT NOT NULL,
    "code" CHARACTER VARYING(40) NOT NULL,
    "label" CHARACTER VARYING(300) NOT NULL,
    "parent_code" CHARACTER VARYING(40),
    "level" INTEGER NOT NULL,
    "sort" INTEGER NOT NULL,
    "selectable" BOOLEAN NOT NULL,
    "range_from" INTEGER,
    "range_to" INTEGER,
    "source_ref" CHARACTER VARYING(80),
    "description" CHARACTER VARYING(1000)
);
ALTER TABLE "public"."stat_catalog_item" ADD CONSTRAINT "public"."CONSTRAINT_153" PRIMARY KEY("id");
-- 147 +/- SELECT COUNT(*) FROM public.stat_catalog_item;
INSERT INTO "public"."stat_catalog_item" VALUES
(1, 1, 'VULNERACION', U&'Vulneraci\00f3n de derechos', NULL, 1, 1, TRUE, NULL, NULL, 'D29', U&'Incluye presuntas vulneraciones, sin exigir acreditaci\00f3n previa. Si existen antecedentes que permitan identificar una posible afectaci\00f3n de los derechos de un NNA, se clasifica como vulneraci\00f3n de derechos, aunque originalmente se haya presentado como reclamo.'),
(2, 1, 'CONSULTA_ORIENTACION', U&'Consulta u orientaci\00f3n', NULL, 1, 2, TRUE, NULL, NULL, 'D29', U&'Incluye solicitudes de informaci\00f3n, asesor\00eda u orientaci\00f3n.'),
(3, 1, 'PROMOCION', U&'Promoci\00f3n', NULL, 1, 3, TRUE, NULL, NULL, 'D29', U&'Incluye actividades de promoci\00f3n y difusi\00f3n de derechos.'),
(4, 1, 'RECLAMO_OTRA_INSTITUCION', U&'Reclamo sobre otra instituci\00f3n', NULL, 1, 4, TRUE, NULL, NULL, 'D29', U&'Reclamos que, seg\00fan los antecedentes disponibles, no constituyen una posible vulneraci\00f3n de derechos.'),
(5, 1, 'OTRO', 'Otro', NULL, 1, 5, TRUE, NULL, NULL, 'D29', U&'Categor\00eda residual, distinta de Sin informaci\00f3n.'),
(6, 2, 'PERSONA_NATURAL_ADULTA', 'Persona natural adulta', NULL, 1, 1, TRUE, NULL, NULL, 'D30', U&'Independiente de su relaci\00f3n con el NNA.'),
(7, 2, 'NNA', U&'Ni\00f1o, ni\00f1a o adolescente', NULL, 1, 2, TRUE, NULL, NULL, 'D30', 'Cuando el propio NNA formula el requerimiento.'),
(8, 2, 'PERSONA_JURIDICA_ORGANISMO', U&'Persona jur\00eddica u organismo', NULL, 1, 3, TRUE, NULL, NULL, 'D30', U&'Incluye instituciones p\00fablicas, privadas y organizaciones.'),
(9, 2, 'ACTUACION_DE_OFICIO', U&'Actuaci\00f3n de oficio', NULL, 1, 4, TRUE, NULL, NULL, 'D30', U&'Categor\00eda estad\00edstica especial para requerimientos iniciados institucionalmente, sin solicitante externo.'),
(10, 3, 'GRUPO_DIGITAL', 'Digital', NULL, 1, 1, FALSE, NULL, NULL, U&'DI-01; agrupaci\00f3n', NULL),
(11, 3, 'FORMULARIO_PUBLICO_ADULTO', U&'Formulario P\00fablico adulto', 'GRUPO_DIGITAL', 2, 2, TRUE, NULL, NULL, 'D31; canal FORMULARIO_WEB_PUBLICO', NULL),
(12, 3, 'FORMULARIO_PUBLICO_NNA', U&'Formulario P\00fablico NNA', 'GRUPO_DIGITAL', 2, 3, TRUE, NULL, NULL, 'D31; canal FORMULARIO_WEB_NNA', NULL),
(13, 3, 'CORREO', 'Correo', 'GRUPO_DIGITAL', 2, 4, TRUE, NULL, NULL, 'D31; canal CORREO', NULL),
(14, 3, 'REDES_SOCIALES', 'Redes sociales', 'GRUPO_DIGITAL', 2, 5, TRUE, NULL, NULL, 'D31; canal REDES_SOCIALES', NULL),
(15, 3, 'GRUPO_PRESENCIAL', 'Presencial', NULL, 1, 6, FALSE, NULL, NULL, U&'DI-01; agrupaci\00f3n', NULL),
(16, 3, 'PRESENCIAL', 'Presencial', 'GRUPO_PRESENCIAL', 2, 7, TRUE, NULL, NULL, 'D31; canal PRESENCIAL', NULL),
(17, 3, 'GRUPO_TELEFONICA', U&'Telef\00f3nica', NULL, 1, 8, FALSE, NULL, NULL, U&'DI-01; agrupaci\00f3n', NULL),
(18, 3, 'TELEFONO', U&'Tel\00e9fono', 'GRUPO_TELEFONICA', 2, 9, TRUE, NULL, NULL, 'D31; canal TELEFONO', NULL),
(19, 3, 'GRUPO_DOCUMENTAL', 'Documental', NULL, 1, 10, FALSE, NULL, NULL, U&'DI-01; agrupaci\00f3n', NULL),
(20, 3, 'OFICIO', 'Oficio', 'GRUPO_DOCUMENTAL', 2, 11, TRUE, NULL, NULL, 'D31; canal OFICIO', NULL),
(21, 3, 'GRUPO_INTERNA', 'Interna', NULL, 1, 12, FALSE, NULL, NULL, U&'DI-01; agrupaci\00f3n', NULL),
(22, 3, 'INTEGRANTE_DEFENSORIA', U&'Integrante de la Defensor\00eda', 'GRUPO_INTERNA', 2, 13, TRUE, NULL, NULL, 'D31; canal INTEGRANTE_DEFENSORIA', U&'Categor\00eda de ingreso interno, solo cuando representa el origen efectivo de la comunicaci\00f3n. No se usa por el solo hecho de que un funcionario registre un requerimiento recibido por correo, tel\00e9fono u otra v\00eda.'),
(23, 3, 'GRUPO_OTRA', 'Otra', NULL, 1, 14, FALSE, NULL, NULL, U&'DI-01; agrupaci\00f3n', NULL),
(24, 3, 'OTRO', 'Otro', 'GRUPO_OTRA', 2, 15, TRUE, NULL, NULL, 'D31; canal OTRO', NULL),
(25, 4, '3', 'Vida y supervivencia', NULL, 1, 1, FALSE, NULL, NULL, 'Tabla 2, p. 9; eje III, p. 11', NULL),
(26, 4, '3.1', 'Derecho a la Salud', '3', 2, 2, TRUE, NULL, NULL, 'Tabla 2, p. 9; 3.1, p. 11', NULL),
(27, 4, '3.1.01', 'Salud integral', '3.1', 3, 3, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.1.2, p. 14', NULL),
(28, 4, '3.1.02', 'Salud nutricional', '3.1', 3, 4, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.1.2, p. 14', NULL);
INSERT INTO "public"."stat_catalog_item" VALUES
(29, 4, '3.1.03', 'Salud sexual y reproductiva', '3.1', 3, 5, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.1.2, p. 14', NULL),
(30, 4, '3.1.04', 'Salud bucal', '3.1', 3, 6, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.1.2, p. 14', NULL),
(31, 4, '3.1.05', 'Salud mental', '3.1', 3, 7, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.1.2, p. 14', NULL),
(32, 4, '3.2', 'Derecho a una vivienda adecuada', '3', 2, 8, TRUE, NULL, NULL, 'Tabla 2, p. 9; 3.2, p. 15', NULL),
(33, 4, '3.2.01', 'Vivienda', '3.2', 3, 9, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.2.2, p. 17', NULL),
(34, 4, '3.2.02', 'Acceso a servicios', '3.2', 3, 10, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.2.2, p. 17', NULL),
(35, 4, '3.2.03', 'Seguridad del entorno', '3.2', 3, 11, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.2.2, p. 17', NULL),
(36, 4, '3.3', 'Derecho a un medioambiente sano', '3', 2, 12, TRUE, NULL, NULL, 'Tabla 2, p. 9; 3.3, p. 18', NULL),
(37, 4, '3.3.01', U&'Entornos sin contaminaci\00f3n', '3.3', 3, 13, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.3.2, p. 20', NULL),
(38, 4, '3.3.02', U&'Crisis clim\00e1tica', '3.3', 3, 14, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.3.2, p. 20', NULL),
(39, 4, '3.4', 'Derecho a un nivel de vida adecuado', '3', 2, 15, TRUE, NULL, NULL, 'Tabla 2, p. 9; 3.4, p. 21', NULL),
(40, 4, '3.4.01', 'Seguridad de ingresos y fin a la pobreza', '3.4', 3, 16, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.4.2, p. 24', NULL),
(41, 4, '3.4.02', 'Apoyo en situaciones de crisis', '3.4', 3, 17, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.4.2, p. 24', NULL),
(42, 4, '3.4.03', U&'Protecci\00f3n social', '3.4', 3, 18, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.4.2, p. 24', NULL),
(43, 4, '3.4.04', 'Seguridad alimentaria y vestuario', '3.4', 3, 19, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 3.4.2, p. 24', NULL),
(44, 4, '4', 'Desarrollo', NULL, 1, 20, FALSE, NULL, NULL, 'Tabla 2, p. 9; eje IV, p. 25', NULL),
(45, 4, '4.1', 'Derecho al desarrollo integral', '4', 2, 21, TRUE, NULL, NULL, 'Tabla 2, p. 9; 4.1, p. 25', NULL),
(46, 4, '4.1.01', 'Primera infancia', '4.1', 3, 22, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 4.1.2, p. 28', NULL),
(47, 4, '4.1.02', 'Infancia', '4.1', 3, 23, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 4.1.2, p. 28', NULL),
(48, 4, '4.1.03', 'Adolescencia', '4.1', 3, 24, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 4.1.2, p. 28', NULL),
(49, 4, '4.2', 'Derecho al descanso, esparcimiento y a participar de la cultura y el arte', '4', 2, 25, TRUE, NULL, NULL, 'Tabla 2, p. 9; 4.2, p. 29', NULL),
(50, 4, '4.2.01', 'Descanso adecuado y tiempo libre', '4.2', 3, 26, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 4.2.2, p. 31', NULL),
(51, 4, '4.2.02', U&'Esparcimiento, juego y recreaci\00f3n', '4.2', 3, 27, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 4.2.2, p. 31', NULL),
(52, 4, '4.2.03', 'Culturas y las artes', '4.2', 3, 28, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 4.2.2, p. 31', NULL),
(53, 4, '4.3', U&'Derecho a la educaci\00f3n', '4', 2, 29, TRUE, NULL, NULL, 'Tabla 2, p. 9; 4.3, p. 32', NULL),
(54, 4, '4.3.01', U&'Acceso a la educaci\00f3n', '4.3', 3, 30, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 4.3.2, p. 34', NULL),
(55, 4, '4.3.02', U&'Calidad y pertinencia de la educaci\00f3n', '4.3', 3, 31, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 4.3.2, p. 34', NULL),
(56, 4, '4.4', U&'Derecho al uso y disfrute de su entorno p\00fablico', '4', 2, 32, TRUE, NULL, NULL, 'Tabla 2, p. 9; 4.4, p. 35', NULL),
(57, 4, '4.4.01', U&'Seguridad y protecci\00f3n de los entornos p\00fablicos', '4.4', 3, 33, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 4.4.2, p. 38', NULL),
(58, 4, '4.4.02', U&'Planificaci\00f3n y pertinencia de los entornos p\00fablicos', '4.4', 3, 34, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 4.4.2, p. 38', NULL),
(59, 4, '5', U&'Protecci\00f3n', NULL, 1, 35, FALSE, NULL, NULL, 'Tabla 2, p. 9; eje V, p. 39', NULL),
(60, 4, '5.1', 'Derecho a vivir en familia', '5', 2, 36, TRUE, NULL, NULL, 'Tabla 2, p. 9; 5.1, p. 39', NULL),
(61, 4, '5.1.01', U&'Cuidado y crianza de ni\00f1os, ni\00f1as y adolescentes', '5.1', 3, 37, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.1.2, p. 46', NULL);
INSERT INTO "public"."stat_catalog_item" VALUES
(62, 4, '5.1.02', 'Procesos que resguarden su derecho a vivir en familia', '5.1', 3, 38, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.1.2, p. 46', NULL),
(63, 4, '5.1.03', U&'Ni\00f1os, ni\00f1as y adolescentes separados de su entorno familiar', '5.1', 3, 39, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.1.2, p. 46', NULL),
(64, 4, '5.2', 'Derecho a estar protegido contra toda forma de violencia', '5', 2, 40, TRUE, NULL, NULL, 'Tabla 2, p. 9; 5.2, p. 47', NULL),
(65, 4, '5.2.01', 'Descuido o negligencia en el cuidado', '5.2', 3, 41, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.2.2, p. 53', NULL),
(66, 4, '5.2.02', U&'Violencia mental, psicol\00f3gica y emocional', '5.2', 3, 42, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.2.2, p. 53', NULL),
(67, 4, '5.2.03', U&'Violencia f\00edsica y corporal', '5.2', 3, 43, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.2.2, p. 53', NULL),
(68, 4, '5.2.04', 'Torturas y tratos crueles, inhumanos y degradantes', '5.2', 3, 44, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.2.2, p. 53', NULL),
(69, 4, '5.2.05', 'Violencia sexual', '5.2', 3, 45, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.2.2, p. 54', NULL),
(70, 4, '5.2.06', U&'Explotaci\00f3n sexual comercial infantil', '5.2', 3, 46, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.2.2, p. 54', NULL),
(71, 4, '5.2.07', U&'Explotaci\00f3n econ\00f3mica y trabajo infantil', '5.2', 3, 47, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.2.2, p. 54', NULL),
(72, 4, '5.2.08', U&'Ni\00f1os, ni\00f1as y adolescentes en contexto de conflictos armados', '5.2', 3, 48, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.2.2, p. 54', NULL),
(73, 4, '5.2.09', U&'Ni\00f1os, ni\00f1as y adolescentes con conducta delictiva', '5.2', 3, 49, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.2.2, p. 54', NULL),
(74, 4, '5.3', 'Derecho a un trato justo', '5', 2, 50, TRUE, NULL, NULL, 'Tabla 2, p. 9; 5.3, p. 55', NULL),
(75, 4, '5.3.01', 'Trato justo en procedimientos judiciales', '5.3', 3, 51, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.3.2, p. 59', NULL),
(76, 4, '5.3.02', 'Sistema de justicia penal adolescente', '5.3', 3, 52, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.3.2, p. 59', NULL),
(77, 4, '5.3.03', U&'Privaci\00f3n de libertad', '5.3', 3, 53, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.3.2, p. 59', NULL),
(78, 4, '5.4', U&'Derecho a la no discriminaci\00f3n', '5', 2, 54, TRUE, NULL, NULL, 'Tabla 2, p. 9; 5.4, p. 60', NULL),
(79, 4, '5.4.01', U&'Ni\00f1os, ni\00f1as y adolescentes con discapacidad', '5.4', 3, 55, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.4.2, p. 64', NULL),
(80, 4, '5.4.02', U&'Ni\00f1os, ni\00f1as y adolescentes migrantes', '5.4', 3, 56, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.4.2, p. 64', NULL),
(81, 4, '5.4.03', U&'Ni\00f1os, ni\00f1as y adolescentes de pueblos originarios y tribales', '5.4', 3, 57, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.4.2, p. 64', NULL),
(82, 4, '5.4.04', U&'Mujeres, ni\00f1as y adolescentes', '5.4', 3, 58, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.4.2, p. 64', NULL),
(83, 4, '5.4.05', U&'Ni\00f1os, ni\00f1as y adolescentes de la diversidad sexual', '5.4', 3, 59, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.4.2, p. 65', NULL),
(84, 4, '5.4.06', U&'Ni\00f1os, ni\00f1as y adolescentes en situaci\00f3n de calle', '5.4', 3, 60, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.4.2, p. 65', NULL),
(85, 4, '5.4.07', 'Adolescentes en el sistema de responsabilidad penal', '5.4', 3, 61, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.4.2, p. 65', NULL),
(86, 4, '5.4.08', U&'Ni\00f1os, ni\00f1as y adolescentes en protecci\00f3n y cuidado', '5.4', 3, 62, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.4.2, p. 65', NULL),
(87, 4, '5.5', 'Derecho a la identidad, a tener un nombre y nacionalidad', '5', 2, 63, TRUE, NULL, NULL, 'Tabla 2, p. 9; 5.5, p. 66', NULL),
(88, 4, '5.5.01', 'Identidad, nombre y nacionalidad', '5.5', 3, 64, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.5.2, p. 67', NULL),
(89, 4, '5.5.02', U&'Ni\00f1os, ni\00f1as y adolescentes migrantes', '5.5', 3, 65, TRUE, NULL, NULL, 'Tabla 2, p. 9; tabla 5.5.2, p. 67', NULL),
(90, 4, '6', U&'Participaci\00f3n', NULL, 1, 66, FALSE, NULL, NULL, 'Tabla 2, p. 10; eje VI, p. 68', NULL);
INSERT INTO "public"."stat_catalog_item" VALUES
(91, 4, '6.1', U&'Derecho a la libertad de pensamiento, conciencia y religi\00f3n', '6', 2, 67, TRUE, NULL, NULL, 'Tabla 2, p. 10; 6.1, p. 68', NULL),
(92, 4, '6.1.01', U&'Libertad de pensamiento, creencias y religi\00f3n a nivel social, digital y familiar', '6.1', 3, 68, TRUE, NULL, NULL, 'Tabla 2, p. 10; tabla 6.1.2, p. 71', NULL),
(93, 4, '6.1.02', U&'Libertad de pensamiento, creencias y religi\00f3n en espacios educativos y de responsabilidad del Estado', '6.1', 3, 69, TRUE, NULL, NULL, 'Tabla 2, p. 10; tabla 6.1.2, p. 71', NULL),
(94, 4, '6.1.03', U&'Libertad de pensamiento, creencias y religi\00f3n de ni\00f1os, ni\00f1as y adolescentes de pueblos o comunidades \00e9tnicas, religiosas, ling\00fc\00edsticas, extranjeras o ind\00edgenas', '6.1', 3, 70, TRUE, NULL, NULL, 'Tabla 2, p. 10; tabla 6.1.2, p. 71', NULL),
(95, 4, '6.2', U&'Derecho a la informaci\00f3n y libertad de expresi\00f3n', '6', 2, 71, TRUE, NULL, NULL, 'Tabla 2, p. 10; 6.2, p. 72', NULL),
(96, 4, '6.2.01', U&'Conocimiento de los derechos de ni\00f1os, ni\00f1as y adolescentes', '6.2', 3, 72, TRUE, NULL, NULL, 'Tabla 2, p. 10; tabla 6.2.2, p. 76', NULL),
(97, 4, '6.2.02', U&'Acceso a la informaci\00f3n en procedimientos judiciales y administrativos', '6.2', 3, 73, TRUE, NULL, NULL, 'Tabla 2, p. 10; tabla 6.2.2, p. 76', NULL),
(98, 4, '6.2.03', U&'Libertad de expresi\00f3n', '6.2', 3, 74, TRUE, NULL, NULL, 'Tabla 2, p. 10; tabla 6.2.2, p. 76', NULL),
(99, 4, '6.3', U&'Derecho a la libertad de asociaci\00f3n', '6', 2, 75, TRUE, NULL, NULL, 'Tabla 2, p. 10; 6.3, p. 77', NULL),
(100, 4, '6.3.01', U&'Libertad de asociaci\00f3n', '6.3', 3, 76, TRUE, NULL, NULL, 'Tabla 2, p. 10; tabla 6.3.2, p. 78', NULL),
(101, 4, '6.3.02', U&'Libertad para participar en reuniones y manifestaciones pac\00edficas', '6.3', 3, 77, TRUE, NULL, NULL, 'Tabla 2, p. 10; tabla 6.3.2, p. 78', NULL),
(102, 4, '6.4', 'Derecho a ser escuchados', '6', 2, 78, TRUE, NULL, NULL, 'Tabla 2, p. 10; 6.4, p. 79', NULL),
(103, 4, '6.4.01', U&'Derecho a ser escuchados en instancias de participaci\00f3n ciudadana y democr\00e1tica', '6.4', 3, 79, TRUE, NULL, NULL, 'Tabla 2, p. 10; tabla 6.4.2, p. 85', NULL),
(104, 4, '6.4.02', 'Derecho a ser escuchados en procesos administrativos y judiciales involucrados', '6.4', 3, 80, TRUE, NULL, NULL, 'Tabla 2, p. 10; tabla 6.4.2, p. 85', NULL),
(105, 4, '6.4.03', 'Derecho a ser escuchados a nivel familiar y social', '6.4', 3, 81, TRUE, NULL, NULL, 'Tabla 2, p. 10; tabla 6.4.2, p. 85', NULL),
(106, 5, 'FAMILIAR', 'Familiar', NULL, 1, 1, TRUE, NULL, NULL, 'DI-02', U&'Entorno familiar o de cuidado dom\00e9stico.'),
(107, 5, 'ESCOLAR', 'Escolar', NULL, 1, 2, TRUE, NULL, NULL, 'DI-02', 'Entorno educativo y convivencia escolar.'),
(108, 5, 'INSTITUCIONAL', 'Institucional', NULL, 1, 3, TRUE, NULL, NULL, 'DI-02', U&'Atenci\00f3n o intervenci\00f3n de instituciones.'),
(109, 5, 'SALUD', 'Salud', 'INSTITUCIONAL', 2, 4, TRUE, NULL, NULL, 'DI-02', 'Prestaciones y establecimientos sanitarios.'),
(110, 5, 'PROTECCION_ESPECIALIZADA', U&'Protecci\00f3n especializada', 'INSTITUCIONAL', 2, 5, TRUE, NULL, NULL, 'DI-02', 'Residencias, familias de acogida y programas especializados.'),
(111, 5, 'JUSTICIA_JUVENIL', 'Justicia juvenil', 'INSTITUCIONAL', 2, 6, TRUE, NULL, NULL, 'DI-02', 'Sistema de responsabilidad penal adolescente.'),
(112, 5, 'JUDICIAL', 'Judicial', 'INSTITUCIONAL', 2, 7, TRUE, NULL, NULL, 'DI-02', 'Procedimientos e intervenciones judiciales.'),
(113, 5, 'POLICIAL', 'Policial', 'INSTITUCIONAL', 2, 8, TRUE, NULL, NULL, 'DI-02', 'Intervenciones de instituciones policiales.'),
(114, 5, 'COMUNITARIO', 'Comunitario', NULL, 1, 9, TRUE, NULL, NULL, 'DI-02', U&'Barrio, comunidad y espacios p\00fablicos.'),
(115, 5, 'DIGITAL', 'Digital', NULL, 1, 10, TRUE, NULL, NULL, 'DI-02', 'Internet, redes sociales y plataformas digitales.'),
(116, 5, 'LABORAL', 'Laboral', NULL, 1, 11, TRUE, NULL, NULL, 'DI-02', 'Actividades laborales y relaciones de trabajo.'),
(117, 5, 'OTRO', 'Otro contexto', NULL, 1, 12, TRUE, NULL, NULL, 'DI-02', U&'Entornos no comprendidos en las categor\00edas anteriores.');
INSERT INTO "public"."stat_catalog_item" VALUES
(118, 6, 'ORIENTACION', U&'Orientaci\00f3n', NULL, 1, 1, TRUE, NULL, NULL, 'D36', NULL),
(119, 6, 'RESPUESTA_DIRECTA', 'Respuesta directa', NULL, 1, 2, TRUE, NULL, NULL, 'D36', NULL),
(120, 6, 'DERIVACION_EXTERNA', U&'Derivaci\00f3n externa', NULL, 1, 3, TRUE, NULL, NULL, 'D36', NULL),
(121, 6, 'OFICIO', 'Oficio', NULL, 1, 4, TRUE, NULL, NULL, 'D36', U&'Oficio cuyo prop\00f3sito no corresponde a otro tipo de actuaci\00f3n.'),
(122, 6, 'DENUNCIA_ADMINISTRATIVA', 'Denuncia administrativa', NULL, 1, 5, TRUE, NULL, NULL, 'D36', NULL),
(123, 6, 'DENUNCIA_PENAL', 'Denuncia penal', NULL, 1, 6, TRUE, NULL, NULL, 'D36', NULL),
(124, 6, 'MEDIDA_PROTECCION', U&'Solicitud de medida de protecci\00f3n', NULL, 1, 7, TRUE, NULL, NULL, 'D36; DI-03', U&'Distingue la actuaci\00f3n de la Defensor\00eda de una medida decretada por un tribunal.'),
(125, 6, 'QUERELLA', 'Querella', NULL, 1, 8, TRUE, NULL, NULL, 'D36', NULL),
(126, 6, 'ACCION_CONSTITUCIONAL', U&'Acci\00f3n constitucional', NULL, 1, 9, TRUE, NULL, NULL, 'D36', NULL),
(127, 6, 'AMICUS_CURIAE', 'Amicus curiae', NULL, 1, 10, TRUE, NULL, NULL, 'D36', NULL),
(128, 6, 'SOLICITUD_ANTECEDENTES', 'Solicitud de antecedentes', NULL, 1, 11, TRUE, NULL, NULL, 'DI-03', 'Una solicitud de antecedentes realizada mediante oficio no se cuenta como dos actuaciones por el solo hecho de usar ese medio.'),
(129, 6, 'COORDINACION_INTERINSTITUCIONAL', U&'Coordinaci\00f3n interinstitucional', NULL, 1, 12, TRUE, NULL, NULL, 'DI-03', NULL),
(130, 6, 'SEGUIMIENTO_REQUERIMIENTO', 'Seguimiento de requerimiento', NULL, 1, 13, TRUE, NULL, NULL, 'DI-03', NULL),
(131, 6, 'OTRA', U&'Otra actuaci\00f3n', NULL, 1, 14, TRUE, NULL, NULL, 'D36', NULL),
(132, 7, 'MADRE', 'Madre', NULL, 1, 1, TRUE, NULL, NULL, 'madre', NULL),
(133, 7, 'PADRE', 'Padre', NULL, 1, 2, TRUE, NULL, NULL, 'padre', NULL),
(134, 7, 'CUIDADOR', 'Cuidador o cuidadora', NULL, 1, 3, TRUE, NULL, NULL, 'cuidador', NULL),
(135, 7, 'OTRO_FAMILIAR', 'Otro familiar', NULL, 1, 4, TRUE, NULL, NULL, 'otro-familiar', NULL),
(136, 7, 'DOCENTE', 'Docente o profesional educativo', NULL, 1, 5, TRUE, NULL, NULL, 'docente', NULL),
(137, 7, 'SALUD', 'Profesional de salud', NULL, 1, 6, TRUE, NULL, NULL, 'salud', NULL),
(138, 7, 'TESTIGO', 'Persona testigo', NULL, 1, 7, TRUE, NULL, NULL, 'testigo', NULL),
(139, 7, 'ORGANIZACION', U&'Representante de una organizaci\00f3n', NULL, 1, 8, TRUE, NULL, NULL, 'organizacion', NULL),
(140, 7, 'PROPIO_NNA', 'El propio NNA', NULL, 1, 9, TRUE, NULL, NULL, 'exclusivo de SIGER (DI-03)', NULL),
(141, 7, 'REPRESENTANTE_LEGAL', 'Representante legal', NULL, 1, 10, TRUE, NULL, NULL, 'exclusivo de SIGER (DI-03)', NULL),
(142, 7, 'OTRO', 'Otro', NULL, 1, 11, TRUE, NULL, NULL, 'otro', NULL),
(143, 8, 'ESTABLECIMIENTO_EDUCATIVO', 'Establecimiento educativo', NULL, 1, 1, TRUE, NULL, NULL, 'Establecimiento educativo', NULL),
(144, 8, 'ORGANISMO_PUBLICO', U&'Organismo p\00fablico', NULL, 1, 2, TRUE, NULL, NULL, U&'Organismo p\00fablico', NULL),
(145, 8, 'EMPRESA_ENTIDAD_PRIVADA', 'Empresa o entidad privada', NULL, 1, 3, TRUE, NULL, NULL, 'Empresa o entidad privada', NULL),
(146, 8, 'FUNDACION_ONG', U&'Fundaci\00f3n u ONG', NULL, 1, 4, TRUE, NULL, NULL, U&'Fundaci\00f3n u ONG', NULL),
(147, 8, 'OTRA_INSTITUCION', U&'Otra instituci\00f3n', NULL, 1, 5, TRUE, NULL, NULL, U&'Otra instituci\00f3n', NULL);
CREATE UNIQUE NULLS DISTINCT INDEX "public"."ux_stat_catalog_item_code" ON "public"."stat_catalog_item"("version_id" NULLS FIRST, "code" NULLS FIRST);
CREATE MEMORY TABLE "public"."stat_declared_version"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 8) DEFAULT ON NULL NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "source" CHARACTER VARYING(12) NOT NULL,
    "source_id" BIGINT NOT NULL,
    "registered_ms" BIGINT NOT NULL,
    "requester_region" CHARACTER VARYING(4),
    "requester_commune" CHARACTER VARYING(120),
    "facts_region" CHARACTER VARYING(4),
    "facts_commune" CHARACTER VARYING(120)
);
ALTER TABLE "public"."stat_declared_version" ADD CONSTRAINT "public"."CONSTRAINT_3A" PRIMARY KEY("id");
-- 7 +/- SELECT COUNT(*) FROM public.stat_declared_version;
INSERT INTO "public"."stat_declared_version" VALUES
(1, 5, 'FICHA', 1, 1795532157601, NULL, NULL, NULL, NULL),
(2, 9, 'FICHA', 2, 1795532157699, '07', 'Talca', NULL, NULL),
(3, 9, 'FICHA', 3, 1795532157708, '07', 'Talca', NULL, NULL),
(4, 10, 'FICHA', 4, 1795532157718, NULL, NULL, NULL, NULL),
(5, 19, 'INGRESO', 1, 1795805923682, '13', U&'\00d1u\00f1oa', NULL, NULL),
(6, 20, 'INGRESO', 2, 1795805923732, NULL, NULL, NULL, NULL),
(7, 21, 'INGRESO', 3, 1795805923801, NULL, NULL, NULL, NULL);
CREATE INDEX "public"."ix_stat_declared_com_at" ON "public"."stat_declared_version"("communication_id" NULLS FIRST, "registered_ms" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."stat_declared_person"(
    "version_id" BIGINT NOT NULL,
    "person_order" INTEGER NOT NULL,
    "person_role" CHARACTER VARYING(24) NOT NULL,
    "region_code" CHARACTER VARYING(4),
    "commune" CHARACTER VARYING(120),
    "age_years" INTEGER,
    "age_origin" CHARACTER VARYING(16),
    "nationality" CHARACTER VARYING(200)
);
ALTER TABLE "public"."stat_declared_person" ADD CONSTRAINT "public"."CONSTRAINT_5A" PRIMARY KEY("version_id", "person_order");
-- 9 +/- SELECT COUNT(*) FROM public.stat_declared_person;
INSERT INTO "public"."stat_declared_person" VALUES
(1, 0, 'REQUIRENTE', NULL, NULL, NULL, NULL, NULL),
(1, 1, 'NNA', NULL, NULL, NULL, NULL, NULL),
(2, 0, 'REQUIRENTE', '07', 'Talca', NULL, NULL, 'Chile'),
(3, 0, 'REQUIRENTE', '07', 'Talca', NULL, NULL, 'Chile'),
(3, 1, 'NNA', NULL, NULL, NULL, NULL, NULL),
(4, 0, 'REQUIRENTE', NULL, NULL, NULL, NULL, NULL),
(4, 1, 'NNA', NULL, NULL, NULL, NULL, NULL),
(5, 0, 'REQUIRENTE', '13', U&'\00d1u\00f1oa', NULL, NULL, NULL),
(6, 0, 'REQUIRENTE', NULL, NULL, NULL, NULL, NULL);
CREATE MEMORY TABLE "public"."stat_cut"(
    "id" BIGINT NOT NULL,
    "code" CHARACTER VARYING(20) NOT NULL,
    "title" CHARACTER VARYING(200) NOT NULL,
    "scope_id" BIGINT,
    "period_type" CHARACTER VARYING(20) NOT NULL,
    "period_from" CHARACTER VARYING(10) NOT NULL,
    "period_to" CHARACTER VARYING(10) NOT NULL,
    "as_of_ms" BIGINT NOT NULL,
    "methodology_version" CHARACTER VARYING(10) NOT NULL,
    "catalog_versions" CHARACTER VARYING NOT NULL,
    "calendar_sha256" CHARACTER(64) NOT NULL,
    "dimensions_json" CHARACTER VARYING NOT NULL,
    "labels_json" CHARACTER VARYING NOT NULL,
    "counts_json" CHARACTER VARYING NOT NULL,
    "row_count" BIGINT NOT NULL,
    "rows_sha256" CHARACTER(64) NOT NULL,
    "metadata_sha256" CHARACTER(64) NOT NULL,
    "created_by" BIGINT NOT NULL,
    "created_ms" BIGINT NOT NULL,
    "basis" CHARACTER VARYING NOT NULL,
    "state" CHARACTER VARYING(12) NOT NULL,
    "annulled_by" BIGINT,
    "annulled_ms" BIGINT,
    "annul_basis" CHARACTER VARYING,
    "revision" BIGINT DEFAULT 0 NOT NULL
);
ALTER TABLE "public"."stat_cut" ADD CONSTRAINT "public"."CONSTRAINT_4E8" PRIMARY KEY("id");
-- 1 +/- SELECT COUNT(*) FROM public.stat_cut;
INSERT INTO "public"."stat_cut" VALUES
(1, 'CORTE-2026-001', 'Corte sintetico', NULL, 'PERSONALIZADO', '2026-11-01', '2026-11-26', 1795805923841, '1', '{"CARACTERIZACION":null,"CONTEXTO":5,"DERECHO":4,"GRUPO_PRIORITARIO":null,"RELACION":7,"ROL_ESTADO":null,"TIPO_ACTUACION":6,"TIPO_INSTITUCION":8,"TIPO_REQUIRENTE":2,"TIPO_SOLICITUD":1,"TRAMO_EDAD":null,"VIA_INGRESO":3}', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', '["PERIODO_DIA","PERIODO_MES","PERIODO_TRIMESTRE","PERIODO_SEMESTRE","PERIODO_ANIO","PERIODO_CUENTA_PUBLICA","TARDANZA","ESTADO_COM","ESTADO_SOL","ESTADO_REQ","CANAL","MECANISMO","VIA_INGRESO","VIA_AGRUPACION","TIPO_SOLICITUD","TIPO_REQUIRENTE","MODALIDAD","REGION_REQUIRENTE","COMUNA_REQUIRENTE","REGION_ORIGEN","REGION_NNA","COMUNA_NNA","REGION_HECHOS","COMUNA_HECHOS","AMBITO","SEDE_INICIAL","ADSCRIPCION_INICIAL","SEDE_VIGENTE","TERRITORIO_COBERTURA","REGION_SEDE","UNIDAD","NIVEL","MATERIA","COMPLEJIDAD","ORIGEN_REQ","RESULTADO_PRIMERA_ATENCION","DERECHO_EJE","DERECHO","DERECHO_SUBCATEGORIA","CONTEXTO","CARACTERIZACION","ROL_ESTADO","GRUPO_PRIORITARIO","TIPO_ACTUACION","ORIGEN_ACTUACION","TIPO_INSTITUCION","EDAD_NNA","TRAMO_EDAD_NNA","NACIONALIDAD_NNA","GRUPO_NACIONALIDAD_NNA","EDAD_REQUIRENTE","TRAMO_EDAD_REQUIRENTE","NACIONALIDAD_REQUIRENTE","GRUPO_NACIONALIDAD_REQUIRENTE","ESTADO_TIPO_SOLICITUD","ESTADO_TIPO_REQUIRENTE","ESTADO_VIA_INGRESO","ESTADO_DERECHO","ESTADO_CONTEXTO","ESTADO_CARACTERIZACION","ESTADO_ROL_ESTADO","ESTADO_GRUPO_PRIORITARIO","VARIABLE","ESTADO_VARIABLE"]', '{"scopes":[["REG-DEMO","Sede sintetica - no oficial"],["REG-PROY","Sede proyectada sintetica"],["UEP","Direccion UEP"],["UEP-EST","Estudios"],["UEP-OBS","Observatorio de Derechos"],["UGI","Gestion Institucional"],["UPD","Direccion UPD"],["UPD-DIF","Difusion"],["UPD-EDU","Educacion"],["UPD-PAR","Participacion"],["UPR","Direccion UPR"],["UPR-NCE","NNA bajo cuidado del Estado"],["UPR-PD","Proteccion de Derechos"],["UPR-REQ","Requerimientos / Atencion de Personas"],["UPR-RJ","Representacion Judicial"]],"units":[["AUD","Auditoria"],["DAI","Desarrollo y Analisis Institucional"],["DEF","Defensoria"],["GAB","Gabinete"],["REG","Sedes Regionales"],["UEP","Estudios Publicaciones y Estadisticas"],["UGI","Gestion Institucional"],["UPD","Promocion y Difusion de Derechos"],["UPR","Proteccion de Derechos y Representacion Judicial"]]}', '{"stat_cut_action":3,"stat_cut_action_institution":1,"stat_cut_case":5,"stat_cut_event":2,"stat_cut_person":5,"stat_cut_row":18,"stat_export_cut_folio":18,"stat_export_cut_origin":5}', 18, '66ad664565737a48802ba64f4d8dbeb9c6029eb1e94657189fec4869b376f1a7', 'b1ba0f64a03fdea214bcf7799362de786de48ed62649f80222a938363b425988', 19, 1795805923841, 'Volcado sintetico', 'VIGENTE', NULL, NULL, NULL, 0);
CREATE MEMORY TABLE "public"."stat_cut_row"(
    "cut_id" BIGINT NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "req_id" BIGINT,
    "received_ms" BIGINT NOT NULL,
    "created_ms" BIGINT NOT NULL,
    "req_ms" BIGINT,
    "com_state" CHARACTER VARYING(20) NOT NULL,
    "v_via_ingreso" BIGINT,
    "v_tipo_solicitud" BIGINT,
    "v_tipo_requirente" BIGINT,
    "p_tipo_solicitud" CHARACTER VARYING(40),
    "p_tipo_requirente" CHARACTER VARYING(40),
    "d_estado_com" CHARACTER VARYING(20) NOT NULL,
    "d_estado_sol" CHARACTER VARYING(20) NOT NULL,
    "d_estado_req" CHARACTER VARYING(20) NOT NULL,
    "d_canal" CHARACTER VARYING(40) NOT NULL,
    "d_mecanismo" CHARACTER VARYING(20) NOT NULL,
    "d_via_ingreso" CHARACTER VARYING(40) NOT NULL,
    "d_via_agrupacion" CHARACTER VARYING(40) NOT NULL,
    "d_tipo_solicitud" CHARACTER VARYING(40) NOT NULL,
    "d_tipo_requirente" CHARACTER VARYING(40) NOT NULL,
    "d_modalidad" CHARACTER VARYING(20) NOT NULL,
    "d_region_requirente" CHARACTER VARYING(40) NOT NULL,
    "d_comuna_requirente" CHARACTER VARYING(200) NOT NULL,
    "d_region_origen" CHARACTER VARYING(40) NOT NULL,
    "d_region_hechos" CHARACTER VARYING(40) NOT NULL,
    "d_comuna_hechos" CHARACTER VARYING(200) NOT NULL,
    "d_ambito" CHARACTER VARYING(48) NOT NULL,
    "d_sede_inicial" CHARACTER VARYING(48) NOT NULL,
    "d_adscripcion_inicial" CHARACTER VARYING(48) NOT NULL,
    "d_sede_vigente" CHARACTER VARYING(48) NOT NULL,
    "d_territorio_cobertura" CHARACTER VARYING(48) NOT NULL,
    "d_region_sede" CHARACTER VARYING(40) NOT NULL,
    "d_unidad" CHARACTER VARYING(24) NOT NULL,
    "d_nivel" CHARACTER VARYING(20) NOT NULL,
    "d_materia" CHARACTER VARYING(20) NOT NULL,
    "d_complejidad" CHARACTER VARYING(20) NOT NULL,
    "d_origen_req" CHARACTER VARYING(20) NOT NULL,
    "d_resultado_primera_atencion" CHARACTER VARYING(24) NOT NULL,
    "d_edad_requirente" BIGINT NOT NULL,
    "d_tramo_edad_requirente" CHARACTER VARYING(40) NOT NULL,
    "d_nacionalidad_requirente" CHARACTER VARYING(200) NOT NULL,
    "d_grupo_nacionalidad_requirente" CHARACTER VARYING(20) NOT NULL,
    "d_estado_tipo_solicitud" CHARACTER VARYING(20) NOT NULL,
    "d_estado_tipo_requirente" CHARACTER VARYING(20) NOT NULL,
    "d_estado_via_ingreso" CHARACTER VARYING(20) NOT NULL,
    "d_estado_derecho" CHARACTER VARYING(20) NOT NULL,
    "d_estado_contexto" CHARACTER VARYING(20) NOT NULL,
    "d_estado_caracterizacion" CHARACTER VARYING(20) NOT NULL,
    "d_estado_rol_estado" CHARACTER VARYING(20) NOT NULL,
    "d_estado_grupo_prioritario" CHARACTER VARYING(20) NOT NULL
);
ALTER TABLE "public"."stat_cut_row" ADD CONSTRAINT "public"."CONSTRAINT_7B" PRIMARY KEY("cut_id", "communication_id");
-- 18 +/- SELECT COUNT(*) FROM public.stat_cut_row;
INSERT INTO "public"."stat_cut_row" VALUES
(1, 1, 1, 1795531857253, 1795532157265, 1795532157328, 'ENVIADA', 3, NULL, NULL, NULL, NULL, 'ENVIADA', 'ASUMIDA', 'ABIERTO', 'TELEFONO', 'MANUAL', 'TELEFONO', 'GRUPO_TELEFONICA', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'UPR-REQ', 'OFICINA_NACIONAL', 'UPR-REQ', 'SIN_INFORMACION', 'NIVEL_CENTRAL', 'UPR', 'CENTRAL', 'NO_APLICA', 'BAJA', 'SOL', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 2, 2, 1795531857531, 1795532157533, 1795532157545, 'ENVIADA', 3, NULL, NULL, NULL, NULL, 'ENVIADA', 'ASUMIDA', 'ABIERTO', 'TELEFONO', 'MANUAL', 'TELEFONO', 'GRUPO_TELEFONICA', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'UPR-REQ', 'OFICINA_NACIONAL', 'UPR-REQ', 'SIN_INFORMACION', 'NIVEL_CENTRAL', 'UPR', 'CENTRAL', 'NO_APLICA', 'PENDIENTE', 'SOL', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 3, NULL, 1795531857553, 1795532157556, NULL, 'DUPLICADA', 3, NULL, NULL, NULL, NULL, 'DUPLICADA', 'DESCARTADA', 'NO_APLICA', 'TELEFONO', 'MANUAL', 'TELEFONO', 'GRUPO_TELEFONICA', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'UPR-REQ', 'OFICINA_NACIONAL', 'UPR-REQ', 'SIN_INFORMACION', 'NIVEL_CENTRAL', 'UPR', 'CENTRAL', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 4, 3, 1795531557576, 1795532157579, 1795532157581, 'ATENDIDA', 3, NULL, NULL, NULL, NULL, 'ATENDIDA', 'NO_APLICA', 'CERRADO', 'TELEFONO', 'ATENCION_DIRECTA', 'TELEFONO', 'GRUPO_TELEFONICA', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'UPR-REQ', 'OFICINA_NACIONAL', 'UPR-REQ', 'SIN_INFORMACION', 'NIVEL_CENTRAL', 'UPR', 'CENTRAL', 'NO_APLICA', 'PENDIENTE', 'ATENCION_DIRECTA', 'ORIENTACION', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 5, 4, 1795531557586, 1795532157591, 1795532157593, 'ATENDIDA', 3, NULL, NULL, NULL, NULL, 'ATENDIDA', 'NO_APLICA', 'CERRADO', 'PRESENCIAL', 'ATENCION_DIRECTA', 'PRESENCIAL', 'GRUPO_PRESENCIAL', 'PENDIENTE', 'PENDIENTE', 'NNA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'UPR-REQ', 'OFICINA_NACIONAL', 'UPR-REQ', 'SIN_INFORMACION', 'NIVEL_CENTRAL', 'UPR', 'CENTRAL', 'NO_APLICA', 'PENDIENTE', 'ATENCION_DIRECTA', 'ORIENTACION', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 6, NULL, 1793793600000, 1795532157647, NULL, 'NUEVA', 3, NULL, NULL, 'VULNERACION', 'PERSONA_NATURAL_ADULTA', 'NUEVA', 'NO_APLICA', 'NO_APLICA', 'FORMULARIO_WEB_PUBLICO', 'FORMULARIO', 'FORMULARIO_PUBLICO_ADULTO', 'GRUPO_DIGITAL', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', '13', U&'\00d1u\00f1oa', '13', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'SIN_ATENCION', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 7, NULL, 1794744000000, 1795532157669, NULL, 'NUEVA', 3, NULL, NULL, NULL, 'NNA', 'NUEVA', 'NO_APLICA', 'NO_APLICA', 'FORMULARIO_WEB_NNA', 'FORMULARIO', 'FORMULARIO_PUBLICO_NNA', 'GRUPO_DIGITAL', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', '13', 'SIN_INFORMACION', '13', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'SIN_ATENCION', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE');
INSERT INTO "public"."stat_cut_row" VALUES
(1, 8, NULL, 1794744000000, 1795532157683, NULL, 'NUEVA', 3, NULL, NULL, NULL, 'NNA', 'NUEVA', 'NO_APLICA', 'NO_APLICA', 'FORMULARIO_WEB_NNA', 'FORMULARIO', 'FORMULARIO_PUBLICO_NNA', 'GRUPO_DIGITAL', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', '13', 'SIN_INFORMACION', '13', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'SIN_ATENCION', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 9, 5, 1795531357691, 1795532157694, 1795532157696, 'ATENDIDA', 3, NULL, NULL, NULL, NULL, 'ATENDIDA', 'NO_APLICA', 'ABIERTO', 'PRESENCIAL', 'ATENCION_DIRECTA', 'PRESENCIAL', 'GRUPO_PRESENCIAL', 'PENDIENTE', 'PENDIENTE', 'GENERAL', '07', 'Talca', '07', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'UPR-REQ', 'OFICINA_NACIONAL', 'UPR-REQ', 'SIN_ATENCION', 'NIVEL_CENTRAL', 'UPR', 'CENTRAL', 'NO_APLICA', 'PENDIENTE', 'ATENCION_DIRECTA', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 10, 6, 1795531457711, 1795532157714, 1795532157715, 'ATENDIDA', 3, NULL, NULL, NULL, NULL, 'ATENDIDA', 'NO_APLICA', 'ABIERTO', 'PRESENCIAL', 'ATENCION_DIRECTA', 'PRESENCIAL', 'GRUPO_PRESENCIAL', 'PENDIENTE', 'PENDIENTE', 'NNA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'UPR-REQ', 'OFICINA_NACIONAL', 'UPR-REQ', 'SIN_INFORMACION', 'NIVEL_CENTRAL', 'UPR', 'CENTRAL', 'NO_APLICA', 'PENDIENTE', 'ATENCION_DIRECTA', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 11, 7, 1795531257721, 1795532157723, 1795532157740, 'ENVIADA', 3, NULL, NULL, NULL, NULL, 'ENVIADA', 'ASUMIDA', 'CERRADO', 'TELEFONO', 'MANUAL', 'TELEFONO', 'GRUPO_TELEFONICA', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPD', 'UPR', 'OFICINA_NACIONAL', 'UPD', 'SIN_INFORMACION', 'NIVEL_CENTRAL', 'UPD', 'CENTRAL', 'NO_APLICA', 'PENDIENTE', 'SOL', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 12, 8, 1795531257766, 1795532157768, 1795532157792, 'ENVIADA', 3, NULL, NULL, NULL, NULL, 'ENVIADA', 'ASUMIDA', 'ABIERTO', 'TELEFONO', 'MANUAL', 'TELEFONO', 'GRUPO_TELEFONICA', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR', 'UPR', 'OFICINA_NACIONAL', 'UPR', 'SIN_INFORMACION', 'NIVEL_CENTRAL', 'UPR', 'CENTRAL', 'NO_APLICA', 'MEDIANA', 'SOL', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 13, NULL, 1795531257840, 1795532157842, NULL, 'VINCULADA', 3, NULL, NULL, NULL, NULL, 'VINCULADA', 'NO_APLICA', 'NO_APLICA', 'TELEFONO', 'MANUAL', 'TELEFONO', 'GRUPO_TELEFONICA', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'SIN_INFORMACION', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 14, NULL, 1795531257865, 1795532157868, NULL, 'NUEVA', 3, NULL, NULL, NULL, NULL, 'NUEVA', 'NO_APLICA', 'NO_APLICA', 'TELEFONO', 'MANUAL', 'TELEFONO', 'GRUPO_TELEFONICA', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'SIN_INFORMACION', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE');
INSERT INTO "public"."stat_cut_row" VALUES
(1, 15, NULL, 1795531257901, 1795532157903, NULL, 'INVALIDA', 3, NULL, NULL, NULL, NULL, 'INVALIDA', 'DESCARTADA', 'NO_APLICA', 'TELEFONO', 'MANUAL', 'TELEFONO', 'GRUPO_TELEFONICA', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'UPR-REQ', 'OFICINA_NACIONAL', 'UPR-REQ', 'SIN_INFORMACION', 'NIVEL_CENTRAL', 'UPR', 'CENTRAL', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 16, NULL, 1795531257931, 1795532157933, NULL, 'ENVIADA', 3, NULL, NULL, NULL, NULL, 'ENVIADA', 'PENDIENTE', 'NO_APLICA', 'TELEFONO', 'MANUAL', 'TELEFONO', 'GRUPO_TELEFONICA', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPD', 'UPD', 'OFICINA_NACIONAL', 'UPD', 'SIN_INFORMACION', 'NIVEL_CENTRAL', 'UPD', 'CENTRAL', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 17, NULL, 1795531257949, 1795532157951, NULL, 'NUEVA', 3, NULL, NULL, NULL, NULL, 'NUEVA', 'NO_APLICA', 'NO_APLICA', 'TELEFONO', 'MANUAL', 'TELEFONO', 'GRUPO_TELEFONICA', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'SIN_INFORMACION', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', -2, 'NO_APLICA', 'NO_APLICA', 'NO_APLICA', 'PENDIENTE', 'PENDIENTE', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'),
(1, 18, 9, 1793793600000, 1795805923580, 1795805923629, 'ENVIADA', 3, 1, 2, 'VULNERACION', 'PERSONA_NATURAL_ADULTA', 'ENVIADA', 'ASUMIDA', 'ABIERTO', 'FORMULARIO_WEB_PUBLICO', 'FORMULARIO', 'FORMULARIO_PUBLICO_ADULTO', 'GRUPO_DIGITAL', 'VULNERACION', 'PERSONA_NATURAL_ADULTA', 'NO_APLICA', '13', U&'\00d1u\00f1oa', '13', 'SIN_INFORMACION', 'SIN_INFORMACION', 'UPR-REQ', 'UPR-REQ', 'OFICINA_NACIONAL', 'UPR-REQ', 'SIN_ATENCION', 'NIVEL_CENTRAL', 'UPR', 'CENTRAL', 'NO_APLICA', 'MEDIANA', 'SOL', 'NO_APLICA', -1, 'SIN_INFORMACION', 'SIN_INFORMACION', 'SIN_INFORMACION', 'INFORMADO', 'INFORMADO', 'INFORMADO', 'INFORMADO', 'INFORMADO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE');
CREATE MEMORY TABLE "public"."stat_cut_event"(
    "cut_id" BIGINT NOT NULL,
    "event_id" BIGINT NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "variable" CHARACTER VARYING(32) NOT NULL,
    "code" CHARACTER VARYING(40) NOT NULL,
    "catalog_version_id" BIGINT
);
ALTER TABLE "public"."stat_cut_event" ADD CONSTRAINT "public"."CONSTRAINT_3C" PRIMARY KEY("cut_id", "event_id");
-- 2 +/- SELECT COUNT(*) FROM public.stat_cut_event;
INSERT INTO "public"."stat_cut_event" VALUES
(1, 3, 18, 'DERECHO', '3.1.01', 4),
(1, 4, 18, 'CONTEXTO', 'FAMILIAR', 5);
CREATE INDEX "public"."ix_stat_cut_event_com" ON "public"."stat_cut_event"("cut_id" NULLS FIRST, "communication_id" NULLS FIRST, "variable" NULLS FIRST);
CREATE MEMORY TABLE "public"."stat_cut_action"(
    "cut_id" BIGINT NOT NULL,
    "unit_id" BIGINT NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "action_type" CHARACTER VARYING(40) NOT NULL,
    "origin" CHARACTER VARYING(20) NOT NULL,
    "note_id" BIGINT,
    "catalog_version_id" BIGINT
);
ALTER TABLE "public"."stat_cut_action" ADD CONSTRAINT "public"."CONSTRAINT_6D6" PRIMARY KEY("cut_id", "unit_id");
-- 3 +/- SELECT COUNT(*) FROM public.stat_cut_action;
INSERT INTO "public"."stat_cut_action" VALUES
(1, 6, 18, 'OFICIO', 'GESTION_CASO', 3, 6),
(1, 7, 4, 'ORIENTACION', 'PRIMERA_ATENCION', NULL, NULL),
(1, 9, 5, 'ORIENTACION', 'PRIMERA_ATENCION', NULL, NULL);
CREATE INDEX "public"."ix_stat_cut_action_com" ON "public"."stat_cut_action"("cut_id" NULLS FIRST, "communication_id" NULLS FIRST);
CREATE MEMORY TABLE "public"."stat_cut_action_institution"(
    "cut_id" BIGINT NOT NULL,
    "institution_id" BIGINT NOT NULL,
    "note_id" BIGINT NOT NULL,
    "type_code" CHARACTER VARYING(40),
    "catalog_version_id" BIGINT
);
ALTER TABLE "public"."stat_cut_action_institution" ADD CONSTRAINT "public"."CONSTRAINT_AE" PRIMARY KEY("cut_id", "institution_id");
-- 1 +/- SELECT COUNT(*) FROM public.stat_cut_action_institution;
INSERT INTO "public"."stat_cut_action_institution" VALUES
(1, 1, 3, 'ESTABLECIMIENTO_EDUCATIVO', 8);
CREATE INDEX "public"."ix_stat_cut_institution_note" ON "public"."stat_cut_action_institution"("cut_id" NULLS FIRST, "note_id" NULLS FIRST);
CREATE MEMORY TABLE "public"."stat_cut_person"(
    "cut_id" BIGINT NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "ordinal" INTEGER NOT NULL,
    "region_code" CHARACTER VARYING(40),
    "commune" CHARACTER VARYING(200),
    "age_years" BIGINT,
    "age_bracket" CHARACTER VARYING(40),
    "nationality" CHARACTER VARYING(200)
);
ALTER TABLE "public"."stat_cut_person" ADD CONSTRAINT "public"."CONSTRAINT_871" PRIMARY KEY("cut_id", "communication_id", "ordinal");
-- 5 +/- SELECT COUNT(*) FROM public.stat_cut_person;
INSERT INTO "public"."stat_cut_person" VALUES
(1, 5, 1, NULL, NULL, NULL, NULL, NULL),
(1, 6, 1, '13', U&'\00d1u\00f1oa', 9, NULL, NULL),
(1, 9, 1, NULL, NULL, NULL, NULL, NULL),
(1, 10, 1, NULL, NULL, NULL, NULL, NULL),
(1, 18, 1, '13', U&'\00d1u\00f1oa', 9, NULL, NULL);
CREATE MEMORY TABLE "public"."stat_cut_case"(
    "cut_id" BIGINT NOT NULL,
    "case_id" BIGINT NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "created_ms" BIGINT NOT NULL
);
ALTER TABLE "public"."stat_cut_case" ADD CONSTRAINT "public"."CONSTRAINT_E75" PRIMARY KEY("cut_id", "case_id");
-- 5 +/- SELECT COUNT(*) FROM public.stat_cut_case;
INSERT INTO "public"."stat_cut_case" VALUES
(1, 1, 1, 1795532157346),
(1, 2, 1, 1795532157463),
(1, 3, 12, 1795532157806),
(1, 4, 12, 1795532157827),
(1, 5, 18, 1795805923648);
CREATE INDEX "public"."ix_stat_cut_case_com" ON "public"."stat_cut_case"("cut_id" NULLS FIRST, "communication_id" NULLS FIRST);
CREATE MEMORY TABLE "public"."stat_view"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 2) DEFAULT ON NULL NOT NULL,
    "owner_id" BIGINT NOT NULL,
    "title" CHARACTER VARYING(200) NOT NULL,
    "definition_json" CHARACTER VARYING NOT NULL,
    "shared" BOOLEAN DEFAULT FALSE NOT NULL,
    "created_ms" BIGINT NOT NULL,
    "updated_ms" BIGINT NOT NULL,
    "revision" BIGINT DEFAULT 0 NOT NULL
);
ALTER TABLE "public"."stat_view" ADD CONSTRAINT "public"."CONSTRAINT_83" PRIMARY KEY("id");
-- 1 +/- SELECT COUNT(*) FROM public.stat_view;
INSERT INTO "public"."stat_view" VALUES
(1, 20, 'Ingresos por canal', '{"measure":"INGRESOS","percent":"TOTAL","period":{"from":"2025-07-01","type":"CUENTA_PUBLICA"},"rows":["CANAL"]}', FALSE, 1795805923914, 1795805923914, 0);
CREATE INDEX "public"."ix_stat_view_owner" ON "public"."stat_view"("owner_id" NULLS FIRST, "id" NULLS FIRST);
CREATE MEMORY TABLE "public"."stat_export_cut_folio"(
    "cut_id" BIGINT NOT NULL,
    "communication_id" BIGINT NOT NULL,
    "folio" CHARACTER VARYING(48)
);
ALTER TABLE "public"."stat_export_cut_folio" ADD CONSTRAINT "public"."CONSTRAINT_E2" PRIMARY KEY("cut_id", "communication_id");
-- 18 +/- SELECT COUNT(*) FROM public.stat_export_cut_folio;
INSERT INTO "public"."stat_export_cut_folio" VALUES
(1, 1, 'COM-A-000001'),
(1, 2, 'COM-A-000002'),
(1, 3, 'COM-A-000003'),
(1, 4, 'COM-A-000004'),
(1, 5, 'COM-A-000005'),
(1, 6, 'COM-A-000006'),
(1, 7, 'COM-A-000007'),
(1, 8, 'COM-A-000008'),
(1, 9, 'COM-A-000009'),
(1, 10, 'COM-A-000010'),
(1, 11, 'COM-A-000011'),
(1, 12, 'COM-A-000012'),
(1, 13, 'COM-A-000013'),
(1, 14, 'COM-A-000014'),
(1, 15, 'COM-A-000015'),
(1, 16, 'COM-A-000016'),
(1, 17, 'COM-A-000017'),
(1, 18, 'COMA-000018');
CREATE MEMORY TABLE "public"."stat_export_cut_origin"(
    "cut_id" BIGINT NOT NULL,
    "source_kind" CHARACTER VARYING(8) NOT NULL,
    "value_id" BIGINT NOT NULL,
    "record_type" CHARACTER VARYING(8) NOT NULL,
    "record_id" BIGINT NOT NULL
);
ALTER TABLE "public"."stat_export_cut_origin" ADD CONSTRAINT "public"."CONSTRAINT_80" PRIMARY KEY("cut_id", "source_kind", "value_id");
-- 5 +/- SELECT COUNT(*) FROM public.stat_export_cut_origin;
INSERT INTO "public"."stat_export_cut_origin" VALUES
(1, 'ACTO', 6, 'CAS', 5),
(1, 'ACTO', 7, 'REQ', 3),
(1, 'ACTO', 9, 'REQ', 4),
(1, 'EVENTO', 3, 'REQ', 9),
(1, 'EVENTO', 4, 'REQ', 9);
CREATE MEMORY TABLE "public"."stat_export_cut_integrity"(
    "cut_id" BIGINT NOT NULL,
    "export_sha256" CHARACTER(64) NOT NULL
);
ALTER TABLE "public"."stat_export_cut_integrity" ADD CONSTRAINT "public"."CONSTRAINT_D8" PRIMARY KEY("cut_id");
-- 1 +/- SELECT COUNT(*) FROM public.stat_export_cut_integrity;
INSERT INTO "public"."stat_export_cut_integrity" VALUES
(1, 'e5d6b0a3326da55bdd8bf364a1c1c81c7bd9fc7379a04cd6d5d730db4950eb73');
CREATE MEMORY TABLE "public"."stat_series"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) DEFAULT ON NULL NOT NULL,
    "title" CHARACTER VARYING(200) NOT NULL,
    "indicator" CHARACTER VARYING(40) NOT NULL,
    "scope_id" BIGINT,
    "source" CHARACTER VARYING(300) NOT NULL,
    "methodology" CHARACTER VARYING NOT NULL,
    "granularity" CHARACTER VARYING(20) NOT NULL,
    "period_from" CHARACTER VARYING(10) NOT NULL,
    "period_to" CHARACTER VARYING(10) NOT NULL,
    "imported_by" BIGINT NOT NULL,
    "imported_ms" BIGINT NOT NULL,
    "basis" CHARACTER VARYING NOT NULL,
    "file_sha256" CHARACTER(64) NOT NULL,
    "state" CHARACTER VARYING(12) NOT NULL,
    "revision" BIGINT NOT NULL,
    "annulled_by" BIGINT,
    "annulled_ms" BIGINT,
    "annul_basis" CHARACTER VARYING
);
ALTER TABLE "public"."stat_series" ADD CONSTRAINT "public"."CONSTRAINT_3F" PRIMARY KEY("id");
-- 0 +/- SELECT COUNT(*) FROM public.stat_series;
CREATE INDEX "public"."ix_stat_series_overlap" ON "public"."stat_series"("indicator" NULLS FIRST, "state" NULLS FIRST, "scope_id" NULLS FIRST, "period_from" NULLS FIRST, "period_to" NULLS FIRST);
CREATE MEMORY TABLE "public"."stat_series_value"(
    "series_id" BIGINT NOT NULL,
    "ordinal" INTEGER NOT NULL,
    "period_from" CHARACTER VARYING(10) NOT NULL,
    "period_to" CHARACTER VARYING(10) NOT NULL,
    "dimension" CHARACTER VARYING(40) NOT NULL,
    "category_label" CHARACTER VARYING(200) NOT NULL,
    "category_code" CHARACTER VARYING(200),
    "is_total" BOOLEAN NOT NULL,
    "amount" BIGINT NOT NULL
);
ALTER TABLE "public"."stat_series_value" ADD CONSTRAINT "public"."CONSTRAINT_8D83" PRIMARY KEY("series_id", "ordinal");
-- 0 +/- SELECT COUNT(*) FROM public.stat_series_value;
ALTER TABLE "public"."attention_handoff" ADD CONSTRAINT "public"."CONSTRAINT_FC162C7F_2" CHECK("state" IN('PENDIENTE', 'RECIBIDA', 'DEVUELTA', 'RETIRADA')) NOCHECK;
ALTER TABLE "public"."attention_handoff" ADD CONSTRAINT "public"."CONSTRAINT_FC162C7F_1" CHECK("recipient_kind" IN('PROFESIONAL', 'JEFATURA')) NOCHECK;
ALTER TABLE "public"."attention_handoff" ADD CONSTRAINT "public"."CONSTRAINT_FC162C7F_3" CHECK((("recipient_kind" = 'PROFESIONAL')
    AND ("target_user" IS NOT NULL))
    OR (("recipient_kind" = 'JEFATURA')
    AND ("target_user" IS NULL))) NOCHECK;
ALTER TABLE "public"."stat_series_value" ADD CONSTRAINT "public"."CONSTRAINT_8D8" CHECK("amount" >= CAST(0 AS BIGINT)) NOCHECK;
ALTER TABLE "public"."integration_control" ADD CONSTRAINT "public"."CONSTRAINT_6D9A6" CHECK("name" IN('FORMULARIO_PULL', 'CORREO_GRAPH')) NOCHECK;
ALTER TABLE "public"."attention_record" ADD CONSTRAINT "public"."CONSTRAINT_F01B" CHECK("modality" IN('GENERAL', 'NNA')) NOCHECK;
ALTER TABLE "public"."substitution" ADD CONSTRAINT "public"."CONSTRAINT_313E0" CHECK("until_ms" > "from_ms") NOCHECK;
ALTER TABLE "public"."document_item" ADD CONSTRAINT "public"."CONSTRAINT_5E7CDBB" CHECK((("communication_id" IS NOT NULL)
    AND ("requirement_id" IS NULL)
    AND ("case_id" IS NULL))
    OR (("communication_id" IS NULL)
    AND ("requirement_id" IS NOT NULL)
    AND ("case_id" IS NULL))
    OR (("communication_id" IS NULL)
    AND ("requirement_id" IS NULL)
    AND ("case_id" IS NOT NULL))) NOCHECK;
ALTER TABLE "public"."requirement" ADD CONSTRAINT "public"."CONSTRAINT_15A8DC43_4" CHECK(("complexity" IN('BAJA', 'MEDIANA', 'ALTA'))
    OR ("complexity" IS NULL)) NOCHECK;
ALTER TABLE "public"."integration_status" ADD CONSTRAINT "public"."CONSTRAINT_F5C" CHECK("name" IN('FORMULARIO_PULL', 'CORREO_GRAPH')) NOCHECK;
ALTER TABLE "public"."substitution_designation" ADD CONSTRAINT "public"."CONSTRAINT_A701ADB" CHECK("titular_id" <> "substitute_id") NOCHECK;
ALTER TABLE "public"."mail_outbox" ADD CONSTRAINT "public"."CONSTRAINT_FDC54F45" CHECK("state" IN('PENDIENTE', 'EN_INTENTO', 'ACEPTADO', 'REINTENTABLE', 'AMBIGUO', 'RECHAZADO', 'SUPRIMIDO', 'DESCARTADO')) NOCHECK;
ALTER TABLE "public"."formulario_reception" ADD CONSTRAINT "public"."formulario_reception_state" CHECK("state" IN('RECHAZADA', 'REGISTRADA', 'CONFIRMADA')) NOCHECK;
ALTER TABLE "public"."external_field" ADD CONSTRAINT "public"."CONSTRAINT_F81F" CHECK("field_group" IN('CAMPO', 'PERSONA', 'INSTITUCION')) NOCHECK;
ALTER TABLE "public"."document_scan" ADD CONSTRAINT "public"."CONSTRAINT_5E8127" CHECK("verdict" IN('LIMPIO', 'INFECTADO', 'ERROR')) NOCHECK;
ALTER TABLE "public"."requirement" ADD CONSTRAINT "public"."CONSTRAINT_15A8" UNIQUE NULLS DISTINCT ("communication_id");
ALTER TABLE "public"."stat_declared_version" ADD CONSTRAINT "public"."CONSTRAINT_3AB6" UNIQUE NULLS DISTINCT ("source", "source_id");
ALTER TABLE "public"."communication_link" ADD CONSTRAINT "public"."CONSTRAINT_C9B" UNIQUE NULLS DISTINCT ("communication_id");
ALTER TABLE "public"."requirement" ADD CONSTRAINT "public"."CONSTRAINT_15A" UNIQUE NULLS DISTINCT ("folio");
ALTER TABLE "public"."case_record" ADD CONSTRAINT "public"."CONSTRAINT_24" UNIQUE NULLS DISTINCT ("folio");
ALTER TABLE "public"."communication" ADD CONSTRAINT "public"."CONSTRAINT_C2" UNIQUE NULLS DISTINCT ("folio");
ALTER TABLE "public"."communication" ADD CONSTRAINT "public"."CONSTRAINT_C24AC" UNIQUE NULLS DISTINCT ("external_source", "external_id");
ALTER TABLE "public"."communication_original" ADD CONSTRAINT "public"."uq_original_source" UNIQUE NULLS DISTINCT ("source", "external_id");
ALTER TABLE "public"."mail_outbox" ADD CONSTRAINT "public"."CONSTRAINT_FDC" UNIQUE NULLS DISTINCT ("notice_id");
ALTER TABLE "public"."external_document" ADD CONSTRAINT "public"."external_document_attachment" UNIQUE NULLS DISTINCT ("communication_id", "attachment_id");
ALTER TABLE "public"."integration_control" ADD CONSTRAINT "public"."CONSTRAINT_6D9" UNIQUE NULLS DISTINCT ("name");
ALTER TABLE "public"."substitution_designation" ADD CONSTRAINT "public"."CONSTRAINT_A701AD" UNIQUE NULLS DISTINCT ("scope_id", "titular_id", "substitute_id");
ALTER TABLE "public"."mail_outbox" ADD CONSTRAINT "public"."CONSTRAINT_FDC5" UNIQUE NULLS DISTINCT ("correlation_id");
ALTER TABLE "public"."intake_revision" ADD CONSTRAINT "public"."CONSTRAINT_78641" UNIQUE NULLS DISTINCT ("communication_id", "revision");
ALTER TABLE "public"."stat_cut" ADD CONSTRAINT "public"."CONSTRAINT_4E8F" UNIQUE NULLS DISTINCT ("code");
ALTER TABLE "public"."formulario_reception" ADD CONSTRAINT "public"."CONSTRAINT_AC9DF8" UNIQUE NULLS DISTINCT ("institutional_id");
ALTER TABLE "public"."communication_original" ADD CONSTRAINT "public"."CONSTRAINT_206A" UNIQUE NULLS DISTINCT ("communication_id");
ALTER TABLE "public"."solicitation" ADD CONSTRAINT "public"."CONSTRAINT_8D" UNIQUE NULLS DISTINCT ("communication_id");
ALTER TABLE "public"."mail_attempt" ADD CONSTRAINT "public"."CONSTRAINT_D49" UNIQUE NULLS DISTINCT ("claim_id");
ALTER TABLE "public"."org_scope" ADD CONSTRAINT "public"."CONSTRAINT_7D" UNIQUE NULLS DISTINCT ("code");
ALTER TABLE "public"."requirement" ADD CONSTRAINT "public"."CONSTRAINT_15A8D" UNIQUE NULLS DISTINCT ("solicitation_id");
ALTER TABLE "public"."intake_upload" ADD CONSTRAINT "public"."CONSTRAINT_8D6C" UNIQUE NULLS DISTINCT ("storage_key");
ALTER TABLE "public"."formulario_reception" ADD CONSTRAINT "public"."CONSTRAINT_AC9DF" UNIQUE NULLS DISTINCT ("communication_id");
ALTER TABLE "public"."formulario_reception" ADD CONSTRAINT "public"."CONSTRAINT_AC9D" UNIQUE NULLS DISTINCT ("operation_id");
ALTER TABLE "public"."attention_revision" ADD CONSTRAINT "public"."CONSTRAINT_77032" UNIQUE NULLS DISTINCT ("requirement_id", "requirement_revision");
ALTER TABLE "public"."external_person" ADD CONSTRAINT "public"."uq_external_person" UNIQUE NULLS DISTINCT ("communication_id", "person_key");
ALTER TABLE "public"."staff" ADD CONSTRAINT "public"."CONSTRAINT_68" UNIQUE NULLS DISTINCT ("username");
ALTER TABLE "public"."document_item" ADD CONSTRAINT "public"."CONSTRAINT_5E" UNIQUE NULLS DISTINCT ("storage_key");
ALTER TABLE "public"."territorial_coverage" ADD CONSTRAINT "public"."CONSTRAINT_81DA" FOREIGN KEY("from_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."attention_handoff" ADD CONSTRAINT "public"."CONSTRAINT_FC162C7F_0" FOREIGN KEY("decided_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."substitution_designation" ADD CONSTRAINT "public"."CONSTRAINT_A70" FOREIGN KEY("scope_id") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."management_note" ADD CONSTRAINT "public"."CONSTRAINT_2A57" FOREIGN KEY("case_id") REFERENCES "public"."case_record"("id") NOCHECK;
ALTER TABLE "public"."substitution_scope" ADD CONSTRAINT "public"."CONSTRAINT_875CB" FOREIGN KEY("scope_id") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."person_reference" ADD CONSTRAINT "public"."CONSTRAINT_9D69" FOREIGN KEY("requirement_id") REFERENCES "public"."requirement"("id") NOCHECK;
ALTER TABLE "public"."attention_handoff" ADD CONSTRAINT "public"."CONSTRAINT_FC162" FOREIGN KEY("from_scope") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."task_item" ADD CONSTRAINT "public"."fk_task_item_action_version" FOREIGN KEY("action_catalog_version") REFERENCES "public"."stat_catalog_version"("id") NOCHECK;
ALTER TABLE "public"."document_item" ADD CONSTRAINT "public"."CONSTRAINT_5E7CDB" FOREIGN KEY("uploaded_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."attention_record" ADD CONSTRAINT "public"."CONSTRAINT_F01" FOREIGN KEY("updated_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."note_institution" ADD CONSTRAINT "public"."CONSTRAINT_6517" FOREIGN KEY("catalog_version_id") REFERENCES "public"."stat_catalog_version"("id") NOCHECK;
ALTER TABLE "public"."stat_series" ADD CONSTRAINT "public"."CONSTRAINT_3F6BE" FOREIGN KEY("annulled_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."flow_request" ADD CONSTRAINT "public"."CONSTRAINT_86C98C7E" FOREIGN KEY("decided_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."mail_outbox" ADD CONSTRAINT "public"."CONSTRAINT_FDC54" FOREIGN KEY("notice_id") REFERENCES "public"."inbox_notice"("id") NOCHECK;
ALTER TABLE "public"."stat_catalog_version" ADD CONSTRAINT "public"."CONSTRAINT_543B" FOREIGN KEY("activated_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."communication_original" ADD CONSTRAINT "public"."CONSTRAINT_206A5" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."substitution" ADD CONSTRAINT "public"."CONSTRAINT_313E" FOREIGN KEY("ended_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."document_scan" ADD CONSTRAINT "public"."CONSTRAINT_5E812" FOREIGN KEY("requested_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."stat_series_value" ADD CONSTRAINT "public"."CONSTRAINT_8D834" FOREIGN KEY("series_id") REFERENCES "public"."stat_series"("id") NOCHECK;
ALTER TABLE "public"."solicitation" ADD CONSTRAINT "public"."CONSTRAINT_8D0" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."document_item" ADD CONSTRAINT "public"."fk_document_note" FOREIGN KEY("note_id") REFERENCES "public"."management_note"("id") NOCHECK;
ALTER TABLE "public"."reopening" ADD CONSTRAINT "public"."CONSTRAINT_B7057" FOREIGN KEY("principal_approved_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."substitution_designation" ADD CONSTRAINT "public"."CONSTRAINT_A701A" FOREIGN KEY("substitute_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."reopening" ADD CONSTRAINT "public"."CONSTRAINT_B70" FOREIGN KEY("case_id") REFERENCES "public"."case_record"("id") NOCHECK;
ALTER TABLE "public"."stat_view" ADD CONSTRAINT "public"."CONSTRAINT_836" FOREIGN KEY("owner_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."external_attribute" ADD CONSTRAINT "public"."CONSTRAINT_7AB" FOREIGN KEY("person_id") REFERENCES "public"."external_person"("id") NOCHECK;
ALTER TABLE "public"."stat_catalog_mapping" ADD CONSTRAINT "public"."CONSTRAINT_713" FOREIGN KEY("from_version") REFERENCES "public"."stat_catalog_version"("id") NOCHECK;
ALTER TABLE "public"."communication_link" ADD CONSTRAINT "public"."CONSTRAINT_C9B9" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."communication_link" ADD CONSTRAINT "public"."CONSTRAINT_C9B90" FOREIGN KEY("requirement_id") REFERENCES "public"."requirement"("id") NOCHECK;
ALTER TABLE "public"."case_record" ADD CONSTRAINT "public"."CONSTRAINT_248FF" FOREIGN KEY("responsible_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."communication" ADD CONSTRAINT "public"."CONSTRAINT_C24A" FOREIGN KEY("origin_scope") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."attention_revision" ADD CONSTRAINT "public"."CONSTRAINT_7703" FOREIGN KEY("author_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."communication_link" ADD CONSTRAINT "public"."CONSTRAINT_C9B90E" FOREIGN KEY("proposed_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."role_assignment" ADD CONSTRAINT "public"."CONSTRAINT_19" FOREIGN KEY("user_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."solicitation" ADD CONSTRAINT "public"."CONSTRAINT_8D061" FOREIGN KEY("created_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."mail_outbox" ADD CONSTRAINT "public"."CONSTRAINT_FDC54F" FOREIGN KEY("recipient_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."requirement" ADD CONSTRAINT "public"."CONSTRAINT_15A8DC" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."inbox_notice" ADD CONSTRAINT "public"."CONSTRAINT_4EFA" FOREIGN KEY("user_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."person_reference" ADD CONSTRAINT "public"."CONSTRAINT_9D6" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."territorial_coverage" ADD CONSTRAINT "public"."CONSTRAINT_81D" FOREIGN KEY("scope_id") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."attention_handoff" ADD CONSTRAINT "public"."CONSTRAINT_FC162C7" FOREIGN KEY("target_scope") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."reopening" ADD CONSTRAINT "public"."CONSTRAINT_B705" FOREIGN KEY("requested_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."case_assignment" ADD CONSTRAINT "public"."CONSTRAINT_DF83" FOREIGN KEY("assigned_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."reopening" ADD CONSTRAINT "public"."CONSTRAINT_B7" FOREIGN KEY("requirement_id") REFERENCES "public"."requirement"("id") NOCHECK;
ALTER TABLE "public"."closure_record" ADD CONSTRAINT "public"."CONSTRAINT_4265" FOREIGN KEY("case_id") REFERENCES "public"."case_record"("id") NOCHECK;
ALTER TABLE "public"."management_note" ADD CONSTRAINT "public"."CONSTRAINT_2A57A" FOREIGN KEY("author_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."note_institution" ADD CONSTRAINT "public"."CONSTRAINT_651" FOREIGN KEY("note_id") REFERENCES "public"."management_note"("id") NOCHECK;
ALTER TABLE "public"."attention_revision" ADD CONSTRAINT "public"."CONSTRAINT_770" FOREIGN KEY("requirement_id") REFERENCES "public"."attention_record"("requirement_id") NOCHECK;
ALTER TABLE "public"."case_member" ADD CONSTRAINT "public"."CONSTRAINT_1C0C1" FOREIGN KEY("added_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."document_scan" ADD CONSTRAINT "public"."CONSTRAINT_5E81" FOREIGN KEY("document_id") REFERENCES "public"."document_item"("id") NOCHECK;
ALTER TABLE "public"."flow_request" ADD CONSTRAINT "public"."CONSTRAINT_86C98C7E_0" FOREIGN KEY("successor_case_id") REFERENCES "public"."case_record"("id") NOCHECK;
ALTER TABLE "public"."mail_outbox" ADD CONSTRAINT "public"."CONSTRAINT_FDC54F4" FOREIGN KEY("resolved_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."attention_handoff" ADD CONSTRAINT "public"."CONSTRAINT_FC162C" FOREIGN KEY("sent_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."attention_handoff" ADD CONSTRAINT "public"."CONSTRAINT_FC16" FOREIGN KEY("from_user") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."flow_request" ADD CONSTRAINT "public"."CONSTRAINT_86C9" FOREIGN KEY("origin_case_id") REFERENCES "public"."case_record"("id") NOCHECK;
ALTER TABLE "public"."stat_catalog_mapping" ADD CONSTRAINT "public"."CONSTRAINT_71322" FOREIGN KEY("loaded_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."requirement" ADD CONSTRAINT "public"."CONSTRAINT_15A8DC43" FOREIGN KEY("principal_scope") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."intake_upload" ADD CONSTRAINT "public"."CONSTRAINT_8D6CB" FOREIGN KEY("user_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."external_person" ADD CONSTRAINT "public"."CONSTRAINT_1CA3" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."case_member" ADD CONSTRAINT "public"."CONSTRAINT_1C0C" FOREIGN KEY("user_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."substitution_scope" ADD CONSTRAINT "public"."CONSTRAINT_875" FOREIGN KEY("substitution_id") REFERENCES "public"."substitution"("id") NOCHECK;
ALTER TABLE "public"."stat_declared_person" ADD CONSTRAINT "public"."CONSTRAINT_5AC" FOREIGN KEY("version_id") REFERENCES "public"."stat_declared_version"("id") NOCHECK;
ALTER TABLE "public"."requirement" ADD CONSTRAINT "public"."CONSTRAINT_15A8DC4" FOREIGN KEY("solicitation_id") REFERENCES "public"."solicitation"("id") NOCHECK;
ALTER TABLE "public"."attention_handoff" ADD CONSTRAINT "public"."CONSTRAINT_FC1" FOREIGN KEY("requirement_id") REFERENCES "public"."attention_record"("requirement_id") NOCHECK;
ALTER TABLE "public"."command_log" ADD CONSTRAINT "public"."CONSTRAINT_A8E" FOREIGN KEY("actor_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."mail_attempt" ADD CONSTRAINT "public"."CONSTRAINT_D499" FOREIGN KEY("outbox_id") REFERENCES "public"."mail_outbox"("id") NOCHECK;
ALTER TABLE "public"."role_assignment" ADD CONSTRAINT "public"."CONSTRAINT_194" FOREIGN KEY("scope_id") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."intake_revision" ADD CONSTRAINT "public"."CONSTRAINT_7864" FOREIGN KEY("author_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."task_item" ADD CONSTRAINT "public"."CONSTRAINT_AC3A9" FOREIGN KEY("created_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."intake_record" ADD CONSTRAINT "public"."CONSTRAINT_87AF" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."flow_request" ADD CONSTRAINT "public"."CONSTRAINT_86C" FOREIGN KEY("requirement_id") REFERENCES "public"."requirement"("id") NOCHECK;
ALTER TABLE "public"."document_item" ADD CONSTRAINT "public"."CONSTRAINT_5E7C" FOREIGN KEY("requirement_id") REFERENCES "public"."requirement"("id") NOCHECK;
ALTER TABLE "public"."stat_catalog_item" ADD CONSTRAINT "public"."CONSTRAINT_153B" FOREIGN KEY("version_id") REFERENCES "public"."stat_catalog_version"("id") NOCHECK;
ALTER TABLE "public"."reopening" ADD CONSTRAINT "public"."CONSTRAINT_B70577" FOREIGN KEY("decided_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."milestone" ADD CONSTRAINT "public"."CONSTRAINT_C08419" FOREIGN KEY("actor_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."org_scope" ADD CONSTRAINT "public"."CONSTRAINT_7D3" FOREIGN KEY("unit_code") REFERENCES "public"."org_unit"("code") NOCHECK;
ALTER TABLE "public"."solicitation" ADD CONSTRAINT "public"."fk_sol_requested_area" FOREIGN KEY("requested_area_scope") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."stat_cut" ADD CONSTRAINT "public"."CONSTRAINT_4E8F6D" FOREIGN KEY("created_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."communication_link" ADD CONSTRAINT "public"."CONSTRAINT_C9B90EC3" FOREIGN KEY("decided_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."flow_request" ADD CONSTRAINT "public"."CONSTRAINT_86C98" FOREIGN KEY("origin_scope") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."management_note" ADD CONSTRAINT "public"."CONSTRAINT_2A5" FOREIGN KEY("requirement_id") REFERENCES "public"."requirement"("id") NOCHECK;
ALTER TABLE "public"."case_record" ADD CONSTRAINT "public"."CONSTRAINT_248FF84" FOREIGN KEY("closed_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."communication" ADD CONSTRAINT "public"."CONSTRAINT_C24" FOREIGN KEY("created_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."stat_event" ADD CONSTRAINT "public"."CONSTRAINT_E887C" FOREIGN KEY("author_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."external_field" ADD CONSTRAINT "public"."CONSTRAINT_F81" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."staff" ADD CONSTRAINT "public"."CONSTRAINT_68A" FOREIGN KEY("home_scope") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."flow_request" ADD CONSTRAINT "public"."CONSTRAINT_86C98C7" FOREIGN KEY("requested_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."document_item" ADD CONSTRAINT "public"."fk_document_previous" FOREIGN KEY("previous_document_id") REFERENCES "public"."document_item"("id") NOCHECK;
ALTER TABLE "public"."stat_catalog_version" ADD CONSTRAINT "public"."CONSTRAINT_543B2" FOREIGN KEY("retired_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."formulario_reception" ADD CONSTRAINT "public"."CONSTRAINT_AC9DF8C" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."stat_cut" ADD CONSTRAINT "public"."CONSTRAINT_4E8F6" FOREIGN KEY("scope_id") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."milestone" ADD CONSTRAINT "public"."CONSTRAINT_C084" FOREIGN KEY("requirement_id") REFERENCES "public"."requirement"("id") NOCHECK;
ALTER TABLE "public"."requirement" ADD CONSTRAINT "public"."CONSTRAINT_15A8DC43_3" FOREIGN KEY("closed_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."stat_declared_version" ADD CONSTRAINT "public"."CONSTRAINT_3AB" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."substitution" ADD CONSTRAINT "public"."CONSTRAINT_313" FOREIGN KEY("activated_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."requirement" ADD CONSTRAINT "public"."CONSTRAINT_15A8DC43_2" FOREIGN KEY("created_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."requirement" ADD CONSTRAINT "public"."CONSTRAINT_15A8DC43_1" FOREIGN KEY("attention_user") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."stat_cut" ADD CONSTRAINT "public"."CONSTRAINT_4E8F6DD" FOREIGN KEY("annulled_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."requirement" ADD CONSTRAINT "public"."CONSTRAINT_15A8DC43_0" FOREIGN KEY("attention_scope") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."substitution_scope" ADD CONSTRAINT "public"."CONSTRAINT_875C" FOREIGN KEY("grant_id") REFERENCES "public"."role_assignment"("id") NOCHECK;
ALTER TABLE "public"."milestone" ADD CONSTRAINT "public"."CONSTRAINT_C08" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."document_item" ADD CONSTRAINT "public"."CONSTRAINT_5E7" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."external_document" ADD CONSTRAINT "public"."CONSTRAINT_9A" FOREIGN KEY("document_id") REFERENCES "public"."document_item"("id") NOCHECK;
ALTER TABLE "public"."closure_record" ADD CONSTRAINT "public"."CONSTRAINT_426" FOREIGN KEY("requirement_id") REFERENCES "public"."requirement"("id") NOCHECK;
ALTER TABLE "public"."stat_event" ADD CONSTRAINT "public"."CONSTRAINT_E887CC" FOREIGN KEY("reopening_id") REFERENCES "public"."reopening"("id") NOCHECK;
ALTER TABLE "public"."stat_catalog_version" ADD CONSTRAINT "public"."CONSTRAINT_543" FOREIGN KEY("loaded_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."intake_revision" ADD CONSTRAINT "public"."CONSTRAINT_786" FOREIGN KEY("communication_id") REFERENCES "public"."intake_record"("communication_id") NOCHECK;
ALTER TABLE "public"."task_item" ADD CONSTRAINT "public"."CONSTRAINT_AC3A" FOREIGN KEY("executor_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."solicitation" ADD CONSTRAINT "public"."CONSTRAINT_8D06" FOREIGN KEY("destination_scope") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."intake_record" ADD CONSTRAINT "public"."CONSTRAINT_87AF1" FOREIGN KEY("updated_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."flow_request" ADD CONSTRAINT "public"."CONSTRAINT_86C98C" FOREIGN KEY("target_scope") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."stat_catalog_mapping" ADD CONSTRAINT "public"."CONSTRAINT_7132" FOREIGN KEY("to_version") REFERENCES "public"."stat_catalog_version"("id") NOCHECK;
ALTER TABLE "public"."attention_handoff" ADD CONSTRAINT "public"."CONSTRAINT_FC162C7F" FOREIGN KEY("target_user") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."case_assignment" ADD CONSTRAINT "public"."CONSTRAINT_DF" FOREIGN KEY("case_id") REFERENCES "public"."case_record"("id") NOCHECK;
ALTER TABLE "public"."task_item" ADD CONSTRAINT "public"."CONSTRAINT_AC3" FOREIGN KEY("case_id") REFERENCES "public"."case_record"("id") NOCHECK;
ALTER TABLE "public"."role_assignment" ADD CONSTRAINT "public"."CONSTRAINT_1946" FOREIGN KEY("granted_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."stat_event" ADD CONSTRAINT "public"."CONSTRAINT_E887" FOREIGN KEY("catalog_version_id") REFERENCES "public"."stat_catalog_version"("id") NOCHECK;
ALTER TABLE "public"."audit_event" ADD CONSTRAINT "public"."CONSTRAINT_3684" FOREIGN KEY("scope_id") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."closure_record" ADD CONSTRAINT "public"."CONSTRAINT_4265F" FOREIGN KEY("actor_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."business_holiday" ADD CONSTRAINT "public"."CONSTRAINT_E7" FOREIGN KEY("created_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."case_member" ADD CONSTRAINT "public"."CONSTRAINT_1C0" FOREIGN KEY("case_id") REFERENCES "public"."case_record"("id") NOCHECK;
ALTER TABLE "public"."substitution" ADD CONSTRAINT "public"."CONSTRAINT_31" FOREIGN KEY("designation_id") REFERENCES "public"."substitution_designation"("id") NOCHECK;
ALTER TABLE "public"."case_record" ADD CONSTRAINT "public"."CONSTRAINT_248F" FOREIGN KEY("scope_id") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."case_record" ADD CONSTRAINT "public"."CONSTRAINT_248" FOREIGN KEY("requirement_id") REFERENCES "public"."requirement"("id") NOCHECK;
ALTER TABLE "public"."audit_event" ADD CONSTRAINT "public"."CONSTRAINT_368" FOREIGN KEY("actor_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."document_item" ADD CONSTRAINT "public"."CONSTRAINT_5E7CD" FOREIGN KEY("case_id") REFERENCES "public"."case_record"("id") NOCHECK;
ALTER TABLE "public"."case_record" ADD CONSTRAINT "public"."CONSTRAINT_248FF840" FOREIGN KEY("origin_case_id") REFERENCES "public"."case_record"("id") NOCHECK;
ALTER TABLE "public"."case_record" ADD CONSTRAINT "public"."CONSTRAINT_248FF8" FOREIGN KEY("created_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."stat_series" ADD CONSTRAINT "public"."CONSTRAINT_3F6" FOREIGN KEY("scope_id") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."integration_control" ADD CONSTRAINT "public"."CONSTRAINT_6D9A" FOREIGN KEY("updated_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."milestone" ADD CONSTRAINT "public"."CONSTRAINT_C0841970" FOREIGN KEY("scope_id") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."communication_original" ADD CONSTRAINT "public"."CONSTRAINT_206A5C" FOREIGN KEY("stored_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."intake_upload" ADD CONSTRAINT "public"."CONSTRAINT_8D6CBA" FOREIGN KEY("scope_id") REFERENCES "public"."org_scope"("id") NOCHECK;
ALTER TABLE "public"."external_document" ADD CONSTRAINT "public"."CONSTRAINT_9A6" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."milestone" ADD CONSTRAINT "public"."CONSTRAINT_C0841" FOREIGN KEY("case_id") REFERENCES "public"."case_record"("id") NOCHECK;
ALTER TABLE "public"."communication_link" ADD CONSTRAINT "public"."CONSTRAINT_C9B90EC" FOREIGN KEY("target_case_id") REFERENCES "public"."case_record"("id") NOCHECK;
ALTER TABLE "public"."stat_event" ADD CONSTRAINT "public"."CONSTRAINT_E88" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."intake_upload" ADD CONSTRAINT "public"."CONSTRAINT_8D6CBA7" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."attention_record" ADD CONSTRAINT "public"."CONSTRAINT_F0" FOREIGN KEY("requirement_id") REFERENCES "public"."requirement"("id") NOCHECK;
ALTER TABLE "public"."substitution_designation" ADD CONSTRAINT "public"."CONSTRAINT_A701" FOREIGN KEY("titular_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."management_note" ADD CONSTRAINT "public"."CONSTRAINT_2A57A2" FOREIGN KEY("rectifies_id") REFERENCES "public"."management_note"("id") NOCHECK;
ALTER TABLE "public"."case_assignment" ADD CONSTRAINT "public"."CONSTRAINT_DF8" FOREIGN KEY("user_id") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."management_note" ADD CONSTRAINT "public"."fk_management_note_action_version" FOREIGN KEY("action_catalog_version") REFERENCES "public"."stat_catalog_version"("id") NOCHECK;
ALTER TABLE "public"."communication_person" ADD CONSTRAINT "public"."CONSTRAINT_464" FOREIGN KEY("communication_id") REFERENCES "public"."communication"("id") NOCHECK;
ALTER TABLE "public"."communication_link" ADD CONSTRAINT "public"."fk_link_return_actor" FOREIGN KEY("returned_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."stat_series" ADD CONSTRAINT "public"."CONSTRAINT_3F6B" FOREIGN KEY("imported_by") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."milestone" ADD CONSTRAINT "public"."CONSTRAINT_C084197" FOREIGN KEY("subject_user") REFERENCES "public"."staff"("id") NOCHECK;
ALTER TABLE "public"."territorial_coverage" ADD CONSTRAINT "public"."CONSTRAINT_81DAA" FOREIGN KEY("until_by") REFERENCES "public"."staff"("id") NOCHECK;
