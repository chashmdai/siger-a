-- V19: eventos de clasificacion estadistica (0.8.0, D28-D31 y D35, gate E05; DISENO 3.2). Aditiva sobre V1-V18.
-- Una sola tabla para todas las variables y solo inserciones: la historia es la tabla. Variables de valor unico
-- (TIPO_SOLICITUD, TIPO_REQUIRENTE, VIA_INGRESO): el valor vigente es el ultimo SET o STATUS. Variables multiples
-- (DERECHO, CONTEXTO, CARACTERIZACION; comandos en E06): el conjunto vigente son los ADD no seguidos de un REMOVE del
-- mismo codigo, y un STATUS vale solo con el conjunto vacio. status = INFORMADO en SET/ADD/REMOVE o el valor especial
-- (SIN_INFORMACION, NO_DECLARADO, NO_APLICA) en STATUS; PENDIENTE = sin evento (salvo la via derivada del canal, que
-- StatValues calcula sin escribir). catalog_version_id = version VIGENTE del catalogo al registrar. origin y
-- reopening_id los asigna el servidor: FUNCIONARIO o RETROSPECTIVA (REQ abierto que ya tuvo un cierre, con la
-- reapertura vigente). basis = motivo del funcionario (puede traer datos personales: nunca va a la auditoria).
-- Valores validados en Java (StatValues), sin CHECK que H2 y MySQL traten distinto. Ninguna migracion escribe eventos.
CREATE TABLE stat_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 communication_id BIGINT NOT NULL,
 record_type VARCHAR(8) NOT NULL,
 record_id BIGINT NOT NULL,
 variable VARCHAR(32) NOT NULL,
 event VARCHAR(8) NOT NULL,
 status VARCHAR(20) NOT NULL,
 catalog_version_id BIGINT,
 code VARCHAR(40),
 origin VARCHAR(16) NOT NULL,
 author_id BIGINT NOT NULL,
 recorded_ms BIGINT NOT NULL,
 basis TEXT,
 reopening_id BIGINT,
 FOREIGN KEY(communication_id) REFERENCES communication(id),
 FOREIGN KEY(catalog_version_id) REFERENCES stat_catalog_version(id),
 FOREIGN KEY(author_id) REFERENCES staff(id),
 FOREIGN KEY(reopening_id) REFERENCES reopening(id)
);
CREATE INDEX ix_stat_event_com ON stat_event(communication_id,variable,id);
CREATE INDEX ix_stat_event_record ON stat_event(record_type,record_id,variable,id);
