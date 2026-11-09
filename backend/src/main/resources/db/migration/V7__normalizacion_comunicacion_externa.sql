-- Proyeccion normalizada de una Comunicacion externa (Formulario completo 1.0/1.1). Se calcula una sola vez,
-- al conservar el original autentico; el documento firmado en communication_original no se modifica nunca.
-- Personas del documento (hasta 30, cualquier rol). Separada de communication_person (registro manual, 12).
CREATE TABLE external_person (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 communication_id BIGINT NOT NULL,
 person_key VARCHAR(64) NOT NULL,
 person_index INT NOT NULL,
 person_role VARCHAR(24) NOT NULL,
 authenticated BOOLEAN NOT NULL,
 given_names MEDIUMTEXT,
 given_names_source VARCHAR(32),
 surnames MEDIUMTEXT,
 surnames_source VARCHAR(32),
 run_number VARCHAR(9),
 run_dv CHAR(1),
 run_source VARCHAR(32),
 run_valid BOOLEAN,
 identification_type VARCHAR(40),
 foreign_document_type VARCHAR(255),
 foreign_document_number VARCHAR(255),
 birth_date CHAR(10),
 birth_date_source VARCHAR(32),
 age_at_intake INT,
 declared_age INT,
 registry_state VARCHAR(24),
 registry_checked_ms BIGINT,
 FOREIGN KEY(communication_id) REFERENCES communication(id),
 CONSTRAINT uq_external_person UNIQUE(communication_id,person_key)
);
-- Cada atributo o discrepancia con su procedencia exacta; meaning es la lectura del diccionario SRCeI.
CREATE TABLE external_attribute (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 person_id BIGINT NOT NULL,
 attr_key VARCHAR(255) NOT NULL,
 discrepancy BOOLEAN NOT NULL,
 value_text MEDIUMTEXT,
 code_text VARCHAR(255),
 meaning VARCHAR(120),
 source VARCHAR(32) NOT NULL,
 level_code VARCHAR(64) NOT NULL,
 observed_ms BIGINT NOT NULL,
 evidence VARCHAR(36) NOT NULL,
 FOREIGN KEY(person_id) REFERENCES external_person(id)
);
CREATE INDEX ix_external_attribute_person ON external_attribute(person_id,id);
-- Respuestas (CAMPO), detalles por persona (PERSONA) e instituciones (INSTITUCION), integros y en orden.
CREATE TABLE external_field (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 communication_id BIGINT NOT NULL,
 field_group VARCHAR(12) NOT NULL,
 person_key VARCHAR(64),
 item INT NOT NULL,
 field_key VARCHAR(255) NOT NULL,
 value_text MEDIUMTEXT,
 FOREIGN KEY(communication_id) REFERENCES communication(id),
 CHECK(field_group IN ('CAMPO','PERSONA','INSTITUCION'))
);
CREATE INDEX ix_external_field_com ON external_field(communication_id,id);
