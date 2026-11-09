-- V10: evidencia de analisis antivirus de originales. Aditiva sobre V1-V9 (no modifica tablas ni datos existentes).
-- Un original real solo sale de cuarentena con un analisis LIMPIO reciente de sus mismos bytes (SHA-256), hecho por el
-- servidor con un motor configurado; un analisis INFECTADO impide su liberacion de forma permanente.
CREATE TABLE document_scan (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 document_id BIGINT NOT NULL,
 sha256 CHAR(64) NOT NULL,
 engine VARCHAR(16) NOT NULL,
 engine_version VARCHAR(120),
 verdict VARCHAR(12) NOT NULL,
 exit_code INT,
 detail VARCHAR(64),
 requested_by BIGINT NOT NULL,
 started_ms BIGINT NOT NULL,
 finished_ms BIGINT NOT NULL,
 FOREIGN KEY(document_id) REFERENCES document_item(id),
 FOREIGN KEY(requested_by) REFERENCES staff(id),
 CHECK(verdict IN ('LIMPIO','INFECTADO','ERROR'))
);
CREATE INDEX ix_document_scan_document ON document_scan(document_id,id);
