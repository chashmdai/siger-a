-- V20: derechos, contexto, caracterizacion y actuaciones tipificadas (0.8.0, D34-D36, gate E06; DISENO 3.3, 4.3 y 4.4).
-- Aditiva sobre V1-V19, sin siembra ni reescritura de datos existentes salvo la carga historica del resultado de la
-- primera atencion (marcador RESULTADO_INMEDIATO_V20, desde Bootstrap).
-- * stat_catalog_item.description: definicion operativa opcional de cada item (campo "descripcion" del archivo
--   siger-catalogo-1); ayuda en pantalla y diccionario de la exportacion.
-- * Variables multiples (DERECHO, CONTEXTO, CARACTERIZACION, ROL_ESTADO, GRUPO_PRIORITARIO) en stat_event (V19), por
--   REQ o Caso (record_type REQ o CAS): sin columnas nuevas.
-- * management_note.action_type: codigo de TIPO_ACTUACION (nulo = constancia sin tipificar, incluida toda la historia y
--   la constancia del resultado de la primera atencion, que se cuenta solo desde requirement.immediate_result) y
--   action_catalog_version = version del catalogo con que se registro.
-- * note_institution: instituciones de una actuacion (mismas reglas que institutions_list de la ficha 1.2).
-- * task_item.action_type: compromiso (una tarea completada no es una actuacion).
-- * requirement.immediate_result (ORIENTACION, RESPUESTA_DIRECTA, REMISION_CONCLUIDA) e
--   immediate_result_reconstructed (carga historica desde la constancia con prefijo exacto que coincide con el cierre).
-- Valores validados en Java, sin CHECK que H2 y MySQL traten distinto.
ALTER TABLE stat_catalog_item ADD COLUMN description VARCHAR(1000);
ALTER TABLE management_note ADD COLUMN action_type VARCHAR(40);
ALTER TABLE management_note ADD COLUMN action_catalog_version BIGINT;
ALTER TABLE management_note ADD CONSTRAINT fk_management_note_action_version FOREIGN KEY(action_catalog_version) REFERENCES stat_catalog_version(id);
CREATE TABLE note_institution (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 note_id BIGINT NOT NULL,
 sort INT NOT NULL,
 name VARCHAR(200) NOT NULL,
 type_code VARCHAR(40),
 catalog_version_id BIGINT,
 role VARCHAR(300),
 region_code VARCHAR(4),
 commune VARCHAR(100),
 FOREIGN KEY(note_id) REFERENCES management_note(id),
 FOREIGN KEY(catalog_version_id) REFERENCES stat_catalog_version(id)
);
CREATE INDEX ix_note_institution_note ON note_institution(note_id,sort);
ALTER TABLE task_item ADD COLUMN action_type VARCHAR(40);
ALTER TABLE task_item ADD COLUMN action_catalog_version BIGINT;
ALTER TABLE task_item ADD CONSTRAINT fk_task_item_action_version FOREIGN KEY(action_catalog_version) REFERENCES stat_catalog_version(id);
ALTER TABLE requirement ADD COLUMN immediate_result VARCHAR(24);
ALTER TABLE requirement ADD COLUMN immediate_result_reconstructed BOOLEAN NOT NULL DEFAULT FALSE;
