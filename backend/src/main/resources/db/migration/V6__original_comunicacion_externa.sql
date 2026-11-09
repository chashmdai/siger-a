-- Documento original de una Comunicacion externa (Formulario completo 1.0/1.1 recibido por SMR).
-- Se conserva tal como llego; los campos normalizados viven en communication y nunca lo sobrescriben.
-- Unicidad por origen e identificador de origen (idempotencia) y una sola COM institucional por original.
CREATE TABLE communication_original (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 communication_id BIGINT NOT NULL UNIQUE,
 source VARCHAR(48) NOT NULL,
 external_id VARCHAR(100) NOT NULL,
 document_version VARCHAR(8) NOT NULL,
 document_kind VARCHAR(24) NOT NULL,
 received_ms BIGINT NOT NULL,
 content_hash CHAR(64) NOT NULL,
 provenance_hash CHAR(64) NOT NULL,
 custody_key_id VARCHAR(64) NOT NULL,
 document MEDIUMTEXT NOT NULL,
 document_sha256 CHAR(64) NOT NULL,
 document_bytes BIGINT NOT NULL,
 stored_ms BIGINT NOT NULL,
 stored_by BIGINT NOT NULL,
 FOREIGN KEY(communication_id) REFERENCES communication(id),
 FOREIGN KEY(stored_by) REFERENCES staff(id),
 CONSTRAINT uq_original_source UNIQUE(source,external_id)
);
