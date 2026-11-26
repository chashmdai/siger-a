-- V23: cortes estadisticos y configuraciones guardadas (0.8.0, D39 y D43, gate E10; DISENO 3.7 y 6). Aditiva sobre
-- V1-V22, sin siembra ni reescritura de datos.
-- * stat_cut: cabecera de un corte (STAT_CUT_CREATE, GESTION_ESTADISTICA con REPORTES en el mismo alcance; scope_id
--   nulo = institucion). Codigo CORTE-AAAA-NNN, periodo, instante (as_of_ms = instante del comando), metodologia,
--   versiones VIGENTE de cada catalogo, huella del calendario, dimensiones congeladas, rotulos de ambito y unidad,
--   cantidades, huellas SHA-256 de las filas (serializacion canonica, docs/CONTRATO_STATS_0_8_0.md) y de los metadatos
--   (que incluyen la de las filas). El id lo asigna el servidor bajo el lock global (sin AUTO_INCREMENT): las filas
--   congeladas se escriben antes que la cabecera, que se inserta ya con sus huellas. Inmutable: solo STAT_CUT_ANNUL
--   cambia state, annulled_* y revision; nada se borra.
-- * Tablas congeladas (lista cerrada de columnas, sin datos personales ni texto libre, sin claves foraneas ni ON DELETE
--   CASCADE): stat_cut_row (una fila por COM con cada dimension de valor unico de StatSource, fechas, versiones y
--   propuestas) y las relaciones de entrada del motor: stat_cut_event (clasificaciones vigentes del REQ y de los Casos
--   del alcance), stat_cut_action (actuaciones vigentes no reservadas y resultado de la primera atencion),
--   stat_cut_action_institution (tipo de institucion de cada actuacion), stat_cut_person (NNA afectados con su tramo de edad congelado, sin fecha de
--   nacimiento) y stat_cut_case (Casos del alcance). Diferencia de referencia con DISENO 6 (stat_cut_multi), aprobada
--   en el GO de E10.
-- * stat_view: configuraciones guardadas del explorador (definicion validada por StatEngine, sin datos), personales o
--   compartidas (compartir exige GESTION_ESTADISTICA).
CREATE TABLE stat_cut (
 id BIGINT PRIMARY KEY,
 code VARCHAR(20) NOT NULL UNIQUE,
 title VARCHAR(200) NOT NULL,
 scope_id BIGINT,
 period_type VARCHAR(20) NOT NULL,
 period_from VARCHAR(10) NOT NULL,
 period_to VARCHAR(10) NOT NULL,
 as_of_ms BIGINT NOT NULL,
 methodology_version VARCHAR(10) NOT NULL,
 catalog_versions TEXT NOT NULL,
 calendar_sha256 CHAR(64) NOT NULL,
 dimensions_json TEXT NOT NULL,
 labels_json MEDIUMTEXT NOT NULL,
 counts_json TEXT NOT NULL,
 row_count BIGINT NOT NULL,
 rows_sha256 CHAR(64) NOT NULL,
 metadata_sha256 CHAR(64) NOT NULL,
 created_by BIGINT NOT NULL,
 created_ms BIGINT NOT NULL,
 basis TEXT NOT NULL,
 state VARCHAR(12) NOT NULL,
 annulled_by BIGINT,
 annulled_ms BIGINT,
 annul_basis TEXT,
 revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(scope_id) REFERENCES org_scope(id),
 FOREIGN KEY(created_by) REFERENCES staff(id),
 FOREIGN KEY(annulled_by) REFERENCES staff(id)
);
CREATE TABLE stat_cut_row (
 cut_id BIGINT NOT NULL,
 communication_id BIGINT NOT NULL,
 req_id BIGINT,
 received_ms BIGINT NOT NULL,
 created_ms BIGINT NOT NULL,
 req_ms BIGINT,
 com_state VARCHAR(20) NOT NULL,
 v_via_ingreso BIGINT,
 v_tipo_solicitud BIGINT,
 v_tipo_requirente BIGINT,
 p_tipo_solicitud VARCHAR(40),
 p_tipo_requirente VARCHAR(40),
 d_estado_com VARCHAR(20) NOT NULL,
 d_estado_sol VARCHAR(20) NOT NULL,
 d_estado_req VARCHAR(20) NOT NULL,
 d_canal VARCHAR(40) NOT NULL,
 d_mecanismo VARCHAR(20) NOT NULL,
 d_via_ingreso VARCHAR(40) NOT NULL,
 d_via_agrupacion VARCHAR(40) NOT NULL,
 d_tipo_solicitud VARCHAR(40) NOT NULL,
 d_tipo_requirente VARCHAR(40) NOT NULL,
 d_modalidad VARCHAR(20) NOT NULL,
 d_region_requirente VARCHAR(40) NOT NULL,
 d_comuna_requirente VARCHAR(200) NOT NULL,
 d_region_origen VARCHAR(40) NOT NULL,
 d_region_hechos VARCHAR(40) NOT NULL,
 d_comuna_hechos VARCHAR(200) NOT NULL,
 d_ambito VARCHAR(48) NOT NULL,
 d_sede_inicial VARCHAR(48) NOT NULL,
 d_adscripcion_inicial VARCHAR(48) NOT NULL,
 d_sede_vigente VARCHAR(48) NOT NULL,
 d_territorio_cobertura VARCHAR(48) NOT NULL,
 d_region_sede VARCHAR(40) NOT NULL,
 d_unidad VARCHAR(24) NOT NULL,
 d_nivel VARCHAR(20) NOT NULL,
 d_materia VARCHAR(20) NOT NULL,
 d_complejidad VARCHAR(20) NOT NULL,
 d_origen_req VARCHAR(20) NOT NULL,
 d_resultado_primera_atencion VARCHAR(24) NOT NULL,
 d_edad_requirente BIGINT NOT NULL,
 d_tramo_edad_requirente VARCHAR(40) NOT NULL,
 d_nacionalidad_requirente VARCHAR(200) NOT NULL,
 d_grupo_nacionalidad_requirente VARCHAR(20) NOT NULL,
 d_estado_tipo_solicitud VARCHAR(20) NOT NULL,
 d_estado_tipo_requirente VARCHAR(20) NOT NULL,
 d_estado_via_ingreso VARCHAR(20) NOT NULL,
 d_estado_derecho VARCHAR(20) NOT NULL,
 d_estado_contexto VARCHAR(20) NOT NULL,
 d_estado_caracterizacion VARCHAR(20) NOT NULL,
 d_estado_rol_estado VARCHAR(20) NOT NULL,
 d_estado_grupo_prioritario VARCHAR(20) NOT NULL,
 PRIMARY KEY(cut_id,communication_id)
);
CREATE TABLE stat_cut_event (
 cut_id BIGINT NOT NULL,
 event_id BIGINT NOT NULL,
 communication_id BIGINT NOT NULL,
 variable VARCHAR(32) NOT NULL,
 code VARCHAR(40) NOT NULL,
 catalog_version_id BIGINT,
 PRIMARY KEY(cut_id,event_id)
);
CREATE INDEX ix_stat_cut_event_com ON stat_cut_event(cut_id,communication_id,variable);
CREATE TABLE stat_cut_action (
 cut_id BIGINT NOT NULL,
 unit_id BIGINT NOT NULL,
 communication_id BIGINT NOT NULL,
 action_type VARCHAR(40) NOT NULL,
 origin VARCHAR(20) NOT NULL,
 note_id BIGINT,
 catalog_version_id BIGINT,
 PRIMARY KEY(cut_id,unit_id)
);
CREATE INDEX ix_stat_cut_action_com ON stat_cut_action(cut_id,communication_id);
CREATE TABLE stat_cut_action_institution (
 cut_id BIGINT NOT NULL,
 institution_id BIGINT NOT NULL,
 note_id BIGINT NOT NULL,
 type_code VARCHAR(40),
 catalog_version_id BIGINT,
 PRIMARY KEY(cut_id,institution_id)
);
CREATE INDEX ix_stat_cut_institution_note ON stat_cut_action_institution(cut_id,note_id);
CREATE TABLE stat_cut_person (
 cut_id BIGINT NOT NULL,
 communication_id BIGINT NOT NULL,
 ordinal INT NOT NULL,
 region_code VARCHAR(40),
 commune VARCHAR(200),
 age_years BIGINT,
 age_bracket VARCHAR(40),
 nationality VARCHAR(200),
 PRIMARY KEY(cut_id,communication_id,ordinal)
);
CREATE TABLE stat_cut_case (
 cut_id BIGINT NOT NULL,
 case_id BIGINT NOT NULL,
 communication_id BIGINT NOT NULL,
 created_ms BIGINT NOT NULL,
 PRIMARY KEY(cut_id,case_id)
);
CREATE INDEX ix_stat_cut_case_com ON stat_cut_case(cut_id,communication_id);
CREATE TABLE stat_view (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 owner_id BIGINT NOT NULL,
 title VARCHAR(200) NOT NULL,
 definition_json MEDIUMTEXT NOT NULL,
 shared BOOLEAN NOT NULL DEFAULT FALSE,
 created_ms BIGINT NOT NULL,
 updated_ms BIGINT NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(owner_id) REFERENCES staff(id)
);
CREATE INDEX ix_stat_view_owner ON stat_view(owner_id,id);
