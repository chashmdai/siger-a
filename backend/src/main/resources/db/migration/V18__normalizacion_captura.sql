-- V18: normalizacion integral de la captura (0.8.0, D33, gate E03). Aditiva sobre V1-V17.
-- attention_revision.change_summary: resumen de cambios de cada version de la ficha respecto de la anterior (JSON con
-- las rutas COMPLEMENTO y RECTIFICACION, sin valores; nulo en la primera version). Dato derivado que calcula el
-- servidor (DeclaredPerson.changes) al crear la version; las versiones anteriores las calcula la carga inicial
-- (marcador RESUMEN_CAMBIOS_V18). No cambia data_json.
-- intake_record e intake_revision: ficha de ingreso de la COM manual (DISENO 3.5) con el esquema canonico persona-1
-- (presentador opcional, personas relacionadas, restricciones de contacto, hechos e instituciones). Es la unica
-- fuente de la COM que la tiene: sus columnas de resumen en communication son derivadas y communication_person no
-- recibe filas para ella. Cada version guarda autor, instante, motivo, datos, region del presentador (codigo valido
-- del catalogo del Formulario o nulo) y resumen de cambios. Valores validados en Java, sin CHECK.
ALTER TABLE attention_revision ADD COLUMN change_summary MEDIUMTEXT;
CREATE TABLE intake_record (
 communication_id BIGINT PRIMARY KEY,
 data_json MEDIUMTEXT NOT NULL,
 updated_by BIGINT NOT NULL,
 updated_ms BIGINT NOT NULL,
 FOREIGN KEY(communication_id) REFERENCES communication(id),
 FOREIGN KEY(updated_by) REFERENCES staff(id)
);
CREATE TABLE intake_revision (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 communication_id BIGINT NOT NULL,
 revision BIGINT NOT NULL,
 author_id BIGINT NOT NULL,
 registered_ms BIGINT NOT NULL,
 reason VARCHAR(2000) NOT NULL,
 data_json MEDIUMTEXT NOT NULL,
 region_code VARCHAR(4),
 change_summary MEDIUMTEXT,
 FOREIGN KEY(communication_id) REFERENCES intake_record(communication_id),
 FOREIGN KEY(author_id) REFERENCES staff(id),
 UNIQUE(communication_id,revision)
);
CREATE INDEX ix_intake_revisions ON intake_revision(communication_id,id);
