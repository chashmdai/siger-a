-- V12: indice de personas y folios (0.5.0, D5, gate G07). Aditiva sobre V1-V11.
-- Dato derivado y reconstruible (PersonIndex): coincidencias sobre los datos declarados de cada COM, nunca un
-- registro maestro. No fusiona identidades ni autentica personas. source: COM, COM_PERSONA, FICHA o FORMULARIO.
-- name_key: minusculas, sin tildes ni puntuacion, espacios simples. run_number sin puntos ni ceros iniciales.
-- document_key: documento no RUN en mayusculas, solo letras y digitos. Valores validados en Java, sin CHECK.
CREATE TABLE person_reference (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 communication_id BIGINT NOT NULL,
 requirement_id BIGINT,
 source VARCHAR(16) NOT NULL,
 person_role VARCHAR(24) NOT NULL,
 display_name VARCHAR(500),
 name_key VARCHAR(500),
 run_number VARCHAR(9),
 run_dv CHAR(1),
 document_key VARCHAR(100),
 authenticated BOOLEAN NOT NULL DEFAULT FALSE,
 updated_ms BIGINT NOT NULL,
 FOREIGN KEY(communication_id) REFERENCES communication(id),
 FOREIGN KEY(requirement_id) REFERENCES requirement(id)
);
CREATE INDEX ix_person_reference_run ON person_reference(run_number);
CREATE INDEX ix_person_reference_document ON person_reference(document_key);
CREATE INDEX ix_person_reference_com ON person_reference(communication_id);
